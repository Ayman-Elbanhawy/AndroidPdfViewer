package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class AssistantPrivacyMode {
    LocalOnly,
    CloudAssisted,
}

@Serializable
enum class AssistantTaskType {
    AskPdf,
    SummarizeDocument,
    SummarizePage,
    ExtractActionItems,
    ExplainSelection,
    SemanticSearch,
}

@Serializable
enum class AssistantMessageRole {
    User,
    Assistant,
}

@Serializable
enum class AssistiveSuggestionType {
    Redaction,
    FormAutofill,
}

@Serializable
data class CitationAnchor(
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val quote: String,
) {
    val pageLabel: String
        get() = "Page ${pageIndex + 1}"

    val regionLabel: String
        get() = "${bounds.left.formatPercent()}-${bounds.right.formatPercent()} x ${bounds.top.formatPercent()}-${bounds.bottom.formatPercent()}"
}

@Serializable
data class AssistantCitation(
    val id: String,
    val title: String,
    val anchor: CitationAnchor,
    val confidence: Float,
)

@Serializable
data class SemanticSearchCard(
    val id: String,
    val title: String,
    val snippet: String,
    val anchor: CitationAnchor,
    val score: Float,
)

@Serializable
data class AssistiveSuggestion(
    val id: String,
    val type: AssistiveSuggestionType,
    val title: String,
    val detail: String,
    val anchor: CitationAnchor,
    val suggestedValue: String = "",
    val fieldName: String = "",
)

@Serializable
data class AssistantMessage(
    val id: String,
    val role: AssistantMessageRole,
    val task: AssistantTaskType,
    val text: String,
    val citations: List<AssistantCitation> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Serializable
data class AssistantResult(
    val task: AssistantTaskType,
    val headline: String,
    val body: String,
    val citations: List<AssistantCitation>,
    val semanticCards: List<SemanticSearchCard> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val suggestions: List<AssistiveSuggestion> = emptyList(),
    val generatedAtEpochMillis: Long,
)

@Serializable
data class AssistantSettings(
    val privacyMode: AssistantPrivacyMode = AssistantPrivacyMode.LocalOnly,
    val redactBeforeCloud: Boolean = true,
    val allowSuggestions: Boolean = true,
)

@Serializable
data class AssistantAvailability(
    val enabled: Boolean,
    val reason: String? = null,
    val missingFeatures: Set<FeatureFlag> = emptySet(),
)

@Serializable
data class AssistantUiState(
    val settings: AssistantSettings = AssistantSettings(),
    val availability: AssistantAvailability = AssistantAvailability(enabled = false, reason = "AI is disabled"),
    val prompt: String = "",
    val isWorking: Boolean = false,
    val latestResult: AssistantResult? = null,
    val conversation: List<AssistantMessage> = emptyList(),
    val semanticCards: List<SemanticSearchCard> = emptyList(),
    val suggestions: List<AssistiveSuggestion> = emptyList(),
)

@Serializable
data class AssistantPromptRequest(
    val task: AssistantTaskType,
    val prompt: String,
    val documentTitle: String,
    val currentPageIndex: Int,
    val selectionText: String,
    val pageContext: List<GroundedPageContext>,
    val privacyMode: AssistantPrivacyMode,
)

@Serializable
data class GroundedPageContext(
    val pageIndex: Int,
    val snippets: List<GroundedSnippet>,
)

@Serializable
data class GroundedSnippet(
    val text: String,
    val bounds: NormalizedRect,
)

internal fun Float.formatPercent(): String = (this * 100f).toInt().toString() + "%"
