package com.aymanelbanhawy.editor.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecentDocumentEntity::class,
        DraftEntity::class,
        EditHistoryMetadataEntity::class,
        FormProfileEntity::class,
        SavedSignatureEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class PdfWorkspaceDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun draftDao(): DraftDao
    abstract fun editHistoryMetadataDao(): EditHistoryMetadataDao
    abstract fun formProfileDao(): FormProfileDao
    abstract fun savedSignatureDao(): SavedSignatureDao
}
