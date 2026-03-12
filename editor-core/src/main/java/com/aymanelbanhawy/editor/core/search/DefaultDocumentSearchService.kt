package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import kotlin.math.max
import kotlin.math.min

class DefaultDocumentSearchService(
    private val store: RoomSearchIndexStore,
    private val extractionService: TextExtractionService,
    private val ocrSessionStore: OcrSessionStore,
    private val diagnosticsRepository: RuntimeDiagnosticsRepository? = null,
) : DocumentSearchService {

    override suspend fun ensureIndex(document: DocumentModel, forceRefresh: Boolean): List<IndexedPageContent> {
        val existing = if (forceRefresh) emptyList() else store.indexedPages(document.documentRef.sourceKey)
        if (!forceRefresh && existing.size == document.pageCount && existing.isNotEmpty()) {
            applyPersistedOcr(document)
            return store.indexedPages(document.documentRef.sourceKey)
        }
        val startedAt = System.currentTimeMillis()
        store.clearDocument(document.documentRef.sourceKey)
        when (val extractor = extractionService) {
            is PdfBoxTextExtractionService -> {
                extractor.extractInChunks(document.documentRef, chunkSize = recommendedChunkSize(document.pageCount)) { chunk ->
                    store.saveEmbeddedIndexChunk(document.documentRef.sourceKey, chunk)
                }
            }
            else -> store.saveEmbeddedIndex(document.documentRef.sourceKey, extractor.extract(document.documentRef))
        }
        applyPersistedOcr(document)
        diagnosticsRepository?.recordBreadcrumb(
            category = RuntimeEventCategory.Indexing,
            level = RuntimeLogLevel.Info,
            eventName = "search_index_ready",
            message = "Indexed ${document.pageCount} pages in ${System.currentTimeMillis() - startedAt}ms.",
            metadata = mapOf("document" to document.documentRef.displayName),
        )
        return store.indexedPages(document.documentRef.sourceKey)
    }

    override suspend fun search(document: DocumentModel, query: String): SearchResultSet {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return SearchResultSet(indexedPageCount = 0)
        }
        val pages = ensureIndex(document)
        val lowered = trimmed.lowercase()
        val hits = pages.flatMap { page ->
            page.blocks.mapNotNull { block ->
                val text = block.text.trim()
                if (!text.lowercase().contains(lowered)) return@mapNotNull null
                SearchHit(
                    pageIndex = page.pageIndex,
                    matchText = text,
                    preview = preview(text, trimmed),
                    bounds = block.bounds,
                    source = block.source,
                )
            }
        }
        if (hits.isNotEmpty()) {
            store.rememberSearch(document.documentRef.sourceKey, trimmed)
        }
        return SearchResultSet(
            query = trimmed,
            hits = hits,
            selectedHitIndex = hits.indices.firstOrNull() ?: -1,
            indexedPageCount = pages.size,
        )
    }

    override suspend fun recentSearches(documentKey: String, limit: Int): List<String> {
        return store.recentSearches(documentKey, limit)
    }

    override suspend fun outline(documentRef: com.aymanelbanhawy.editor.core.model.PdfDocumentRef): List<OutlineItem> {
        return extractionService.extractOutline(documentRef)
    }

    override suspend fun selectionForBounds(document: DocumentModel, pageIndex: Int, bounds: NormalizedRect): TextSelectionPayload? {
        val blocks = ensureIndex(document)
            .firstOrNull { it.pageIndex == pageIndex }
            ?.blocks
            ?.filter { intersects(it.bounds, bounds) }
            .orEmpty()
        if (blocks.isEmpty()) return null
        return TextSelectionPayload(
            pageIndex = pageIndex,
            text = blocks.joinToString(separator = "\n") { it.text.trim() }.trim(),
            blocks = blocks,
        )
    }

    override suspend fun attachOcrResult(documentKey: String, pageIndex: Int, pageText: String, blocks: List<ExtractedTextBlock>) {
        store.saveOcrIndex(documentKey, pageIndex, pageText, blocks)
    }

    private suspend fun applyPersistedOcr(document: DocumentModel) {
        ocrSessionStore.load(document.documentRef)
            ?.pages
            ?.forEach { page ->
                store.saveOcrIndex(document.documentRef.sourceKey, page.pageIndex, page.text, page.flattenedSearchBlocks())
            }
    }

    private fun preview(text: String, query: String): String {
        val lowered = text.lowercase()
        val index = lowered.indexOf(query.lowercase())
        if (index < 0) return text.take(90)
        val start = max(0, index - 24)
        val end = min(text.length, index + query.length + 36)
        return text.substring(start, end).trim()
    }

    private fun intersects(left: NormalizedRect, right: NormalizedRect): Boolean {
        return left.left < right.right && left.right > right.left && left.top < right.bottom && left.bottom > right.top
    }

    internal fun recommendedChunkSize(pageCount: Int): Int {
        return when {
            pageCount >= 600 -> 6
            pageCount >= 250 -> 10
            pageCount >= 100 -> 14
            else -> 20
        }
    }
}
