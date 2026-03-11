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
    System,
}

@Serializable
enum class AssistiveSuggestionType {
    Redaction,
    FormAutofill,
}

@Serializable
enum class AiProviderKind {
    OllamaLocal,
    OllamaRemote,
    OpenAi,
    OpenAiCompatible,
}

@Serializable
enum class AiProviderErrorCode {
    Offline,
    Timeout,
    Cancelled,
    Unauthorized,
    Forbidden,
    BadRequest,
    NotFound,
    RateLimited,
    Server,
    PolicyBlocked,
    InvalidConfiguration,
    ParseFailure,
    Unknown,
}

@Serializable
enum class AiProviderStatus {
    Idle,
    Discovering,
    Testing,
    Streaming,
    Completed,
    Failed,
    Cancelled,
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
data class AiProviderCapabilities(
    val supportsStreaming: Boolean = true,
    val maxContextHint: Int? = null,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsJsonMode: Boolean = false,
)

@Serializable
data class AiProviderModelInfo(
    val id: String,
    val displayName: String,
    val owner: String = "",
    val capabilitySummary: String = "",
    val capabilities: AiProviderCapabilities = AiProviderCapabilities(),
)

@Serializable
data class AiProviderProfile(
    val id: String,
    val kind: AiProviderKind,
    val displayName: String,
    val endpointUrl: String,
    val modelId: String = "",
    val hasStoredCredential: Boolean = false,
    val requestTimeoutSeconds: Int = 90,
    val retryCount: Int = 2,
)

@Serializable
data class AiProviderDraft(
    val profileId: String = DEFAULT_PROVIDER_ID,
    val kind: AiProviderKind = AiProviderKind.OllamaLocal,
    val displayName: String = "Local Ollama",
    val endpointUrl: String = "http://10.0.2.2:11434",
    val modelId: String = "",
    val apiKeyInput: String = "",
    val requestTimeoutSeconds: Int = 90,
    val retryCount: Int = 2,
)

@Serializable
data class LocalAiAppInfo(
    val packageName: String,
    val appName: String,
)

@Serializable
data class AiProviderError(
    val code: AiProviderErrorCode,
    val message: String,
    val retryable: Boolean,
    val providerId: String? = null,
)

@Serializable
data class AiProviderRuntimeState(
    val selectedProviderId: String = DEFAULT_PROVIDER_ID,
    val profiles: List<AiProviderProfile> = defaultProviderProfiles(),
    val draft: AiProviderDraft = defaultProviderProfiles().first().toDraft(),
    val availableModels: List<AiProviderModelInfo> = emptyList(),
    val activeCapabilities: AiProviderCapabilities? = null,
    val discoveredLocalApps: List<LocalAiAppInfo> = emptyList(),
    val status: AiProviderStatus = AiProviderStatus.Idle,
    val streamPreview: String = "",
    val diagnosticsMessage: String = "",
    val lastError: AiProviderError? = null,
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
    val providerRuntime: AiProviderRuntimeState = AiProviderRuntimeState(),
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

@Serializable
data class AssistantPersistenceModel(
    val settings: AssistantSettings = AssistantSettings(),
    val selectedProviderId: String = DEFAULT_PROVIDER_ID,
    val profiles: List<AiProviderProfile> = defaultProviderProfiles(),
)

internal fun Float.formatPercent(): String = (this * 100f).toInt().toString() + "%"

fun AiProviderProfile.toDraft(apiKeyInput: String = ""): AiProviderDraft = AiProviderDraft(
    profileId = id,
    kind = kind,
    displayName = displayName,
    endpointUrl = endpointUrl,
    modelId = modelId,
    apiKeyInput = apiKeyInput,
    requestTimeoutSeconds = requestTimeoutSeconds,
    retryCount = retryCount,
)

fun AiProviderDraft.toProfile(hasStoredCredential: Boolean): AiProviderProfile = AiProviderProfile(
    id = profileId,
    kind = kind,
    displayName = displayName.ifBlank { kind.name },
    endpointUrl = endpointUrl.trim(),
    modelId = modelId.trim(),
    hasStoredCredential = hasStoredCredential,
    requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(15, 300),
    retryCount = retryCount.coerceIn(0, 4),
)

internal fun defaultProviderProfiles(): List<AiProviderProfile> = listOf(
    AiProviderProfile(
        id = DEFAULT_PROVIDER_ID,
        kind = AiProviderKind.OllamaLocal,
        displayName = "Local Ollama",
        endpointUrl = "http://10.0.2.2:11434",
    ),
    AiProviderProfile(
        id = "ollama-remote",
        kind = AiProviderKind.OllamaRemote,
        displayName = "Remote Ollama",
        endpointUrl = "https://ollama.example.com",
    ),
    AiProviderProfile(
        id = "openai",
        kind = AiProviderKind.OpenAi,
        displayName = "OpenAI",
        endpointUrl = "https://api.openai.com/v1",
    ),
    AiProviderProfile(
        id = "openai-compatible",
        kind = AiProviderKind.OpenAiCompatible,
        displayName = "OpenAI Compatible",
        endpointUrl = "https://api.example.com/v1",
    ),
)

const val DEFAULT_PROVIDER_ID: String = "ollama-local"


