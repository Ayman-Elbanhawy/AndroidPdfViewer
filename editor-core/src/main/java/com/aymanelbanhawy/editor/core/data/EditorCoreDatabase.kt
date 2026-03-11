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
        .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
        .build()
}

val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN progressPercent INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN maxAttempts INTEGER NOT NULL DEFAULT 2")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN resultPageJson TEXT")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN diagnosticsJson TEXT")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN settingsJson TEXT")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN preprocessedImagePath TEXT")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN startedAtEpochMillis INTEGER")
        database.execSQL("ALTER TABLE ocr_jobs ADD COLUMN completedAtEpochMillis INTEGER")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS ocr_settings (id TEXT NOT NULL, payloadJson TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))",
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

val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE saved_signatures ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'Handwritten'")
        database.execSQL("ALTER TABLE saved_signatures ADD COLUMN signingIdentityId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE saved_signatures ADD COLUMN signerDisplayName TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE saved_signatures ADD COLUMN certificateSubject TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE saved_signatures ADD COLUMN certificateSha256 TEXT NOT NULL DEFAULT ''")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS signing_identities (id TEXT NOT NULL, displayName TEXT NOT NULL, subjectCommonName TEXT NOT NULL, issuerCommonName TEXT NOT NULL, serialNumberHex TEXT NOT NULL, certificateSha256 TEXT NOT NULL, validFromEpochMillis INTEGER NOT NULL, validToEpochMillis INTEGER NOT NULL, encryptedPkcs12Path TEXT NOT NULL, encryptedPasswordPath TEXT NOT NULL, certificateAlias TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))",
        )
    }
}

val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE enterprise_settings ADD COLUMN policy_version TEXT NOT NULL DEFAULT 'local'")
        database.execSQL("ALTER TABLE enterprise_settings ADD COLUMN policy_etag TEXT")
        database.execSQL("ALTER TABLE enterprise_settings ADD COLUMN last_sync_at INTEGER")
        database.execSQL("ALTER TABLE enterprise_settings ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 2")
        database.execSQL("ALTER TABLE telemetry_events ADD COLUMN upload_state TEXT NOT NULL DEFAULT 'Pending'")
        database.execSQL("ALTER TABLE telemetry_events ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE telemetry_events ADD COLUMN last_attempt_at INTEGER")
        database.execSQL("ALTER TABLE telemetry_events ADD COLUMN uploaded_at INTEGER")
        database.execSQL("ALTER TABLE telemetry_events ADD COLUMN failure_message TEXT")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_telemetry_events_upload_state_created_at ON telemetry_events(upload_state, created_at)")
    }
}

val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS connector_accounts (id TEXT NOT NULL, connector_type TEXT NOT NULL, display_name TEXT NOT NULL, base_url TEXT NOT NULL, credential_type TEXT NOT NULL, username TEXT NOT NULL, secret_alias TEXT, supports_open INTEGER NOT NULL, supports_save INTEGER NOT NULL, supports_share INTEGER NOT NULL, supports_import INTEGER NOT NULL, supports_metadata_sync INTEGER NOT NULL, supports_resumable_transfer INTEGER NOT NULL, is_enterprise_managed INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS remote_document_metadata (document_key TEXT NOT NULL, connector_account_id TEXT NOT NULL, remote_path TEXT NOT NULL, display_name TEXT NOT NULL, version_id TEXT, modified_at INTEGER, etag TEXT, checksum_sha256 TEXT, size_bytes INTEGER, mime_type TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(document_key))",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS connector_transfer_jobs (id TEXT NOT NULL, connector_account_id TEXT NOT NULL, document_key TEXT NOT NULL, remote_path TEXT NOT NULL, local_cache_path TEXT NOT NULL, direction TEXT NOT NULL, status TEXT NOT NULL, bytes_transferred INTEGER NOT NULL, total_bytes INTEGER NOT NULL, resumable_token TEXT, attempt_count INTEGER NOT NULL, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_connector_transfer_jobs_status_created_at ON connector_transfer_jobs(status, created_at)")
    }
}
