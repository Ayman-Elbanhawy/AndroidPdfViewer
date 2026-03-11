package com.aymanelbanhawy.editor.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_jobs",
    indices = [
        Index(value = ["documentKey"]),
        Index(value = ["status", "updatedAtEpochMillis"]),
    ],
)
data class OcrJobEntity(
    @PrimaryKey
    val id: String,
    val documentKey: String,
    val pageIndex: Int,
    val imagePath: String,
    val status: String,
    val resultText: String? = null,
    val resultBlocksJson: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
