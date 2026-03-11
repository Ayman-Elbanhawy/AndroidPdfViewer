package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.data.RecentSearchDao
import com.aymanelbanhawy.editor.core.data.RecentSearchEntity
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.data.SearchIndexEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultDocumentSearchServiceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    @Test
    fun searchFindsHitsAndStoresRecentQueries() = runTest {
        val searchIndexDao = FakeSearchIndexDao()
        val recentSearchDao = FakeRecentSearchDao()
        val store = RoomSearchIndexStore(searchIndexDao, recentSearchDao, json)
        val service = DefaultDocumentSearchService(
            store = store,
            extractionService = FakeTextExtractionService(
                listOf(
                    IndexedPageContent(
                        pageIndex = 0,
                        pageText = "Master service agreement",
                        blocks = listOf(
                            ExtractedTextBlock(0, "Master service agreement", bounds(0.1f, 0.2f, 0.6f, 0.28f)),
                        ),
                    ),
                ),
            ),
        )

        val result = service.search(document(), "service")

        assertThat(result.hits).hasSize(1)
        assertThat(result.hits.first().pageIndex).isEqualTo(0)
        assertThat(service.recentSearches(document().documentRef.sourceKey)).contains("service")
    }

    @Test
    fun ensureIndexUsesCachedPagesWhenCountMatches() = runTest {
        val extractionService = FakeTextExtractionService(
            listOf(IndexedPageContent(0, "Page one", listOf(ExtractedTextBlock(0, "Page one", bounds(0.1f, 0.1f, 0.3f, 0.2f))))),
        )
        val service = DefaultDocumentSearchService(
            store = RoomSearchIndexStore(FakeSearchIndexDao(), FakeRecentSearchDao(), json),
            extractionService = extractionService,
        )

        service.ensureIndex(document(), forceRefresh = false)
        service.ensureIndex(document(), forceRefresh = false)

        assertThat(extractionService.extractCalls).isEqualTo(1)
    }

    @Test
    fun selectionForBoundsReturnsIntersectingText() = runTest {
        val service = DefaultDocumentSearchService(
            store = RoomSearchIndexStore(FakeSearchIndexDao(), FakeRecentSearchDao(), json),
            extractionService = FakeTextExtractionService(
                listOf(
                    IndexedPageContent(
                        pageIndex = 0,
                        pageText = "Clause A\nClause B",
                        blocks = listOf(
                            ExtractedTextBlock(0, "Clause A", bounds(0.1f, 0.1f, 0.4f, 0.2f)),
                            ExtractedTextBlock(0, "Clause B", bounds(0.45f, 0.1f, 0.8f, 0.2f)),
                        ),
                    ),
                ),
            ),
        )

        val selection = service.selectionForBounds(document(), 0, bounds(0.08f, 0.08f, 0.42f, 0.22f))

        assertThat(selection?.text).contains("Clause A")
        assertThat(selection?.text).doesNotContain("Clause B")
    }

    private fun document(): DocumentModel {
        return DocumentModel(
            sessionId = "session",
            documentRef = PdfDocumentRef(
                uriString = "file:///tmp/doc.pdf",
                displayName = "doc.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "/tmp/doc.pdf",
                workingCopyPath = "/tmp/doc.pdf",
            ),
            pages = listOf(PageModel(index = 0, label = "1")),
        )
    }

    private fun bounds(left: Float, top: Float, right: Float, bottom: Float) = com.aymanelbanhawy.editor.core.model.NormalizedRect(left, top, right, bottom)
}

private class FakeTextExtractionService(
    private val pages: List<IndexedPageContent>,
) : TextExtractionService {
    var extractCalls: Int = 0

    override suspend fun extract(documentRef: PdfDocumentRef): List<IndexedPageContent> {
        extractCalls += 1
        return pages
    }

    override suspend fun extractOutline(documentRef: PdfDocumentRef): List<OutlineItem> = emptyList()
}

private class FakeSearchIndexDao : SearchIndexDao {
    private val entities = linkedMapOf<Pair<String, Int>, SearchIndexEntity>()

    override suspend fun upsertAll(entities: List<SearchIndexEntity>) {
        entities.forEach { entity -> this.entities[entity.documentKey to entity.pageIndex] = entity }
    }

    override suspend fun indexForDocument(documentKey: String): List<SearchIndexEntity> {
        return entities.values.filter { it.documentKey == documentKey }.sortedBy { it.pageIndex }
    }

    override suspend fun updateOcrPayload(documentKey: String, pageIndex: Int, ocrText: String?, ocrBlocksJson: String?, updatedAtEpochMillis: Long) {
        val existing = entities[documentKey to pageIndex] ?: return
        entities[documentKey to pageIndex] = existing.copy(ocrText = ocrText, ocrBlocksJson = ocrBlocksJson, updatedAtEpochMillis = updatedAtEpochMillis)
    }

    override suspend fun deleteForDocument(documentKey: String) {
        entities.keys.removeAll { it.first == documentKey }
    }
}

private class FakeRecentSearchDao : RecentSearchDao {
    private val entities = mutableListOf<RecentSearchEntity>()

    override suspend fun insert(entity: RecentSearchEntity) {
        entities += entity.copy(id = entities.size.toLong() + 1)
    }

    override suspend fun recentForDocument(documentKey: String, limit: Int): List<RecentSearchEntity> {
        return entities.filter { it.documentKey == documentKey }.sortedByDescending { it.searchedAtEpochMillis }.take(limit)
    }

    override suspend fun trim(documentKey: String, keepCount: Int) {
        val keep = recentForDocument(documentKey, keepCount).map { it.id }.toSet()
        entities.removeAll { it.documentKey == documentKey && it.id !in keep }
    }
}
