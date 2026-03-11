package com.aymanelbanhawy.editor.core.ocr

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.work.OcrWorker
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class OcrJobPipeline(
    private val ocrJobDao: OcrJobDao,
    private val searchService: DocumentSearchService,
    private val workManager: WorkManager,
    private val json: Json,
) {
    suspend fun enqueue(documentKey: String, jobs: List<QueuedOcrPage>) {
        val now = System.currentTimeMillis()
        ocrJobDao.upsertAll(
            jobs.map { job ->
                OcrJobEntity(
                    id = UUID.randomUUID().toString(),
                    documentKey = documentKey,
                    pageIndex = job.pageIndex,
                    imagePath = job.imagePath,
                    status = OcrJobStatus.Queued.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            },
        )
        OcrWorker.enqueue(workManager, documentKey)
    }

    suspend fun complete(job: OcrJobEntity, result: OcrEngineResult) {
        val now = System.currentTimeMillis()
        ocrJobDao.upsert(
            job.copy(
                status = OcrJobStatus.Completed.name,
                resultText = result.pageText,
                resultBlocksJson = json.encodeToString(ListSerializer(com.aymanelbanhawy.editor.core.search.ExtractedTextBlock.serializer()), result.blocks),
                errorMessage = result.warningMessage,
                updatedAtEpochMillis = now,
            ),
        )
        searchService.attachOcrResult(job.documentKey, job.pageIndex, result.pageText, result.blocks)
    }

    suspend fun fail(job: OcrJobEntity, message: String) {
        ocrJobDao.upsert(job.copy(status = OcrJobStatus.Failed.name, errorMessage = message, updatedAtEpochMillis = System.currentTimeMillis()))
    }
}

data class QueuedOcrPage(
    val pageIndex: Int,
    val imagePath: String,
)
