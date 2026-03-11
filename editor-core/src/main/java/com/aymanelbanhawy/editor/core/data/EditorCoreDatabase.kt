package com.aymanelbanhawy.editor.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

private const val DATABASE_NAME = "enterprise-editor.db"

fun createEditorCoreDatabase(context: Context): PdfWorkspaceDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        PdfWorkspaceDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(MIGRATION_7_8)
        .build()
}

val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN progressPercent INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN maxAttempts INTEGER NOT NULL DEFAULT 2
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN resultPageJson TEXT
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN diagnosticsJson TEXT
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN settingsJson TEXT
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN preprocessedImagePath TEXT
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN startedAtEpochMillis INTEGER
            """.trimIndent(),
        )
        database.execSQL(
            """
            ALTER TABLE ocr_jobs ADD COLUMN completedAtEpochMillis INTEGER
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ocr_settings (
                id TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }
}