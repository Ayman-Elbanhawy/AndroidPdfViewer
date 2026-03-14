package com.aymanelbanhawy.enterprisepdf.app.visual

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.aiassistant.core.AiProviderRuntimeState
import com.aymanelbanhawy.aiassistant.core.AssistantAudioUiState
import com.aymanelbanhawy.aiassistant.core.AssistantAvailability
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistantWorkspaceState
import com.aymanelbanhawy.aiassistant.ui.AssistantSidebar
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.search.SearchContentSource
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.enterprisepdf.app.search.SearchSidebar
import com.aymanelbanhawy.enterprisepdf.app.theme.EnterprisePdfTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualSnapshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun searchSidebar_lightAndDarkSnapshots_render() {
        renderSnapshot(name = "search-light", darkTheme = false) {
            SearchSidebar(
                modifier = Modifier,
                query = "agreement",
                results = SearchResultSet(
                    query = "agreement",
                    hits = listOf(
                        SearchHit(
                            pageIndex = 0,
                            matchText = "agreement",
                            preview = "Service agreement obligations and renewal dates.",
                            bounds = NormalizedRect(0.12f, 0.16f, 0.64f, 0.24f),
                            source = SearchContentSource.EmbeddedText,
                        ),
                    ),
                    selectedHitIndex = 0,
                    indexedPageCount = 1,
                ),
                recentSearches = listOf("obligations", "renewal"),
                outlineItems = emptyList(),
                selectedText = "Service agreement obligations and renewal dates.",
                isIndexing = false,
                ocrJobs = emptyList(),
                ocrSettings = OcrSettingsModel(),
                onQueryChanged = {},
                onSearch = {},
                onSelectHit = {},
                onPreviousHit = {},
                onNextHit = {},
                onUseRecentSearch = {},
                onOpenOutlineItem = {},
                onCopySelectedText = {},
                onShareSelectedText = {},
                onOcrSettingsChanged = {},
                onSaveOcrSettings = {},
                onPauseOcr = {},
                onResumeOcr = {},
                onRerunOcr = {},
                onOpenOcrPage = {},
            )
        }
        renderSnapshot(name = "search-dark", darkTheme = true) {
            SearchSidebar(
                modifier = Modifier,
                query = "renewal",
                results = SearchResultSet(
                    query = "renewal",
                    hits = listOf(
                        SearchHit(
                            pageIndex = 0,
                            matchText = "renewal",
                            preview = "The renewal notice period is ninety days.",
                            bounds = NormalizedRect(0.18f, 0.32f, 0.72f, 0.41f),
                            source = SearchContentSource.Ocr,
                        ),
                    ),
                    selectedHitIndex = 0,
                    indexedPageCount = 1,
                ),
                recentSearches = emptyList(),
                outlineItems = emptyList(),
                selectedText = "The renewal notice period is ninety days.",
                isIndexing = false,
                ocrJobs = emptyList(),
                ocrSettings = OcrSettingsModel(),
                onQueryChanged = {},
                onSearch = {},
                onSelectHit = {},
                onPreviousHit = {},
                onNextHit = {},
                onUseRecentSearch = {},
                onOpenOutlineItem = {},
                onCopySelectedText = {},
                onShareSelectedText = {},
                onOcrSettingsChanged = {},
                onSaveOcrSettings = {},
                onPauseOcr = {},
                onResumeOcr = {},
                onRerunOcr = {},
                onOpenOcrPage = {},
            )
        }
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun assistantSidebar_lightAndDarkSnapshots_render() {
        val state = AssistantUiState(
            settings = AssistantSettings(
                privacyMode = AssistantPrivacyMode.LocalOnly,
                spokenResponsesEnabled = true,
                voicePromptCaptureEnabled = true,
            ),
            availability = AssistantAvailability(enabled = true),
            providerRuntime = AiProviderRuntimeState(),
            workspace = AssistantWorkspaceState(),
            audio = AssistantAudioUiState(enabled = true),
        )
        renderSnapshot(name = "assistant-light", darkTheme = false) {
            AssistantSidebar(
                state = state,
                onPromptChanged = {},
                onAskPdf = {},
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
                onPrivacyModeChanged = {},
                onProviderDraftChanged = {},
                onSaveProvider = {},
                onRefreshProviders = {},
                onTestConnection = {},
                onCancelRequest = {},
                onOpenCitation = {},
                onStartVoicePromptCapture = {},
                onStopVoicePromptCapture = {},
                onCancelVoicePromptCapture = {},
                onReadCurrentPageAloud = {},
                onReadSelectionAloud = {},
                onStopReadAloud = {},
                onAssistantAudioEnabledChanged = {},
            )
        }
        renderSnapshot(name = "assistant-dark", darkTheme = true) {
            AssistantSidebar(
                state = state,
                onPromptChanged = {},
                onAskPdf = {},
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
                onPrivacyModeChanged = {},
                onProviderDraftChanged = {},
                onSaveProvider = {},
                onRefreshProviders = {},
                onTestConnection = {},
                onCancelRequest = {},
                onOpenCitation = {},
                onStartVoicePromptCapture = {},
                onStopVoicePromptCapture = {},
                onCancelVoicePromptCapture = {},
                onReadCurrentPageAloud = {},
                onReadSelectionAloud = {},
                onStopReadAloud = {},
                onAssistantAudioEnabledChanged = {},
            )
        }
    }

    private fun renderSnapshot(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            EnterprisePdfTheme(darkTheme = darkTheme) {
                Surface { content() }
            }
        }
        val tag = if (name.startsWith("assistant")) "assistant-sidebar" else "search-sidebar"
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        val output = File(composeRule.activity.cacheDir, "$name.png")
        output.outputStream().use { stream ->
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertThat(output.exists()).isTrue()
        assertThat(output.length()).isGreaterThan(0)
    }
}



