package com.aymanelbanhawy.enterprisepdf.app

import android.content.Context
import com.aymanelbanhawy.aiassistant.core.AiAssistantRepository
import com.aymanelbanhawy.aiassistant.core.DefaultAiAssistantRepository
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRepository
import com.aymanelbanhawy.editor.core.connectors.ConnectorRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.organize.PageThumbnailRepository
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.work.SearchIndexScheduler
import com.aymanelbanhawy.editor.core.workflow.WorkflowRepository
import com.aymanelbanhawy.enterprisepdf.app.migration.AppMigrationCoordinator
import com.aymanelbanhawy.enterprisepdf.app.migration.DefaultAppMigrationCoordinator
import com.aymanelbanhawy.enterprisepdf.app.release.AppRuntimeConfig
import kotlinx.coroutines.runBlocking

class AppContainer(
    private val editorCoreContainer: EditorCoreContainer,
    val runtimeConfig: AppRuntimeConfig,
) {
    val appContext: Context get() = editorCoreContainer.appContext
    val documentRepository: DocumentRepository get() = editorCoreContainer.documentRepository
    val connectorRepository: ConnectorRepository get() = editorCoreContainer.connectorRepository
    val pageThumbnailRepository: PageThumbnailRepository get() = editorCoreContainer.pageThumbnailRepository
    val formSupportRepository: FormSupportRepository get() = editorCoreContainer.formSupportRepository
    val documentSearchService: DocumentSearchService get() = editorCoreContainer.documentSearchService
    val searchIndexScheduler: SearchIndexScheduler get() = editorCoreContainer.searchIndexScheduler
    val scanImportService: ScanImportService get() = editorCoreContainer.scanImportService
    val ocrJobPipeline: OcrJobPipeline get() = editorCoreContainer.ocrJobPipeline
    val collaborationRepository: CollaborationRepository get() = editorCoreContainer.collaborationRepository
    val workflowRepository: WorkflowRepository get() = editorCoreContainer.workflowRepository
    val securityRepository: SecurityRepository get() = editorCoreContainer.securityRepository
    val enterpriseAdminRepository: EnterpriseAdminRepository get() = editorCoreContainer.enterpriseAdminRepository
    val runtimeDiagnosticsRepository: RuntimeDiagnosticsRepository get() = editorCoreContainer.runtimeDiagnosticsRepository
    val migrationCoordinator: AppMigrationCoordinator by lazy {
        DefaultAppMigrationCoordinator(appContext, editorCoreContainer)
    }
    val aiAssistantRepository: AiAssistantRepository by lazy {
        runBlocking {
            DefaultAiAssistantRepository.create(
                context = appContext,
                documentSearchService = editorCoreContainer.documentSearchService,
                documentRepository = editorCoreContainer.documentRepository,
                recentDocumentDao = editorCoreContainer.recentDocumentDao(),
                enterpriseAdminRepository = editorCoreContainer.enterpriseAdminRepository,
                securityRepository = editorCoreContainer.securityRepository,
            )
        }
    }

    fun createSession(): EditorSession = editorCoreContainer.newSession()

    fun seedDocumentRequest(): OpenDocumentRequest {
        return OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf")
    }
}

