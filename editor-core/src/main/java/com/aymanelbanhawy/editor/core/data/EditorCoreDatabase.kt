package com.aymanelbanhawy.editor.core.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase

private const val DATABASE_NAME = "enterprise-editor.db"

fun createEditorCoreDatabase(context: Context): PdfWorkspaceDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        PdfWorkspaceDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
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

val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE share_links ADD COLUMN remoteVersion INTEGER")
        database.execSQL("ALTER TABLE share_links ADD COLUMN serverUpdatedAtEpochMillis INTEGER")
        database.execSQL("ALTER TABLE share_links ADD COLUMN lastSyncedAtEpochMillis INTEGER")

        database.execSQL("ALTER TABLE review_threads ADD COLUMN remoteVersion INTEGER")
        database.execSQL("ALTER TABLE review_threads ADD COLUMN serverUpdatedAtEpochMillis INTEGER")
        database.execSQL("ALTER TABLE review_threads ADD COLUMN lastSyncedAtEpochMillis INTEGER")

        database.execSQL("ALTER TABLE version_snapshots ADD COLUMN remoteVersion INTEGER")
        database.execSQL("ALTER TABLE version_snapshots ADD COLUMN serverUpdatedAtEpochMillis INTEGER")
        database.execSQL("ALTER TABLE version_snapshots ADD COLUMN lastSyncedAtEpochMillis INTEGER")

        database.execSQL("ALTER TABLE activity_events ADD COLUMN remoteVersion INTEGER")
        database.execSQL("ALTER TABLE activity_events ADD COLUMN serverUpdatedAtEpochMillis INTEGER")
        database.execSQL("ALTER TABLE activity_events ADD COLUMN lastSyncedAtEpochMillis INTEGER")

        database.execSQL("ALTER TABLE sync_queue ADD COLUMN maxAttempts INTEGER NOT NULL DEFAULT 5")
        database.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE sync_queue SET nextAttemptAtEpochMillis = createdAtEpochMillis WHERE nextAttemptAtEpochMillis = 0")
        database.execSQL("ALTER TABLE sync_queue ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        database.execSQL("UPDATE sync_queue SET idempotencyKey = id WHERE idempotencyKey = ''")
        database.execSQL("ALTER TABLE sync_queue ADD COLUMN lastHttpStatus INTEGER")
        database.execSQL("ALTER TABLE sync_queue ADD COLUMN conflictPayloadJson TEXT")
        database.execSQL("ALTER TABLE sync_queue ADD COLUMN tombstone INTEGER NOT NULL DEFAULT 0")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_idempotencyKey ON sync_queue(idempotencyKey)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_state_nextAttemptAtEpochMillis ON sync_queue(state, nextAttemptAtEpochMillis)")
    }
}
