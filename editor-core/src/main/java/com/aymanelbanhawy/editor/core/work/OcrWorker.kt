package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.ocr.NoOpOcrEngine
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
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
        val database = Room.databaseBuilder(applicationContext, PdfWorkspaceDatabase::class.java, DB_NAME).fallbackToDestructiveMigration().build()
        return try {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
            val store = RoomSearchIndexStore(database.searchIndexDao(), database.recentSearchDao(), json)
            val searchService = DefaultDocumentSearchService(store, PdfBoxTextExtractionService())
            val engine = NoOpOcrEngine()
            database.ocrJobDao().pending(limit = 12)
                .filter { it.documentKey == documentKey }
                .forEach { job ->
                    database.ocrJobDao().upsert(job.copy(status = OcrJobStatus.Running.name, updatedAtEpochMillis = System.currentTimeMillis()))
                    runCatching { engine.recognize(job.imagePath, job.pageIndex) }
                        .onSuccess { result ->
                            searchService.attachOcrResult(job.documentKey, job.pageIndex, result.pageText, result.blocks)
                            database.ocrJobDao().upsert(
                                job.copy(
                                    status = OcrJobStatus.Completed.name,
                                    resultText = result.pageText,
                                    errorMessage = result.warningMessage,
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                        .onFailure { error ->
                            database.ocrJobDao().upsert(
                                job.copy(
                                    status = OcrJobStatus.Failed.name,
                                    errorMessage = error.message ?: "OCR failed",
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        private const val DB_NAME = "enterprise-editor.db"
        private const val KEY_DOCUMENT_KEY = "document_key"

        fun enqueue(workManager: WorkManager, documentKey: String) {
            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(KEY_DOCUMENT_KEY to documentKey))
                .build()
            workManager.enqueueUniqueWork("ocr-$documentKey", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
