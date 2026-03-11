package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["documentKey"]), Index(value = ["state", "updatedAtEpochMillis"])],
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val type: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val state: String,
    val attemptCount: Int,
    val lastError: String?,
)
