package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.util.UUID

interface AiAssistantRepository {
    val state: StateFlow<AssistantUiState>

    suspend fun refresh(
        document: DocumentModel?,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun updatePrompt(prompt: String)

    suspend fun updateSettings(settings: AssistantSettings)

    suspend fun askPdf(
        document: DocumentModel,
        question: String,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun summarizeDocument(
        document: DocumentModel,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun summarizePage(
        document: DocumentModel,
        currentPageIndex: Int,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun extractActionItems(
        document: DocumentModel,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun explainSelection(
        document: DocumentModel,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )

    suspend fun semanticSearch(
        document: DocumentModel,
        query: String,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    )
}

class DefaultAiAssistantRepository(
    context: Context,
    private val documentSearchService: DocumentSearchService,
    private val backendProvider: AiBackendProvider = FakeAiBackendProvider(),
) : AiAssistantRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutableState = MutableStateFlow(loadPersistedState())

    override val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    override suspend fun refresh(
        document: DocumentModel?,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        val suggestions = if (availability.enabled && document != null && mutableState.value.settings.allowSuggestions) {
            buildSuggestions(document, selection, enterpriseState)
        } else {
            emptyList()
        }
        mutableState.update {
            it.copy(
                availability = availability,
                suggestions = suggestions,
            )
        }
    }

    override suspend fun updatePrompt(prompt: String) {
        mutableState.update { it.copy(prompt = prompt) }
    }

    override suspend fun updateSettings(settings: AssistantSettings) {
        mutableState.update { it.copy(settings = settings) }
        persistState(mutableState.value)
    }

    override suspend fun askPdf(
        document: DocumentModel,
        question: String,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.AskPdf, question, selection, document.pages.indices.firstOrNull() ?: 0, entitlements, enterpriseState)
    }

    override suspend fun summarizeDocument(
        document: DocumentModel,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.SummarizeDocument, "Summarize the document", null, 0, entitlements, enterpriseState)
    }

    override suspend fun summarizePage(
        document: DocumentModel,
        currentPageIndex: Int,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.SummarizePage, "Summarize the current page", null, currentPageIndex, entitlements, enterpriseState)
    }

    override suspend fun extractActionItems(
        document: DocumentModel,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.ExtractActionItems, "Extract action items", null, 0, entitlements, enterpriseState)
    }

    override suspend fun explainSelection(
        document: DocumentModel,
        selection: TextSelectionPayload?,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.ExplainSelection, "Explain the selected text", selection, selection?.pageIndex ?: 0, entitlements, enterpriseState)
    }

    override suspend fun semanticSearch(
        document: DocumentModel,
        query: String,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        runTask(document, AssistantTaskType.SemanticSearch, query, null, 0, entitlements, enterpriseState)
    }

    private suspend fun runTask(
        document: DocumentModel,
        task: AssistantTaskType,
        prompt: String,
        selection: TextSelectionPayload?,
        currentPageIndex: Int,
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        mutableState.update {
            it.copy(
                isWorking = true,
                prompt = prompt,
                availability = availability,
            )
        }
        val indexed = documentSearchService.ensureIndex(document)
        val request = AssistantPromptRequest(
            task = task,
            prompt = prompt,
            documentTitle = document.documentRef.displayName,
            currentPageIndex = currentPageIndex,
            selectionText = selection?.text.orEmpty(),
            pageContext = indexed
                .filter { page -> task != AssistantTaskType.SummarizePage || page.pageIndex == currentPageIndex }
                .map { page ->
                    GroundedPageContext(
                        pageIndex = page.pageIndex,
                        snippets = page.blocks.take(6).map { block -> GroundedSnippet(block.text.trim(), block.bounds) },
                    )
                }
                .take(if (task == AssistantTaskType.SummarizeDocument) 8 else 5),
            privacyMode = mutableState.value.settings.privacyMode,
        )
        val result = backendProvider.generate(request)
        val cards = if (task == AssistantTaskType.SemanticSearch) {
            rankSemanticCards(indexed.flatMap { page -> page.blocks }, prompt)
        } else {
            result.semanticCards
        }
        val suggestions = if (mutableState.value.settings.allowSuggestions) {
            mergeSuggestions(result.suggestions, buildSuggestions(document, selection, enterpriseState))
        } else {
            emptyList()
        }
        val userMessage = AssistantMessage(
            id = UUID.randomUUID().toString(),
            role = AssistantMessageRole.User,
            task = task,
            text = prompt,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        val assistantMessage = AssistantMessage(
            id = UUID.randomUUID().toString(),
            role = AssistantMessageRole.Assistant,
            task = task,
            text = AssistantResultFormatter.format(result),
            citations = result.citations,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        mutableState.update {
            it.copy(
                isWorking = false,
                latestResult = result.copy(semanticCards = cards, suggestions = suggestions),
                conversation = (it.conversation + listOf(userMessage, assistantMessage)).takeLast(16),
                semanticCards = cards,
                suggestions = suggestions,
                availability = availability,
            )
        }
        persistState(mutableState.value)
    }

    private suspend fun buildSuggestions(
        document: DocumentModel,
        selection: TextSelectionPayload?,
        enterpriseState: EnterpriseAdminStateModel,
    ): List<AssistiveSuggestion> {
        val indexed = documentSearchService.ensureIndex(document)
        val sensitive = indexed.flatMap { page ->
            page.blocks.filter { block -> sensitiveTerms.any { term -> block.text.contains(term, ignoreCase = true) } }
                .map { block ->
                    AssistiveSuggestion(
                        id = UUID.randomUUID().toString(),
                        type = AssistiveSuggestionType.Redaction,
                        title = "Review for redaction",
                        detail = block.text.take(140),
                        anchor = CitationAnchor(page.pageIndex, block.bounds, block.text.take(120)),
                    )
                }
        }.take(3)
        val autofill = document.formDocument.fields.mapNotNull { field ->
            val shouldSuggest = when (val value = field.value) {
                is FormFieldValue.Text -> value.text.isBlank()
                is FormFieldValue.Choice -> value.selected.isBlank()
                else -> false
            }
            if (!shouldSuggest) {
                null
            } else {
                val candidate = when {
                    field.name.contains("email", ignoreCase = true) -> enterpriseState.authSession.email
                    field.name.contains("name", ignoreCase = true) -> enterpriseState.authSession.displayName
                    else -> selection?.text?.take(60).orEmpty()
                }
                candidate.takeIf { it.isNotBlank() }?.let {
                    AssistiveSuggestion(
                        id = UUID.randomUUID().toString(),
                        type = AssistiveSuggestionType.FormAutofill,
                        title = "Suggested value for ${field.label}",
                        detail = it,
                        anchor = CitationAnchor(field.pageIndex, field.bounds, it),
                        suggestedValue = it,
                        fieldName = field.name,
                    )
                }
            }
        }.take(3)
        return sensitive + autofill
    }

    private fun rankSemanticCards(
        blocks: List<ExtractedTextBlock>,
        query: String,
    ): List<SemanticSearchCard> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }.toSet()
        if (terms.isEmpty()) return emptyList()
        return blocks
            .map { block ->
                val lowered = block.text.lowercase()
                val matches = terms.count { lowered.contains(it) }
                val score = matches.toFloat() / terms.size.toFloat()
                block to score
            }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(6)
            .mapIndexed { index, (block, score) ->
                SemanticSearchCard(
                    id = "semantic-$index",
                    title = "Page ${block.pageIndex + 1}",
                    snippet = block.text.take(180),
                    anchor = CitationAnchor(block.pageIndex, block.bounds, block.text.take(120)),
                    score = score,
                )
            }
    }

    private fun resolveAvailability(
        entitlements: EntitlementStateModel,
        enterpriseState: EnterpriseAdminStateModel,
    ): AssistantAvailability {
        val missingFeatures = buildSet {
            if (!entitlements.features.contains(FeatureFlag.Ai)) add(FeatureFlag.Ai)
        }
        if (missingFeatures.isNotEmpty()) {
            return AssistantAvailability(false, "AI features are not included in the current plan.", missingFeatures)
        }
        if (!enterpriseState.adminPolicy.aiEnabled) {
            return AssistantAvailability(false, "Tenant policy has disabled AI assistance.")
        }
        if (mutableState.value.settings.privacyMode == AssistantPrivacyMode.CloudAssisted && !enterpriseState.adminPolicy.aiEnabled) {
            return AssistantAvailability(false, "Cloud-assisted mode is blocked by tenant policy.")
        }
        return AssistantAvailability(true)
    }

    private fun mergeSuggestions(
        primary: List<AssistiveSuggestion>,
        secondary: List<AssistiveSuggestion>,
    ): List<AssistiveSuggestion> {
        return (primary + secondary)
            .distinctBy { suggestion -> listOf(suggestion.type.name, suggestion.anchor.pageIndex.toString(), suggestion.anchor.bounds.toString(), suggestion.fieldName).joinToString("|") }
            .take(6)
    }

    private fun loadPersistedState(): AssistantUiState {
        val raw = prefs.getString(KEY_STATE, null) ?: return AssistantUiState()
        return runCatching { json.decodeFromString(AssistantUiState.serializer(), raw) }.getOrDefault(AssistantUiState())
    }

    private fun persistState(state: AssistantUiState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(AssistantUiState.serializer(), state)).apply()
    }

    private companion object {
        private const val PREFS_NAME = "ai-assistant"
        private const val KEY_STATE = "state"
        private val sensitiveTerms = listOf("ssn", "social security", "passport", "confidential", "bank", "account")
    }
}
