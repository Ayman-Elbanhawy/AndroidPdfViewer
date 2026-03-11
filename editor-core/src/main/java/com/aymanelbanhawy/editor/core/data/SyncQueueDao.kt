package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE documentKey = :documentKey ORDER BY createdAtEpochMillis ASC")
    suspend fun forDocument(documentKey: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE state IN ('Pending', 'Failed', 'Conflict') ORDER BY updatedAtEpochMillis ASC")
    suspend fun pending(): List<SyncQueueEntity>
}
