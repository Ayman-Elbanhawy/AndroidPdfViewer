package com.aymanelbanhawy.editor.core

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.collaboration.CollaborationConflictResolver
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRepository
import com.aymanelbanhawy.editor.core.collaboration.DefaultCollaborationRepository
import com.aymanelbanhawy.editor.core.collaboration.InMemoryCollaborationRemoteDataSource
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.enterprise.DefaultEnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.forms.DefaultFormSupportRepository
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.organize.DefaultPageThumbnailRepository
import com.aymanelbanhawy.editor.core.organize.PageThumbnailRepository
import com.aymanelbanhawy.editor.core.repository.DefaultDocumentRepository
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.DefaultScanImportService
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.DefaultDocumentSearchService
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.RoomSearchIndexStore
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.DefaultSecurityRepository
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import com.aymanelbanhawy.editor.core.session.DefaultEditorSession
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.work.CleanupExportsWorker
import com.aymanelbanhawy.editor.core.work.SearchIndexScheduler
import com.aymanelbanhawy.editor.core.work.WorkManagerAutosaveScheduler
import kotlinx.serialization.json.Json

class EditorCoreContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    private val database: PdfWorkspaceDatabase = Room.databaseBuilder(
        appContext,
        PdfWorkspaceDatabase::class.java,
        DATABASE_NAME,
    ).fallbackToDestructiveMigration().build()
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
    val enterpriseAdminRepository: EnterpriseAdminRepository = DefaultEnterpriseAdminRepository(
        context = appContext,
        settingsDao = database.enterpriseSettingsDao(),
        telemetryDao = database.telemetryEventDao(),
        json = json,
    )
    val securityRepository: SecurityRepository = DefaultSecurityRepository(
        context = appContext,
        appLockSettingsDao = database.appLockSettingsDao(),
        documentSecurityDao = database.documentSecurityDao(),
        auditTrailEventDao = database.auditTrailEventDao(),
        json = json,
    )
    val documentRepository: DocumentRepository = DefaultDocumentRepository(
        context = appContext,
        recentDocumentDao = database.recentDocumentDao(),
        draftDao = database.draftDao(),
        editHistoryMetadataDao = database.editHistoryMetadataDao(),
        documentSecurityDao = database.documentSecurityDao(),
        secureFileCipher = AndroidSecureFileCipher(appContext),
        json = json,
    )
    val pageThumbnailRepository: PageThumbnailRepository = DefaultPageThumbnailRepository(appContext)
    val formSupportRepository: FormSupportRepository = DefaultFormSupportRepository(
        context = appContext,
        profileDao = database.formProfileDao(),
        savedSignatureDao = database.savedSignatureDao(),
    )
    private val searchIndexStore = RoomSearchIndexStore(
        searchIndexDao = database.searchIndexDao(),
        recentSearchDao = database.recentSearchDao(),
        json = json,
    )
    private val extractionService = PdfBoxTextExtractionService()
    val documentSearchService: DocumentSearchService = DefaultDocumentSearchService(searchIndexStore, extractionService)
    val searchIndexScheduler: SearchIndexScheduler = SearchIndexScheduler(workManager)
    private val ocrJobPipeline = OcrJobPipeline(
        ocrJobDao = database.ocrJobDao(),
        searchService = documentSearchService,
        workManager = workManager,
        json = json,
    )
    val scanImportService: ScanImportService = DefaultScanImportService(appContext, ocrJobPipeline)
    val collaborationRepository: CollaborationRepository = DefaultCollaborationRepository(
        context = appContext,
        shareLinkDao = database.shareLinkDao(),
        reviewThreadDao = database.reviewThreadDao(),
        reviewCommentDao = database.reviewCommentDao(),
        versionSnapshotDao = database.versionSnapshotDao(),
        activityEventDao = database.activityEventDao(),
        syncQueueDao = database.syncQueueDao(),
        remoteDataSource = InMemoryCollaborationRemoteDataSource(),
        conflictResolver = CollaborationConflictResolver(),
        json = json,
    )
    private val autosaveScheduler = WorkManagerAutosaveScheduler(documentRepository, workManager)

    init {
        CleanupExportsWorker.enqueue(workManager)
    }

    fun newSession(): EditorSession = DefaultEditorSession(documentRepository, autosaveScheduler)

    companion object {
        const val DATABASE_NAME: String = "enterprise-editor.db"
    }
}

