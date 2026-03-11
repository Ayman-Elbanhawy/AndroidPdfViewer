package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FakeAiBackendProviderTest {
    private val provider = FakeAiBackendProvider()

    @Test
    fun generate_returnsGroundedSemanticCards() = kotlinx.coroutines.test.runTest {
        val result = provider.generate(
            AssistantPromptRequest(
                task = AssistantTaskType.SemanticSearch,
                prompt = "contract renewal",
                documentTitle = "sample.pdf",
                currentPageIndex = 0,
                selectionText = "",
                pageContext = listOf(
                    GroundedPageContext(
                        pageIndex = 1,
                        snippets = listOf(
                            GroundedSnippet("Contract renewal should be reviewed before signing.", NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f)),
                        ),
                    ),
                ),
                privacyMode = AssistantPrivacyMode.LocalOnly,
            ),
        )

        assertThat(result.semanticCards).isNotEmpty()
        assertThat(result.citations.first().anchor.pageLabel).isEqualTo("Page 2")
        assertThat(result.body).contains("Page 2")
    }
}
