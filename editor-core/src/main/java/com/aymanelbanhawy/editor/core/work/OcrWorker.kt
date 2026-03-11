package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.data.createEditorCoreDatabase
import com.aymanelbanhawy.editor.core.ocr.MlKitOcrEngine
import com.aymanelbanhawy.editor.core.ocr.OcrEngineDiagnostics
import com.aymanelbanhawy.editor.core.ocr.OcrEngineException
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.ocr.OcrPageRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.search.DefaultDocumentSearchService
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.RoomSearchIndexStore
import kotlinx.serialization.json.Json

class OcrWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentKey = inputData.getString(KEY_DOCUMENT_KEY) ?: return Result.failure()
        val database = createEditorCoreDatabase(applicationContext)
        return try {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
            val ocrSessionStore = OcrSessionStore(json)
            val store = RoomSearchIndexStore(database.searchIndexDao(), database.recentSearchDao(), json)
            val searchService = DefaultDocumentSearchService(store, PdfBoxTextExtractionService(), ocrSessionStore)
            val pipeline = OcrJobPipeline(
                ocrJobDao = database.ocrJobDao(),
                ocrSettingsDao = database.ocrSettingsDao(),
                searchService = searchService,
                workManager = WorkManager.getInstance(applicationContext),
                json = json,
                ocrSessionStore = ocrSessionStore,
            )
            val engine = MlKitOcrEngine(applicationContext)
            val settings = pipeline.loadSettings()
            val jobs = pipeline.pendingWork(documentKey, limit = 12, staleAfterMillis = 2 * 60 * 1000L)
            if (jobs.isEmpty()) return Result.success()
            var shouldRetry = false
            jobs.forEach { job ->
                val running = pipeline.markRunning(job)
                runCatching {
                    pipeline.updateProgress(running, 20)
                    val result = engine.recognize(
                        OcrPageRequest(
                            imagePath = running.imagePath,
                            pageIndex = running.pageIndex,
                            outputDirectoryPath = applicationContext.cacheDir.resolve("ocr-preprocessed/${running.documentKey.hashCode()}").absolutePath,
                            settings = settings,
                        ),
                    )
                    pipeline.updateProgress(running, 85, result.preprocessedImagePath)
                    pipeline.complete(running, result, settings)
                }.onFailure { error ->
                    val diagnostics = when (error) {
                        is OcrEngineException -> error.diagnostics
                        else -> OcrEngineDiagnostics(
                            code = "ocr-worker-failure",
                            message = error.message ?: "OCR failed.",
                            retryable = false,
                        )
                    }
                    pipeline.fail(running, diagnostics)
                    shouldRetry = shouldRetry || diagnostics.retryable
                }
            }
            if (shouldRetry) Result.retry() else Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        private const val KEY_DOCUMENT_KEY = "document_key"

        fun enqueue(workManager: WorkManager, documentKey: String) {
            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(KEY_DOCUMENT_KEY to documentKey))
                .build()
            workManager.enqueueUniqueWork("ocr-$documentKey", ExistingWorkPolicy.REPLACE, request)
        }
    }
}