package com.aymanelbanhawy.enterprisepdf.app.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AiProviderRuntimeState
import com.aymanelbanhawy.aiassistant.core.AssistantAvailability
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistantWorkspaceState
import com.aymanelbanhawy.aiassistant.ui.AssistantSidebar
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AssistantProviderSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providerSettings_andAskFlow_areReachable() {
        var state by mutableStateOf(
            AssistantUiState(
                settings = AssistantSettings(privacyMode = AssistantPrivacyMode.LocalOnly),
                availability = AssistantAvailability(enabled = true),
                providerRuntime = AiProviderRuntimeState(
                    draft = AiProviderDraft(
                        displayName = "Local Ollama",
                        endpointUrl = "http://10.0.2.2:11434",
                        modelId = "llama3.2",
                    ),
                ),
                workspace = AssistantWorkspaceState(),
            ),
        )
        var savedProvider = false
        var askTriggered = false

        composeRule.setContent {
            MaterialTheme {
                AssistantSidebar(
                    state = state,
                    onPromptChanged = { state = state.copy(prompt = it) },
                    onAskPdf = { askTriggered = true },
                    onSummarizeDocument = {},
                    onSummarizePage = {},
                    onExtractActionItems = {},
                    onExplainSelection = {},
                    onSemanticSearch = {},
                    onAskWorkspace = {},
                    onSummarizeWorkspace = {},
                    onCompareWorkspace = {},
                    onPinCurrentDocument = {},
                    onToggleWorkspaceDocument = { _, _ -> },
                    onUnpinDocument = {},
                    onSaveWorkspaceSet = {},
                    onPrivacyModeChanged = { mode -> state = state.copy(settings = state.settings.copy(privacyMode = mode)) },
                    onProviderDraftChanged = { draft -> state = state.copy(providerRuntime = state.providerRuntime.copy(draft = draft)) },
                    onSaveProvider = { savedProvider = true },
                    onRefreshProviders = {},
                    onTestConnection = {},
                    onCancelRequest = {},
                    onOpenCitation = {},
                )
            }
        }

        composeRule.onNodeWithText("AI Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Provider").assertIsDisplayed()
        composeRule.onNode(hasText("http://10.0.2.2:11434") and hasSetTextAction()).performTextInput("/v1")
        composeRule.onNodeWithContentDescription("Save Provider").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Ask about this PDF or document set")).performTextInput("Summarize the contract obligations")
        composeRule.onNodeWithContentDescription("Ask PDF").performClick()

        assertThat(savedProvider).isTrue()
        assertThat(askTriggered).isTrue()
    }
}

