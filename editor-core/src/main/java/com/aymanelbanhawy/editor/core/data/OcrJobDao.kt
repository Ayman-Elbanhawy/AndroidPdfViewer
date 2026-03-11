package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OcrJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OcrJobEntity>)

    @Query("SELECT * FROM ocr_jobs WHERE id = :id LIMIT 1")
    suspend fun job(id: String): OcrJobEntity?

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE documentKey = :documentKey
        ORDER BY pageIndex ASC, createdAtEpochMillis ASC
        """,
    )
    suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity>

    @Query(
        """
        SELECT * FROM ocr_jobs
        WHERE status IN ('Pending', 'Queued')
        ORDER BY updatedAtEpochMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(limit: Int): List<OcrJobEntity>
}
