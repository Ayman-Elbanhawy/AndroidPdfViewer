package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.RecentSearchDao
import com.aymanelbanhawy.editor.core.data.RecentSearchEntity
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.data.SearchIndexEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.ocr.OcrPageContent
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.ocr.OcrTextBlockModel
import com.aymanelbanhawy.editor.core.ocr.OcrTextElementModel
import com.aymanelbanhawy.editor.core.ocr.OcrTextLineModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            ocrSessionStore = OcrSessionStore(json),
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
            ocrSessionStore = OcrSessionStore(json),
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
            ocrSessionStore = OcrSessionStore(json),
        )

        val selection = service.selectionForBounds(document(), 0, bounds(0.08f, 0.08f, 0.42f, 0.22f))

        assertThat(selection?.text).contains("Clause A")
        assertThat(selection?.text).doesNotContain("Clause B")
    }

    @Test
    fun ensureIndexLoadsPersistedOcrFromDatabaseBackedStore() = runTest {
        val ocrDao = FakeSearchOcrJobDao()
        val ocrPage = OcrPageContent(
            pageIndex = 0,
            text = "Scanned invoice number 12345",
            blocks = listOf(
                OcrTextBlockModel(
                    text = "Scanned invoice number 12345",
                    bounds = bounds(0.1f, 0.2f, 0.9f, 0.3f),
                    lines = listOf(
                        OcrTextLineModel(
                            text = "Scanned invoice number 12345",
                            bounds = bounds(0.1f, 0.2f, 0.9f, 0.3f),
                            elements = listOf(
                                OcrTextElementModel(
                                    text = "Scanned invoice number 12345",
                                    bounds = bounds(0.1f, 0.2f, 0.9f, 0.3f),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            imageWidth = 1000,
            imageHeight = 1600,
        )
        ocrDao.upsert(
            OcrJobEntity(
                id = "doc::0",
                documentKey = document().documentRef.sourceKey,
                pageIndex = 0,
                imagePath = "/tmp/page.png",
                status = OcrJobStatus.Completed.name,
                resultText = ocrPage.text,
                resultPageJson = json.encodeToString(OcrPageContent.serializer(), ocrPage),
                settingsJson = json.encodeToString(OcrSettingsModel.serializer(), OcrSettingsModel(languageHints = listOf("en"))),
                progressPercent = 100,
                attemptCount = 1,
                maxAttempts = 2,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
            ),
        )
        val service = DefaultDocumentSearchService(
            store = RoomSearchIndexStore(FakeSearchIndexDao(), FakeRecentSearchDao(), json),
            extractionService = FakeTextExtractionService(emptyList()),
            ocrSessionStore = OcrSessionStore(json, ocrDao),
        )

        val results = service.search(document(), "invoice")

        assertThat(results.hits).hasSize(1)
        assertThat(results.hits.first().source).isEqualTo(SearchContentSource.Ocr)
        assertThat(results.hits.first().matchText).contains("invoice")
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

    override suspend fun documentKeys(): List<String> = entities.values.map { it.documentKey }.distinct()

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

private class FakeSearchOcrJobDao : OcrJobDao {
    private val entities = linkedMapOf<String, OcrJobEntity>()
    private val flow = MutableStateFlow(emptyList<OcrJobEntity>())

    override suspend fun upsert(entity: OcrJobEntity) {
        entities[entity.id] = entity
        emit()
    }

    override suspend fun upsertAll(entities: List<OcrJobEntity>) {
        entities.forEach { this.entities[it.id] = it }
        emit()
    }

    override suspend fun job(id: String): OcrJobEntity? = entities[id]

    override suspend fun all(): List<OcrJobEntity> = entities.values.toList()

    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> =
        entities.values.filter { it.documentKey == documentKey }.sortedBy { it.pageIndex }

    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = flow

    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? =
        entities.values.firstOrNull { it.documentKey == documentKey && it.pageIndex == pageIndex }

    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> =
        entities.values.filter { it.documentKey == documentKey }.take(limit)

    private fun emit() {
        flow.value = entities.values.sortedBy { it.pageIndex }
    }
}
