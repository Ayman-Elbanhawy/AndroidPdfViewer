package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import java.util.Locale
import java.util.UUID

interface AiBackendProvider {
    suspend fun generate(request: AssistantPromptRequest): AssistantResult
}

class FakeAiBackendProvider : AiBackendProvider {
    override suspend fun generate(request: AssistantPromptRequest): AssistantResult {
        val snippets = request.pageContext.flatMap { context ->
            context.snippets.map { snippet ->
                context.pageIndex to snippet
            }
        }
        val citations = snippets.take(4).mapIndexed { index, (pageIndex, snippet) ->
            AssistantCitation(
                id = "citation-${index + 1}",
                title = "${citationTitle(request.task)} ${pageIndex + 1}",
                anchor = CitationAnchor(pageIndex = pageIndex, bounds = snippet.bounds, quote = snippet.text.take(180)),
                confidence = 0.72f + (index * 0.06f),
            )
        }
        val semanticCards = snippets.take(5).mapIndexed { index, (pageIndex, snippet) ->
            SemanticSearchCard(
                id = UUID.randomUUID().toString(),
                title = "${request.documentTitle} • Page ${pageIndex + 1}",
                snippet = snippet.text.take(180),
                anchor = CitationAnchor(pageIndex = pageIndex, bounds = snippet.bounds, quote = snippet.text.take(140)),
                score = (0.92f - (index * 0.08f)).coerceAtLeast(0.35f),
            )
        }
        val body = when (request.task) {
            AssistantTaskType.AskPdf -> buildAskAnswer(request, citations)
            AssistantTaskType.SummarizeDocument -> buildDocumentSummary(request, citations)
            AssistantTaskType.SummarizePage -> buildPageSummary(request, citations)
            AssistantTaskType.ExtractActionItems -> buildActionItems(request, citations)
            AssistantTaskType.ExplainSelection -> buildSelectionExplanation(request, citations)
            AssistantTaskType.SemanticSearch -> buildSemanticExplanation(request, semanticCards)
        }
        val actionItems = if (request.task == AssistantTaskType.ExtractActionItems) {
            extractBullets(request)
        } else {
            emptyList()
        }
        val suggestions = buildSuggestions(request, snippets)
        return AssistantResult(
            task = request.task,
            headline = headlineFor(request),
            body = body,
            citations = citations,
            semanticCards = if (request.task == AssistantTaskType.SemanticSearch) semanticCards else semanticCards.take(3),
            actionItems = actionItems,
            suggestions = suggestions,
            generatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun buildAskAnswer(request: AssistantPromptRequest, citations: List<AssistantCitation>): String {
        val source = citations.joinToString(separator = " ") { citation -> "${citation.anchor.pageLabel}: ${citation.anchor.quote}." }
        return buildString {
            append("Grounded answer")
            if (request.privacyMode == AssistantPrivacyMode.LocalOnly) {
                append(" using local-only context")
            } else {
                append(" using cloud-assisted ready context")
            }
            append(": ")
            append(source.take(500).ifBlank { "No indexed text is available yet." })
        }
    }

    private fun buildDocumentSummary(request: AssistantPromptRequest, citations: List<AssistantCitation>): String {
        val first = citations.firstOrNull()?.anchor?.quote ?: "No text indexed for summary."
        val second = citations.getOrNull(1)?.anchor?.quote.orEmpty()
        return listOf(first, second).filter { it.isNotBlank() }.joinToString(separator = " ")
    }

    private fun buildPageSummary(request: AssistantPromptRequest, citations: List<AssistantCitation>): String {
        val pageCitation = citations.firstOrNull { it.anchor.pageIndex == request.currentPageIndex } ?: citations.firstOrNull()
        return pageCitation?.anchor?.quote ?: "This page does not have extracted text yet."
    }

    private fun buildActionItems(request: AssistantPromptRequest, citations: List<AssistantCitation>): String {
        val bullets = extractBullets(request)
        return if (bullets.isEmpty()) "No obvious action items were detected in the grounded text." else bullets.joinToString(separator = "\n") { "- $it" }
    }

    private fun buildSelectionExplanation(request: AssistantPromptRequest, citations: List<AssistantCitation>): String {
        if (request.selectionText.isBlank()) {
            return "Select text on the page to get a grounded explanation."
        }
        val supporting = citations.firstOrNull()?.anchor?.quote.orEmpty()
        return "Selection meaning: ${request.selectionText.take(220)}. Supporting context: $supporting"
    }

    private fun buildSemanticExplanation(request: AssistantPromptRequest, cards: List<SemanticSearchCard>): String {
        return if (cards.isEmpty()) {
            "No semantically similar regions were found in the indexed document."
        } else {
            cards.take(3).joinToString(separator = " ") { card -> "${card.anchor.pageLabel}: ${card.snippet}." }
        }
    }

    private fun buildSuggestions(
        request: AssistantPromptRequest,
        snippets: List<Pair<Int, GroundedSnippet>>,
    ): List<AssistiveSuggestion> {
        val loweredNeedle = request.prompt.lowercase(Locale.US)
        val redactionSuggestions = snippets
            .filter { (_, snippet) -> sensitiveTerms.any { term -> snippet.text.lowercase(Locale.US).contains(term) } }
            .take(2)
            .mapIndexed { index, (pageIndex, snippet) ->
                AssistiveSuggestion(
                    id = "redaction-$index",
                    type = AssistiveSuggestionType.Redaction,
                    title = "Possible sensitive content",
                    detail = snippet.text.take(160),
                    anchor = CitationAnchor(pageIndex, snippet.bounds, snippet.text.take(120)),
                )
            }
        val autofillSuggestions = if (loweredNeedle.contains("form") || request.task == AssistantTaskType.ExplainSelection) {
            snippets.take(2).mapIndexed { index, (pageIndex, snippet) ->
                AssistiveSuggestion(
                    id = "autofill-$index",
                    type = AssistiveSuggestionType.FormAutofill,
                    title = "Candidate form value",
                    detail = snippet.text.take(120),
                    anchor = CitationAnchor(pageIndex, snippet.bounds, snippet.text.take(120)),
                    suggestedValue = snippet.text.take(48),
                    fieldName = "field_${index + 1}",
                )
            }
        } else {
            emptyList()
        }
        return redactionSuggestions + autofillSuggestions
    }

    private fun extractBullets(request: AssistantPromptRequest): List<String> {
        return request.pageContext
            .flatMap { it.snippets }
            .map { it.text.trim() }
            .filter { line ->
                val lowered = line.lowercase(Locale.US)
                actionableTerms.any(lowered::contains)
            }
            .map { text -> text.take(140) }
            .distinct()
            .take(5)
    }

    private fun citationTitle(task: AssistantTaskType): String = when (task) {
        AssistantTaskType.AskPdf -> "Answer reference"
        AssistantTaskType.SummarizeDocument -> "Summary evidence"
        AssistantTaskType.SummarizePage -> "Page evidence"
        AssistantTaskType.ExtractActionItems -> "Action evidence"
        AssistantTaskType.ExplainSelection -> "Selection evidence"
        AssistantTaskType.SemanticSearch -> "Search evidence"
    }

    private fun headlineFor(request: AssistantPromptRequest): String = when (request.task) {
        AssistantTaskType.AskPdf -> "Ask PDF"
        AssistantTaskType.SummarizeDocument -> "Document Summary"
        AssistantTaskType.SummarizePage -> "Page ${request.currentPageIndex + 1} Summary"
        AssistantTaskType.ExtractActionItems -> "Action Items"
        AssistantTaskType.ExplainSelection -> "Explain Selection"
        AssistantTaskType.SemanticSearch -> "Semantic Matches"
    }

    private companion object {
        val sensitiveTerms = listOf("ssn", "social security", "confidential", "secret", "account number", "passport")
        val actionableTerms = listOf("must", "should", "due", "action", "review", "follow up", "sign", "complete")
    }
}

object AssistantResultFormatter {
    fun format(result: AssistantResult): String {
        val citations = if (result.citations.isEmpty()) {
            "No citations"
        } else {
            result.citations.joinToString(separator = "\n") { citation ->
                "[${citation.anchor.pageLabel}] ${citation.anchor.regionLabel}: ${citation.anchor.quote.take(90)}"
            }
        }
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

    fun citationAnchor(pageIndex: Int, bounds: NormalizedRect, quote: String): CitationAnchor {
        return CitationAnchor(pageIndex = pageIndex, bounds = bounds, quote = quote)
    }
}
