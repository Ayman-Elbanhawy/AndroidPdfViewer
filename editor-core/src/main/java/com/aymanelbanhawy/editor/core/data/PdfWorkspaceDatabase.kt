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
        RecentSearchEntity::class,
        SearchIndexEntity::class,
        OcrJobEntity::class,
        ShareLinkEntity::class,
        ReviewThreadEntity::class,
        ReviewCommentEntity::class,
        VersionSnapshotEntity::class,
        ActivityEventEntity::class,
        SyncQueueEntity::class,
        AppLockSettingsEntity::class,
        DocumentSecurityEntity::class,
        AuditTrailEventEntity::class,
        EnterpriseSettingsEntity::class,
        TelemetryEventEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class PdfWorkspaceDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun draftDao(): DraftDao
    abstract fun editHistoryMetadataDao(): EditHistoryMetadataDao
    abstract fun formProfileDao(): FormProfileDao
    abstract fun savedSignatureDao(): SavedSignatureDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun ocrJobDao(): OcrJobDao
    abstract fun shareLinkDao(): ShareLinkDao
    abstract fun reviewThreadDao(): ReviewThreadDao
    abstract fun reviewCommentDao(): ReviewCommentDao
    abstract fun versionSnapshotDao(): VersionSnapshotDao
    abstract fun activityEventDao(): ActivityEventDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun appLockSettingsDao(): AppLockSettingsDao
    abstract fun documentSecurityDao(): DocumentSecurityDao
    abstract fun auditTrailEventDao(): AuditTrailEventDao
    abstract fun enterpriseSettingsDao(): EnterpriseSettingsDao
    abstract fun telemetryEventDao(): TelemetryEventDao
}
