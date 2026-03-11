package com.aymanelbanhawy.editor.core

import android.content.Context
import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.collaboration.CollaborationConflictResolver
import com.aymanelbanhawy.editor.core.collaboration.CollaborationCredentialStore
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRemoteRegistry
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRepository
import com.aymanelbanhawy.editor.core.collaboration.DefaultCollaborationRepository
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.data.createEditorCoreDatabase
import com.aymanelbanhawy.editor.core.enterprise.DefaultEnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.forms.DefaultFormSupportRepository
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
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
import com.aymanelbanhawy.editor.core.work.WorkManagerCollaborationSyncScheduler
import kotlinx.serialization.json.Json

class EditorCoreContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    private val database: PdfWorkspaceDatabase = createEditorCoreDatabase(appContext)
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
    private val ocrSessionStore = OcrSessionStore(json)
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
    private val searchIndexStore = RoomSearchIndexStore(
        searchIndexDao = database.searchIndexDao(),
        recentSearchDao = database.recentSearchDao(),
        json = json,
    )
    private val extractionService = PdfBoxTextExtractionService()
    val documentSearchService: DocumentSearchService = DefaultDocumentSearchService(searchIndexStore, extractionService, ocrSessionStore)
    val ocrJobPipeline: OcrJobPipeline = OcrJobPipeline(
        ocrJobDao = database.ocrJobDao(),
        ocrSettingsDao = database.ocrSettingsDao(),
        searchService = documentSearchService,
        workManager = workManager,
        json = json,
        ocrSessionStore = ocrSessionStore,
    )
    val documentRepository: DocumentRepository = DefaultDocumentRepository(
        context = appContext,
        recentDocumentDao = database.recentDocumentDao(),
        draftDao = database.draftDao(),
        editHistoryMetadataDao = database.editHistoryMetadataDao(),
        documentSecurityDao = database.documentSecurityDao(),
        secureFileCipher = AndroidSecureFileCipher(appContext),
        ocrSessionStore = ocrSessionStore,
        json = json,
    )
    val pageThumbnailRepository: PageThumbnailRepository = DefaultPageThumbnailRepository(appContext)
    val formSupportRepository: FormSupportRepository = DefaultFormSupportRepository(
        context = appContext,
        profileDao = database.formProfileDao(),
        savedSignatureDao = database.savedSignatureDao(),
    )
    val searchIndexScheduler: SearchIndexScheduler = SearchIndexScheduler(workManager)
    val scanImportService: ScanImportService = DefaultScanImportService(appContext, ocrJobPipeline)
    private val collaborationSyncScheduler = WorkManagerCollaborationSyncScheduler(workManager)
    private val collaborationCredentialStore = CollaborationCredentialStore(appContext, json)
    private val collaborationRemoteRegistry = CollaborationRemoteRegistry(
        context = appContext,
        enterpriseAdminRepository = enterpriseAdminRepository,
        credentialStore = collaborationCredentialStore,
        json = json,
    )
    val collaborationRepository: CollaborationRepository = DefaultCollaborationRepository(
        context = appContext,
        shareLinkDao = database.shareLinkDao(),
        reviewThreadDao = database.reviewThreadDao(),
        reviewCommentDao = database.reviewCommentDao(),
        versionSnapshotDao = database.versionSnapshotDao(),
        activityEventDao = database.activityEventDao(),
        syncQueueDao = database.syncQueueDao(),
        remoteRegistry = collaborationRemoteRegistry,
        conflictResolver = CollaborationConflictResolver(),
        enterpriseAdminRepository = enterpriseAdminRepository,
        syncScheduler = collaborationSyncScheduler,
        json = json,
    )
    private val autosaveScheduler = WorkManagerAutosaveScheduler(documentRepository, workManager)

    init {
        CleanupExportsWorker.enqueue(workManager)
    }

    fun newSession(): EditorSession = DefaultEditorSession(documentRepository, autosaveScheduler)
}
