package com.aymanelbanhawy.editor.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TelemetryEventEntity)

    @Query("SELECT * FROM telemetry_events ORDER BY created_at DESC")
    suspend fun all(): List<TelemetryEventEntity>
}
