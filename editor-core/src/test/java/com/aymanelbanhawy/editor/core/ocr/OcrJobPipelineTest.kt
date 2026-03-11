package com.aymanelbanhawy.editor.core.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.OcrSettingsDao
import com.aymanelbanhawy.editor.core.data.OcrSettingsEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Test
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OcrJobPipelineTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test
    fun completePersistsSearchAndSidecar() = runTest {
        val dao = FakeOcrJobDao()
        val settingsDao = FakeOcrSettingsDao()
        val searchService = RecordingSearchService()
        val tempDir = createTempDir(prefix = "ocr-pipeline-test")
        val sidecarStore = OcrSessionStore(json)
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = settingsDao,
            searchService = searchService,
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = sidecarStore,
        )
        val settings = OcrSettingsModel()
        val job = OcrJobEntity(
            id = "doc::0",
            documentKey = File(tempDir, "doc.pdf").absolutePath,
            pageIndex = 0,
            imagePath = File(tempDir, "page.png").absolutePath,
            status = OcrJobStatus.Running.name,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(job)

        pipeline.complete(
            job = job,
            settings = settings,
            result = OcrEngineResult(
                page = OcrPageContent(
                    pageIndex = 0,
                    text = "Invoice total due",
                    blocks = listOf(
                        OcrTextBlockModel(
                            text = "Invoice total due",
                            bounds = NormalizedRect(0.1f, 0.1f, 0.8f, 0.2f),
                            lines = listOf(
                                OcrTextLineModel(
                                    text = "Invoice total due",
                                    bounds = NormalizedRect(0.1f, 0.1f, 0.8f, 0.2f),
                                    elements = listOf(OcrTextElementModel("Invoice", NormalizedRect(0.1f, 0.1f, 0.3f, 0.2f))),
                                ),
                            ),
                        ),
                    ),
                    imageWidth = 1000,
                    imageHeight = 1600,
                ),
                preprocessedImagePath = File(tempDir, "processed.png").absolutePath,
            ),
        )

        val stored = dao.job("doc::0")
        assertThat(stored?.status).isEqualTo(OcrJobStatus.Completed.name)
        assertThat(searchService.lastPageText).isEqualTo("Invoice total due")
        assertThat(File(job.documentKey + OcrSessionStore.SIDE_CAR_SUFFIX).exists()).isTrue()
    }

    @Test
    fun failMovesRetryableJobsToRetryScheduled() = runTest {
        val dao = FakeOcrJobDao()
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = FakeOcrSettingsDao(),
            searchService = RecordingSearchService(),
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = OcrSessionStore(json),
        )
        val job = OcrJobEntity(
            id = "doc::1",
            documentKey = "/tmp/doc.pdf",
            pageIndex = 1,
            imagePath = "/tmp/one.png",
            status = OcrJobStatus.Running.name,
            attemptCount = 1,
            maxAttempts = 3,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(job)

        pipeline.fail(job, OcrEngineDiagnostics(code = "model-unavailable", message = "Model unavailable", retryable = true))

        assertThat(dao.job("doc::1")?.status).isEqualTo(OcrJobStatus.RetryScheduled.name)
    }
}

private class FakeOcrJobDao : OcrJobDao {
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
    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> = entities.values.filter { it.documentKey == documentKey }
    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = flow
    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? = entities.values.firstOrNull { it.documentKey == documentKey && it.pageIndex == pageIndex }
    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> = entities.values.filter { it.documentKey == documentKey }.take(limit)

    private fun emit() {
        flow.value = entities.values.sortedBy { it.pageIndex }
    }
}

private class FakeOcrSettingsDao : OcrSettingsDao {
    private var entity: OcrSettingsEntity? = null
    override suspend fun upsert(entity: OcrSettingsEntity) { this.entity = entity }
    override suspend fun get(id: String): OcrSettingsEntity? = entity
}

private class RecordingSearchService : DocumentSearchService {
    var lastPageText: String? = null

    override suspend fun ensureIndex(document: DocumentModel, forceRefresh: Boolean): List<IndexedPageContent> = emptyList()
    override suspend fun search(document: DocumentModel, query: String): SearchResultSet = SearchResultSet()
    override suspend fun recentSearches(documentKey: String, limit: Int): List<String> = emptyList()
    override suspend fun outline(documentRef: PdfDocumentRef): List<OutlineItem> = emptyList()
    override suspend fun selectionForBounds(document: DocumentModel, pageIndex: Int, bounds: NormalizedRect): TextSelectionPayload? = null
    override suspend fun attachOcrResult(documentKey: String, pageIndex: Int, pageText: String, blocks: List<ExtractedTextBlock>) { lastPageText = pageText }
}


