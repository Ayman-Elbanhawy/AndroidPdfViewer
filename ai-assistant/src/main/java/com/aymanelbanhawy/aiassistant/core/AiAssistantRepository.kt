
package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

interface AiAssistantRepository {
    val state: StateFlow<AssistantUiState>
    suspend fun refresh(document: DocumentModel?, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun updatePrompt(prompt: String)
    suspend fun updateSettings(settings: AssistantSettings)
    suspend fun updateProviderDraft(draft: AiProviderDraft)
    suspend fun saveProviderDraft(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun refreshProviderCatalog(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun testProviderConnection(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun cancelActiveRequest()
    suspend fun askPdf(document: DocumentModel, question: String, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun summarizeDocument(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun summarizePage(document: DocumentModel, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun extractActionItems(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun explainSelection(document: DocumentModel, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun semanticSearch(document: DocumentModel, query: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
}

class DefaultAiAssistantRepository(
    private val documentSearchService: DocumentSearchService,
    private val settingsStore: AiProviderSettingsStore,
    private val providerRuntime: AiProviderRuntime,
) : AiAssistantRepository {
    private val mutableState = MutableStateFlow(AssistantUiState())
    private var activeInvocation: ProviderInvocation? = null
    override val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    suspend fun initialize() {
        val persisted = settingsStore.load()
        val profiles = persisted.profiles.ifEmpty { defaultProviderProfiles() }
        val selected = profiles.firstOrNull { it.id == persisted.selectedProviderId }?.id ?: profiles.first().id
        mutableState.value = AssistantUiState(
            settings = persisted.settings,
            availability = AssistantAvailability(false, "AI is disabled"),
            providerRuntime = AiProviderRuntimeState(selectedProviderId = selected, profiles = profiles, draft = profiles.first { it.id == selected }.toDraft()),
        )
    }

    override suspend fun refresh(document: DocumentModel?, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        mutableState.update {
            it.copy(
                availability = availability,
                providerRuntime = it.providerRuntime.copy(discoveredLocalApps = providerRuntime.discoverLocalApps()),
                suggestions = if (availability.enabled && document != null && it.settings.allowSuggestions) buildSuggestions(document, selection, enterpriseState) else emptyList(),
            )
        }
    }

    override suspend fun updatePrompt(prompt: String) {
        mutableState.update { it.copy(prompt = prompt) }
    }

    override suspend fun updateSettings(settings: AssistantSettings) {
        mutableState.update { it.copy(settings = settings) }
        persistState()
    }

    override suspend fun updateProviderDraft(draft: AiProviderDraft) {
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(draft = draft, selectedProviderId = draft.profileId, lastError = null)) }
    }

    override suspend fun saveProviderDraft(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val draft = mutableState.value.providerRuntime.draft
        val profiles = mutableState.value.providerRuntime.profiles.toMutableList()
        providerRuntime.saveCredential(draft.profileId, draft.apiKeyInput.ifBlank { null })
        val updated = draft.toProfile(draft.apiKeyInput.isNotBlank() || profiles.firstOrNull { it.id == draft.profileId }?.hasStoredCredential == true)
        val index = profiles.indexOfFirst { it.id == draft.profileId }
        if (index >= 0) profiles[index] = updated else profiles += updated
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(profiles = profiles, selectedProviderId = updated.id, draft = updated.toDraft(), diagnosticsMessage = "Saved provider settings for ${updated.displayName}.")) }
        persistState()
        refreshProviderCatalog(entitlements, enterpriseState)
        providerRuntime.auditProviderSwitch(updated)
        if (updated.modelId.isNotBlank()) providerRuntime.auditModelSwitch(updated)
    }

    override suspend fun refreshProviderCatalog(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        val current = mutableState.value.providerRuntime.currentProfile() ?: return
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Discovering, lastError = null)) }
        runCatching {
            val selection = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
            val selected = selection.profile ?: error(selection.blockedReason ?: "No provider available")
            val catalog = providerRuntime.loadCatalog(selected)
            mutableState.update {
                it.copy(availability = availability, providerRuntime = it.providerRuntime.copy(selectedProviderId = selected.id, draft = selected.toDraft(), availableModels = catalog.models, activeCapabilities = catalog.capabilities, status = AiProviderStatus.Completed, diagnosticsMessage = catalog.diagnostics, lastError = null))
            }
        }.getOrElse { throwable ->
            val error = providerRuntime.mapThrowable(throwable, current.id)
            mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
        }
    }

    override suspend fun testProviderConnection(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        val current = mutableState.value.providerRuntime.currentProfile() ?: return
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Testing, lastError = null)) }
        runCatching {
            val selection = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
            val selected = selection.profile ?: error(selection.blockedReason ?: "No provider available")
            val health = providerRuntime.testConnection(selected, mutableState.value.settings, enterpriseState)
            val catalog = providerRuntime.loadCatalog(selected)
            mutableState.update {
                it.copy(providerRuntime = it.providerRuntime.copy(selectedProviderId = selected.id, draft = selected.toDraft(), availableModels = catalog.models, activeCapabilities = catalog.capabilities, status = AiProviderStatus.Completed, diagnosticsMessage = health.message, lastError = null))
            }
        }.getOrElse { throwable ->
            val error = providerRuntime.mapThrowable(throwable, current.id)
            mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
        }
    }

    override suspend fun cancelActiveRequest() {
        activeInvocation?.cancel?.invoke()
        activeInvocation = null
        val profile = mutableState.value.providerRuntime.currentProfile()
        if (profile != null) providerRuntime.auditPromptCancelled(profile, mutableState.value.latestResult?.task ?: AssistantTaskType.AskPdf)
        mutableState.update { it.copy(isWorking = false, providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Cancelled)) }
    }

    override suspend fun askPdf(document: DocumentModel, question: String, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.AskPdf, question, selection, document.pages.indices.firstOrNull() ?: 0, entitlements, enterpriseState)

    override suspend fun summarizeDocument(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.SummarizeDocument, "Summarize the document", null, 0, entitlements, enterpriseState)

    override suspend fun summarizePage(document: DocumentModel, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.SummarizePage, "Summarize page ${currentPageIndex + 1}", null, currentPageIndex, entitlements, enterpriseState)

    override suspend fun extractActionItems(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.ExtractActionItems, "Extract action items from this PDF", null, 0, entitlements, enterpriseState)

    override suspend fun explainSelection(document: DocumentModel, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.ExplainSelection, "Explain the selected text", selection, selection?.pageIndex ?: 0, entitlements, enterpriseState)

    override suspend fun semanticSearch(document: DocumentModel, query: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) =
        runTask(document, AssistantTaskType.SemanticSearch, query, null, 0, entitlements, enterpriseState)

    private suspend fun runTask(document: DocumentModel, task: AssistantTaskType, prompt: String, selection: TextSelectionPayload?, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val availability = resolveAvailability(entitlements, enterpriseState)
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        val indexed = documentSearchService.ensureIndex(document)
        val request = AssistantPromptRequest(
            task = task,
            prompt = prompt,
            documentTitle = document.documentRef.displayName,
            currentPageIndex = currentPageIndex,
            selectionText = selection?.text.orEmpty(),
            pageContext = indexed.filter { task != AssistantTaskType.SummarizePage || it.pageIndex == currentPageIndex }.map { page -> GroundedPageContext(page.pageIndex, page.blocks.take(6).map { block -> GroundedSnippet(block.text.trim(), block.bounds) }) }.take(if (task == AssistantTaskType.SummarizeDocument) 8 else 5),
            privacyMode = mutableState.value.settings.privacyMode,
        )
        val selectionDecision = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
        val selectedProvider = selectionDecision.profile
        if (selectedProvider == null) {
            val reason = selectionDecision.blockedReason ?: "No AI provider is available."
            mutableState.update { it.copy(availability = AssistantAvailability(false, reason), providerRuntime = it.providerRuntime.copy(lastError = AiProviderError(AiProviderErrorCode.PolicyBlocked, reason, false))) }
            return
        }
        mutableState.update { it.copy(prompt = prompt, isWorking = true, providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Streaming, streamPreview = "", lastError = null), availability = availability) }
        providerRuntime.auditPromptSubmitted(selectedProvider, task)
        val invocation = providerRuntime.stream(selectedProvider, buildProviderPrompt(request))
        activeInvocation = invocation
        val buffer = StringBuilder()
        runCatching {
            invocation.events.collect { event ->
                when (event) {
                    is ProviderStreamEvent.Delta -> {
                        buffer.append(event.text)
                        mutableState.update { state -> state.copy(providerRuntime = state.providerRuntime.copy(streamPreview = buffer.toString(), status = AiProviderStatus.Streaming)) }
                    }
                    is ProviderStreamEvent.Completed -> buffer.clear().append(event.fullText)
                }
            }
        }.onSuccess {
            val semanticCards = if (task == AssistantTaskType.SemanticSearch) rankSemanticCards(indexed.flatMap { it.blocks }, prompt) else emptyList()
            val citations = buildCitations(request)
            val suggestions = if (mutableState.value.settings.allowSuggestions) buildSuggestions(document, selection, enterpriseState) else emptyList()
            val result = AssistantResult(task, headlineFor(task, currentPageIndex), buffer.toString().ifBlank { "The provider returned an empty response." }, citations, semanticCards, extractActionItems(buffer.toString()), suggestions, System.currentTimeMillis())
            mutableState.update {
                it.copy(
                    isWorking = false,
                    latestResult = result,
                    conversation = (it.conversation + listOf(
                        AssistantMessage(UUID.randomUUID().toString(), AssistantMessageRole.User, task, prompt, createdAtEpochMillis = System.currentTimeMillis()),
                        AssistantMessage(UUID.randomUUID().toString(), AssistantMessageRole.Assistant, task, AssistantResultFormatter.format(result), citations = citations, createdAtEpochMillis = System.currentTimeMillis()),
                    )).takeLast(20),
                    semanticCards = semanticCards,
                    suggestions = suggestions,
                    providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Completed, streamPreview = buffer.toString(), diagnosticsMessage = "Completed with ${selectedProvider.displayName}"),
                )
            }
            providerRuntime.auditPromptSuccess(selectedProvider, task)
            persistState()
        }.onFailure { throwable ->
            val error = providerRuntime.mapThrowable(throwable, selectedProvider.id)
            mutableState.update { it.copy(isWorking = false, providerRuntime = it.providerRuntime.copy(status = if (error.code == AiProviderErrorCode.Cancelled) AiProviderStatus.Cancelled else AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
            providerRuntime.auditPromptFailure(selectedProvider, task, error)
        }
        activeInvocation = null
    }

    private suspend fun buildSuggestions(document: DocumentModel, selection: TextSelectionPayload?, enterpriseState: EnterpriseAdminStateModel): List<AssistiveSuggestion> {
        val indexed = documentSearchService.ensureIndex(document)
        val sensitive = indexed.flatMap { page -> page.blocks.filter { block -> sensitiveTerms.any { term -> block.text.contains(term, ignoreCase = true) } }.map { block -> AssistiveSuggestion(UUID.randomUUID().toString(), AssistiveSuggestionType.Redaction, "Review for redaction", block.text.take(140), CitationAnchor(page.pageIndex, block.bounds, block.text.take(120))) } }.take(3)
        val autofill = document.formDocument.fields.mapNotNull { field ->
            val shouldSuggest = when (val value = field.value) {
                is FormFieldValue.Text -> value.text.isBlank()
                is FormFieldValue.Choice -> value.selected.isBlank()
                else -> false
            }
            if (!shouldSuggest) return@mapNotNull null
            val candidate = when {
                field.name.contains("email", true) -> enterpriseState.authSession.email
                field.name.contains("name", true) -> enterpriseState.authSession.displayName
                else -> selection?.text?.take(60).orEmpty()
            }
            candidate.takeIf { it.isNotBlank() }?.let { AssistiveSuggestion(UUID.randomUUID().toString(), AssistiveSuggestionType.FormAutofill, "Suggested value for ${field.label}", it, CitationAnchor(field.pageIndex, field.bounds, it), suggestedValue = it, fieldName = field.name) }
        }.take(3)
        return (sensitive + autofill).distinctBy { it.title + it.anchor.pageIndex + it.anchor.regionLabel }
    }

    private fun rankSemanticCards(blocks: List<ExtractedTextBlock>, query: String): List<SemanticSearchCard> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }.toSet()
        if (terms.isEmpty()) return emptyList()
        return blocks.map { block -> val lowered = block.text.lowercase(); block to terms.count { lowered.contains(it) }.toFloat() / terms.size.toFloat() }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(6)
            .mapIndexed { index, (block, score) -> SemanticSearchCard("semantic-$index", "Page ${block.pageIndex + 1}", block.text.take(180), CitationAnchor(block.pageIndex, block.bounds, block.text.take(120)), score) }
    }

    private fun buildCitations(request: AssistantPromptRequest): List<AssistantCitation> =
        request.pageContext.flatMap { page -> page.snippets.map { snippet -> page.pageIndex to snippet } }
            .take(4)
            .mapIndexed { index, (pageIndex, snippet) -> AssistantCitation("citation-${index + 1}", "Evidence ${index + 1}", CitationAnchor(pageIndex, snippet.bounds, snippet.text.take(180)), (0.74f - index * 0.06f).coerceAtLeast(0.42f)) }

    private fun buildProviderPrompt(request: AssistantPromptRequest): String {
        val contextLines = request.pageContext.flatMap { page -> page.snippets.map { snippet -> "${CitationAnchor(page.pageIndex, snippet.bounds, snippet.text).pageLabel} | ${CitationAnchor(page.pageIndex, snippet.bounds, snippet.text).regionLabel} | ${snippet.text}" } }
        return buildString {
            appendLine("You are assisting with a PDF. Answer only from the grounded context below.")
            appendLine("If the answer is not supported by the context, say that directly.")
            appendLine("Task: ${request.task.name}")
            appendLine("Prompt: ${request.prompt}")
            if (request.selectionText.isNotBlank()) appendLine("Selected text: ${request.selectionText}")
            appendLine("Grounded context:")
            contextLines.forEach { appendLine(it) }
        }
    }

    private fun extractActionItems(text: String): List<String> = text.lines().map { it.trim().removePrefix("- ").removePrefix("* ") }.filter { it.length > 8 }.take(6)

    private fun resolveAvailability(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel): AssistantAvailability {
        val missing = buildSet { if (!entitlements.features.contains(FeatureFlag.Ai)) add(FeatureFlag.Ai) }
        if (missing.isNotEmpty()) return AssistantAvailability(false, "AI features are not included in the current plan.", missing)
        if (!enterpriseState.adminPolicy.aiEnabled) return AssistantAvailability(false, "Tenant policy has disabled AI assistance.")
        return AssistantAvailability(true)
    }

    private suspend fun persistState() {
        val state = mutableState.value
        settingsStore.save(AssistantPersistenceModel(state.settings, state.providerRuntime.selectedProviderId, state.providerRuntime.profiles))
    }

    private fun headlineFor(task: AssistantTaskType, currentPageIndex: Int): String = when (task) {
        AssistantTaskType.AskPdf -> "Ask PDF"
        AssistantTaskType.SummarizeDocument -> "Document Summary"
        AssistantTaskType.SummarizePage -> "Page ${currentPageIndex + 1} Summary"
        AssistantTaskType.ExtractActionItems -> "Action Items"
        AssistantTaskType.ExplainSelection -> "Explain Selection"
        AssistantTaskType.SemanticSearch -> "Semantic Matches"
    }

    private fun AiProviderRuntimeState.currentProfile(): AiProviderProfile? = profiles.firstOrNull { it.id == selectedProviderId } ?: profiles.firstOrNull()

    companion object {
        suspend fun create(context: Context, documentSearchService: DocumentSearchService, enterpriseAdminRepository: EnterpriseAdminRepository, securityRepository: SecurityRepository): DefaultAiAssistantRepository {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val database = newAiAssistantDatabase(context)
            val store = RoomAiProviderSettingsStore(database.providerSettingsDao(), json)
            val runtime = AiProviderRuntime(
                registry = ProviderRuntimeFactory(OkHttpClient.Builder().retryOnConnectionFailure(true).build(), json).createRegistry(),
                selectionEngine = ProviderSelectionEngine(),
                secureCredentialStore = AndroidKeystoreCredentialStore(context),
                discovery = AndroidLocalAiAppDiscovery(context),
                enterpriseAdminRepository = enterpriseAdminRepository,
                securityRepository = securityRepository,
            )
            return DefaultAiAssistantRepository(documentSearchService, store, runtime).also { it.initialize() }
        }

        private val sensitiveTerms = listOf("ssn", "social security", "passport", "confidential", "bank", "account")
    }
}

object AssistantResultFormatter {
    fun format(result: AssistantResult): String {
        val citations = if (result.citations.isEmpty()) "No citations" else result.citations.joinToString(separator = "\n") { citation -> "[${citation.anchor.pageLabel}] ${citation.anchor.regionLabel}: ${citation.anchor.quote.take(90)}" }
        return buildString {
            appendLine(result.headline)
            appendLine(result.body)
            if (result.actionItems.isNotEmpty()) {
                appendLine("Action items:")
                result.actionItems.forEach { appendLine("- $it") }
            }
            appendLine("Citations:")
            append(citations)
        }.trim()
    }

    fun citationAnchor(pageIndex: Int, bounds: com.aymanelbanhawy.editor.core.model.NormalizedRect, quote: String): CitationAnchor = CitationAnchor(pageIndex, bounds, quote)
}
