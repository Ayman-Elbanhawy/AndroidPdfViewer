package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enterprise_settings")
data class EnterpriseSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
