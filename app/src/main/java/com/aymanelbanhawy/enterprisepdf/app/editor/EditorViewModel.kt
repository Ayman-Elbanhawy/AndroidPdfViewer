package com.aymanelbanhawy.enterprisepdf.app.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventModel
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventType
import com.aymanelbanhawy.editor.core.collaboration.ReviewFilterModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadState
import com.aymanelbanhawy.editor.core.collaboration.ShareLinkModel
import com.aymanelbanhawy.editor.core.collaboration.SharePermission
import com.aymanelbanhawy.editor.core.collaboration.VersionSnapshotModel
import com.aymanelbanhawy.editor.core.command.AddAnnotationCommand
import com.aymanelbanhawy.editor.core.command.AddPageEditCommand
import com.aymanelbanhawy.editor.core.command.BatchRotatePagesCommand
import com.aymanelbanhawy.editor.core.command.DeleteAnnotationCommand
import com.aymanelbanhawy.editor.core.command.DeletePageEditCommand
import com.aymanelbanhawy.editor.core.command.DeletePagesCommand
import com.aymanelbanhawy.editor.core.command.DuplicateAnnotationCommand
import com.aymanelbanhawy.editor.core.command.DuplicatePagesCommand
import com.aymanelbanhawy.editor.core.command.ExtractPagesCommand
import com.aymanelbanhawy.editor.core.command.InsertBlankPageCommand
import com.aymanelbanhawy.editor.core.command.InsertImagePageCommand
import com.aymanelbanhawy.editor.core.command.MergePagesCommand
import com.aymanelbanhawy.editor.core.command.ReorderPagesCommand
import com.aymanelbanhawy.editor.core.command.ReplaceSecurityDocumentCommand
import com.aymanelbanhawy.editor.core.command.ReplaceFormDocumentCommand
import com.aymanelbanhawy.editor.core.command.ReplaceImageAssetCommand
import com.aymanelbanhawy.editor.core.command.RotatePageCommand
import com.aymanelbanhawy.editor.core.command.UpdateAnnotationCommand
import com.aymanelbanhawy.editor.core.command.UpdateFormFieldCommand
import com.aymanelbanhawy.editor.core.command.UpdatePageEditCommand
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryCategory
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.enterprise.newTelemetryEvent
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.FormProfileModel
import com.aymanelbanhawy.editor.core.forms.SavedSignatureModel
import com.aymanelbanhawy.editor.core.forms.SignatureCapture
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.EditorSessionState
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.aymanelbanhawy.editor.core.model.duplicated
import com.aymanelbanhawy.editor.core.ocr.OcrJobSummary
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.organize.SplitMode
import com.aymanelbanhawy.editor.core.organize.SplitRequest
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.search.IndexingPolicy
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.aymanelbanhawy.editor.core.security.AppLockReason
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AppLockStateModel
import com.aymanelbanhawy.editor.core.security.AuditEventType
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.DocumentPermissionModel
import com.aymanelbanhawy.editor.core.security.MetadataScrubOptionsModel
import com.aymanelbanhawy.editor.core.security.RedactionMarkModel
import com.aymanelbanhawy.editor.core.security.RedactionStatus
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.TenantPolicyHooksModel
import com.aymanelbanhawy.editor.core.security.WatermarkModel
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class WorkspacePanel {
    Annotate,
    Forms,
    Sign,
    Search,
    Assistant,
    Review,
    Activity,
    Protect,
    Settings,
}

data class EditorUiState(
    val session: EditorSessionState = EditorSessionState(),
    val activeTool: AnnotationTool = AnnotationTool.Select,
    val activePanel: WorkspacePanel = WorkspacePanel.Annotate,
    val annotationSidebarVisible: Boolean = true,
    val organizeVisible: Boolean = false,
    val selectedPageIndexes: Set<Int> = emptySet(),
    val thumbnails: List<ThumbnailDescriptor> = emptyList(),
    val splitRangeExpression: String = "1-2",
    val formProfiles: List<FormProfileModel> = emptyList(),
    val savedSignatures: List<SavedSignatureModel> = emptyList(),
    val signatureCaptureVisible: Boolean = false,
    val signingFieldName: String? = null,
    val searchQuery: String = "",
    val searchResults: SearchResultSet = SearchResultSet(),
    val assistantState: AssistantUiState = AssistantUiState(),
    val recentSearches: List<String> = emptyList(),
    val outlineItems: List<OutlineItem> = emptyList(),
    val selectedTextSelection: TextSelectionPayload? = null,
    val isSearchIndexing: Boolean = false,
    val scanImportVisible: Boolean = false,
    val scanImportOptions: ScanImportOptions = ScanImportOptions(),
    val ocrJobs: List<OcrJobSummary> = emptyList(),
    val ocrSettings: OcrSettingsModel = OcrSettingsModel(),
    val shareLinks: List<ShareLinkModel> = emptyList(),
    val reviewThreads: List<ReviewThreadModel> = emptyList(),
    val versionSnapshots: List<VersionSnapshotModel> = emptyList(),
    val activityEvents: List<ActivityEventModel> = emptyList(),
    val reviewFilter: ReviewFilterModel = ReviewFilterModel(),
    val pendingSyncCount: Int = 0,
    val appLockSettings: AppLockSettingsModel = AppLockSettingsModel(),
    val appLockState: AppLockStateModel = AppLockStateModel(),
    val securityAuditEvents: List<AuditTrailEventModel> = emptyList(),
    val enterpriseState: EnterpriseAdminStateModel = EnterpriseAdminStateModel(),
    val entitlements: EntitlementStateModel = EntitlementStateModel(LicensePlan.Free, emptySet()),
    val telemetryEvents: List<TelemetryEventModel> = emptyList(),
    val diagnosticsBundleCount: Int = 0,
) {
    val selectedAnnotation: AnnotationModel?
        get() = session.document?.pages?.flatMap { it.annotations }?.firstOrNull { it.id in session.selection.selectedAnnotationIds }

    val currentPageAnnotations: List<AnnotationModel>
        get() = session.document?.pages?.getOrNull(session.selection.selectedPageIndex)?.annotations.orEmpty()

    val selectedFormField: FormFieldModel?
        get() = session.document?.formDocument?.field(session.selection.selectedFormFieldName.orEmpty())

    val currentPageFormFields: List<FormFieldModel>
        get() = session.document?.formDocument?.fields?.filter { it.pageIndex == session.selection.selectedPageIndex }.orEmpty()

    val selectedEditObject: PageEditModel?
        get() = session.document?.pages?.flatMap { it.editObjects }?.firstOrNull { it.id == session.selection.selectedEditId }

    val currentPageEditObjects: List<PageEditModel>
        get() = session.document?.pages?.getOrNull(session.selection.selectedPageIndex)?.editObjects.orEmpty()

    val selectedSearchHit
        get() = searchResults.selectedHit
}

class EditorViewModel(
    private val session: EditorSession,
    private val repository: DocumentRepository,
    private val appContainer: AppContainer,
) : ViewModel() {
    private val activeTool = MutableStateFlow(AnnotationTool.Select)
    private val activePanel = MutableStateFlow(WorkspacePanel.Annotate)
    private val sidebarVisible = MutableStateFlow(true)
    private val organizeVisible = MutableStateFlow(false)
    private val selectedPageIndexes = MutableStateFlow(emptySet<Int>())
    private val thumbnails = MutableStateFlow(emptyList<ThumbnailDescriptor>())
    private val splitRangeExpression = MutableStateFlow("1-2")
    private val formProfiles = MutableStateFlow(emptyList<FormProfileModel>())
    private val savedSignatures = MutableStateFlow(emptyList<SavedSignatureModel>())
    private val signatureCaptureVisible = MutableStateFlow(false)
    private val signingFieldName = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val searchResults = MutableStateFlow(SearchResultSet())
    private val recentSearches = MutableStateFlow(emptyList<String>())
    private val outlineItems = MutableStateFlow(emptyList<OutlineItem>())
    private val selectedTextSelection = MutableStateFlow<TextSelectionPayload?>(null)
    private val isSearchIndexing = MutableStateFlow(false)
    private val scanImportVisible = MutableStateFlow(false)
    private val scanImportOptions = MutableStateFlow(ScanImportOptions())
    private val ocrJobs = MutableStateFlow(emptyList<OcrJobSummary>())
    private val ocrSettings = MutableStateFlow(OcrSettingsModel())
    private val shareLinks = MutableStateFlow(emptyList<ShareLinkModel>())
    private val reviewThreads = MutableStateFlow(emptyList<ReviewThreadModel>())
    private val versionSnapshots = MutableStateFlow(emptyList<VersionSnapshotModel>())
    private val activityEvents = MutableStateFlow(emptyList<ActivityEventModel>())
    private val reviewFilter = MutableStateFlow(ReviewFilterModel())
    private val pendingSyncCount = MutableStateFlow(0)
    private val appLockSettings = MutableStateFlow(AppLockSettingsModel())
    private val appLockState = MutableStateFlow(AppLockStateModel())
    private val securityAuditEvents = MutableStateFlow(emptyList<AuditTrailEventModel>())
    private val enterpriseState = MutableStateFlow(EnterpriseAdminStateModel())
    private val entitlements = MutableStateFlow(EntitlementStateModel(LicensePlan.Free, emptySet()))
    private val telemetryEvents = MutableStateFlow(emptyList<TelemetryEventModel>())
    private val diagnosticsBundleCount = MutableStateFlow(0)
    private val assistantState = MutableStateFlow(AssistantUiState())
    private val localEvents = MutableSharedFlow<EditorSessionEvent>(extraBufferCapacity = 16)
    private val indexingPolicy = IndexingPolicy()
    private var ocrObservationJob: Job? = null

    val uiState: StateFlow<EditorUiState> = combine(
        session.state,
        activeTool,
        activePanel,
        sidebarVisible,
        organizeVisible,
        selectedPageIndexes,
        thumbnails,
        splitRangeExpression,
        formProfiles,
        savedSignatures,
        signatureCaptureVisible,
        signingFieldName,
        searchQuery,
        searchResults,
        assistantState,
        recentSearches,
        outlineItems,
        selectedTextSelection,
        isSearchIndexing,
        scanImportVisible,
        scanImportOptions,
        shareLinks,
        reviewThreads,
        versionSnapshots,
        activityEvents,
        reviewFilter,
        pendingSyncCount,
        appLockSettings,
        appLockState,
        securityAuditEvents,
        enterpriseState,
        entitlements,
        telemetryEvents,
        diagnosticsBundleCount,
    ) { values ->
        EditorUiState(
            session = values[0] as EditorSessionState,
            activeTool = values[1] as AnnotationTool,
            activePanel = values[2] as WorkspacePanel,
            annotationSidebarVisible = values[3] as Boolean,
            organizeVisible = values[4] as Boolean,
            selectedPageIndexes = values[5] as Set<Int>,
            thumbnails = values[6] as List<ThumbnailDescriptor>,
            splitRangeExpression = values[7] as String,
            formProfiles = values[8] as List<FormProfileModel>,
            savedSignatures = values[9] as List<SavedSignatureModel>,
            signatureCaptureVisible = values[10] as Boolean,
            signingFieldName = values[11] as String?,
            searchQuery = values[12] as String,
            searchResults = values[13] as SearchResultSet,
            assistantState = values[14] as AssistantUiState,
            recentSearches = values[15] as List<String>,
            outlineItems = values[16] as List<OutlineItem>,
            selectedTextSelection = values[17] as TextSelectionPayload?,
            isSearchIndexing = values[18] as Boolean,
            scanImportVisible = values[19] as Boolean,
            scanImportOptions = values[20] as ScanImportOptions,
            shareLinks = values[21] as List<ShareLinkModel>,
            reviewThreads = values[22] as List<ReviewThreadModel>,
            versionSnapshots = values[23] as List<VersionSnapshotModel>,
            activityEvents = values[24] as List<ActivityEventModel>,
            reviewFilter = values[25] as ReviewFilterModel,
            pendingSyncCount = values[26] as Int,
            appLockSettings = values[27] as AppLockSettingsModel,
            appLockState = values[28] as AppLockStateModel,
            securityAuditEvents = values[29] as List<AuditTrailEventModel>,
            enterpriseState = values[30] as EnterpriseAdminStateModel,
            entitlements = values[31] as EntitlementStateModel,
            telemetryEvents = values[32] as List<TelemetryEventModel>,
            diagnosticsBundleCount = values[33] as Int,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    val events: Flow<EditorSessionEvent> = merge(session.events, localEvents)

    init {
        viewModelScope.launch {
            session.openDocument(appContainer.seedDocumentRequest())
            refreshThumbnails()
            refreshFormSupportData()
            refreshSearchSupportData(forceSync = true)
            refreshCollaborationData()
            refreshSecurityData()
            refreshEnterpriseData()
            recordActivity(ActivityEventType.Opened, "Opened ${session.state.value.document?.documentRef?.displayName.orEmpty()}")
            recordSecurityAudit(AuditEventType.DocumentOpened, "Opened document")
            queueTelemetry("document_opened", mapOf("mode" to enterpriseState.value.authSession.mode.name))
        }
    }

    fun onDocumentLoaded(pageCount: Int) = session.onDocumentLoaded(pageCount)
    fun onPageChanged(page: Int, pageCount: Int) = session.onPageChanged(page, pageCount)

    fun onActionSelected(action: EditorAction) {
        when (action) {
            EditorAction.Organize -> {
                organizeVisible.value = true
                refreshThumbnailsAsync()
            }
            EditorAction.Forms -> {
                activePanel.value = WorkspacePanel.Forms
                sidebarVisible.value = true
                viewModelScope.launch { refreshFormSupportData() }
                session.onActionSelected(action)
            }
            EditorAction.Sign -> {
                activePanel.value = WorkspacePanel.Sign
                sidebarVisible.value = true
                viewModelScope.launch { refreshFormSupportData() }
                session.onActionSelected(action)
            }
            EditorAction.Annotate -> {
                activePanel.value = WorkspacePanel.Annotate
                sidebarVisible.value = true
                session.onActionSelected(action)
            }
            EditorAction.Search -> {
                activePanel.value = WorkspacePanel.Search
                sidebarVisible.value = true
                viewModelScope.launch { refreshSearchSupportData(forceSync = false) }
            }
            EditorAction.Assistant -> {
                activePanel.value = WorkspacePanel.Assistant
                sidebarVisible.value = true
                viewModelScope.launch { refreshAssistantData() }
                session.onActionSelected(action)
            }
            EditorAction.Review -> {
                activePanel.value = WorkspacePanel.Review
                sidebarVisible.value = true
                viewModelScope.launch { refreshCollaborationData() }
            }
            EditorAction.Activity -> {
                activePanel.value = WorkspacePanel.Activity
                sidebarVisible.value = true
                viewModelScope.launch { refreshCollaborationData() }
            }
            EditorAction.Protect -> {
                activePanel.value = WorkspacePanel.Protect
                sidebarVisible.value = true
                viewModelScope.launch { refreshSecurityData() }
                session.onActionSelected(action)
            }
            EditorAction.Settings -> {
                activePanel.value = WorkspacePanel.Settings
                sidebarVisible.value = true
                viewModelScope.launch { refreshEnterpriseData() }
                session.onActionSelected(action)
            }
            EditorAction.Share -> {
                val document = session.state.value.document ?: return
                val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Share)
                if (decision.allowed) {
                    session.onActionSelected(action)
                } else {
                    localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Share blocked"))
                    viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Share blocked", mapOf("action" to RestrictedAction.Share.name)) }
                }
            }
            else -> session.onActionSelected(action)
        }
    }

    fun showEditor() { organizeVisible.value = false }
    fun showOrganize() { organizeVisible.value = true; refreshThumbnailsAsync() }
    fun onToolSelected(tool: AnnotationTool) { activeTool.value = tool; activePanel.value = WorkspacePanel.Annotate }
    fun toggleAnnotationSidebar() { sidebarVisible.value = !sidebarVisible.value }
    fun updateSearchQuery(value: String) { searchQuery.value = value }
    fun updateAssistantPrompt(value: String) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updatePrompt(value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun updateAssistantProviderDraft(draft: AiProviderDraft) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updateProviderDraft(draft)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun saveAssistantProvider() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.saveProviderDraft(entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun refreshAssistantProviders() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.refreshProviderCatalog(entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun testAssistantConnection() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.testProviderConnection(entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun cancelAssistantRequest() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.cancelActiveRequest()
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun askPdf() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.askPdf(document, assistantState.value.prompt.ifBlank { "What should I know about this PDF?" }, selectedTextSelection.value, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun summarizeDocumentWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.summarizeDocument(document, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun summarizeCurrentPageWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.summarizePage(document, session.state.value.selection.selectedPageIndex, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun extractActionItemsWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.extractActionItems(document, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun explainSelectionWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.explainSelection(document, selectedTextSelection.value, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun runAiSemanticSearch() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.semanticSearch(document, assistantState.value.prompt.ifBlank { searchQuery.value }, entitlements.value, enterpriseState.value)
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun updateAssistantPrivacyMode(mode: AssistantPrivacyMode) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updateSettings(assistantState.value.settings.copy(privacyMode = mode))
            assistantState.value = appContainer.aiAssistantRepository.state.value
        }
    }

    fun openAssistantCitation(pageIndex: Int) {
        viewModelScope.launch {
            session.updateSelection(
                session.state.value.selection.copy(
                    selectedPageIndex = pageIndex,
                    selectedAnnotationIds = emptySet(),
                    selectedFormFieldName = null,
                    selectedEditId = null,
                ),
            )
        }
    }
    fun showScanImportDialog() { scanImportVisible.value = true }
    fun dismissScanImportDialog() { scanImportVisible.value = false }
    fun updateScanImportOptions(options: ScanImportOptions) { scanImportOptions.value = options }
    fun updateReviewFilter(filter: ReviewFilterModel) { reviewFilter.value = filter; refreshCollaborationAsync() }

    fun createShareLink() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.collaborationRepository.createShareLink(document, document.documentRef.displayName, SharePermission.Comment, System.currentTimeMillis() + 604_800_000L)
            refreshCollaborationData()
            localEvents.emit(EditorSessionEvent.UserMessage("Created share link"))
        }
    }

    fun addReviewThread(title: String, message: String) {
        val document = session.state.value.document ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            appContainer.collaborationRepository.addReviewThread(
                document = document,
                title = title,
                message = message,
                pageIndex = session.state.value.selection.selectedPageIndex,
                anchorBounds = selectedTextSelection.value?.blocks?.firstOrNull()?.bounds,
            )
            refreshCollaborationData()
        }
    }

    fun addReviewReply(threadId: String, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            appContainer.collaborationRepository.addReviewReply(threadId, "Ayman", message)
            refreshCollaborationData()
        }
    }

    fun toggleThreadResolved(threadId: String, resolved: Boolean) {
        viewModelScope.launch {
            appContainer.collaborationRepository.setThreadResolved(threadId, resolved)
            refreshCollaborationData()
        }
    }

    fun createVersionSnapshot() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.collaborationRepository.createVersionSnapshot(document, "Snapshot ${System.currentTimeMillis()}")
            refreshCollaborationData()
            localEvents.emit(EditorSessionEvent.UserMessage("Created local snapshot"))
        }
    }

    fun syncCollaboration() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val summary = appContainer.collaborationRepository.processSync(document.documentRef.sourceKey)
            refreshCollaborationData()
            localEvents.emit(EditorSessionEvent.UserMessage("Sync processed ${summary.processedCount} operation(s)"))
        }
    }

    fun performSearch(queryOverride: String? = null) {
        val document = session.state.value.document ?: return
        val query = queryOverride ?: searchQuery.value
        searchQuery.value = query
        viewModelScope.launch {
            isSearchIndexing.value = true
            val results = appContainer.documentSearchService.search(document, query)
            searchResults.value = results
            recentSearches.value = appContainer.documentSearchService.recentSearches(document.documentRef.sourceKey)
            isSearchIndexing.value = false
            results.selectedHit?.let { focusSearchHit(it.pageIndex, it.bounds, it.matchText, results.selectedHitIndex) }
        }
    }

    fun selectSearchHit(index: Int) {
        val current = searchResults.value
        val hit = current.hits.getOrNull(index) ?: return
        searchResults.value = current.copy(selectedHitIndex = index)
        focusSearchHit(hit.pageIndex, hit.bounds, hit.matchText, index)
    }

    fun nextSearchHit() {
        val current = searchResults.value
        if (current.hits.isEmpty()) return
        val next = if (current.selectedHitIndex < 0) 0 else (current.selectedHitIndex + 1) % current.hits.size
        selectSearchHit(next)
    }

    fun previousSearchHit() {
        val current = searchResults.value
        if (current.hits.isEmpty()) return
        val previous = if (current.selectedHitIndex <= 0) current.hits.lastIndex else current.selectedHitIndex - 1
        selectSearchHit(previous)
    }

    fun openOutlineItem(pageIndex: Int) {
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun copySelectedText() {
        val selection = selectedTextSelection.value ?: return
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Copy)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Copy blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Copy blocked", mapOf("action" to RestrictedAction.Copy.name)) }
            return
        }
        val clipboard = appContainer.appContext.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Selected text", selection.text))
        localEvents.tryEmit(EditorSessionEvent.UserMessage("Copied selected text"))
    }

    fun shareSelectedText() {
        val selection = selectedTextSelection.value ?: return
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Share)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Share blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Share blocked", mapOf("action" to RestrictedAction.Share.name)) }
            return
        }
        localEvents.tryEmit(EditorSessionEvent.ShareText("Selected text", selection.text))
    }

    fun importScanImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val cachedImages = uris.mapIndexedNotNull { index, uri -> copyUriToCache(uri, "scan-import-$index", ".png") }
            if (cachedImages.isEmpty()) return@launch
            val request = appContainer.scanImportService.importImages(cachedImages, scanImportOptions.value)
            scanImportVisible.value = false
            session.openDocument(request)
            refreshThumbnails()
            refreshFormSupportData()
            refreshSearchSupportData(forceSync = true)
            refreshCollaborationData()
            recordActivity(ActivityEventType.Opened, "Opened scanned import ${session.state.value.document?.documentRef?.displayName.orEmpty()}")
            localEvents.emit(EditorSessionEvent.UserMessage("Imported ${cachedImages.size} scan image(s)"))
        }
    }

    fun onAnnotationCreated(annotation: AnnotationModel) {
        session.execute(AddAnnotationCommand(annotation.pageIndex, annotation))
        activeTool.value = AnnotationTool.Select
    }

    fun onAnnotationUpdated(before: AnnotationModel, after: AnnotationModel) {
        session.execute(UpdateAnnotationCommand(before.pageIndex, before, after))
    }

    fun onAnnotationSelectionChanged(pageIndex: Int, annotationIds: Set<String>) {
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = annotationIds,
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun onPageEditSelectionChanged(pageIndex: Int, editId: String?) {
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = null,
                selectedEditId = editId,
            ),
        )
        if (editId != null) {
            activePanel.value = WorkspacePanel.Annotate
            activeTool.value = AnnotationTool.Select
        }
    }

    fun onPageEditUpdated(before: PageEditModel, after: PageEditModel) {
        session.execute(UpdatePageEditCommand(before, after))
    }

    fun addTextBox() {
        val pageIndex = session.state.value.selection.selectedPageIndex
        val edit = TextBoxEditModel(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            bounds = NormalizedRect(0.14f, 0.16f, 0.54f, 0.28f),
            text = "Edit text",
        )
        session.execute(AddPageEditCommand(pageIndex, edit))
        activePanel.value = WorkspacePanel.Annotate
    }

    fun addImageEdit(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "page-edit-image", ".png") ?: return@launch
            val pageIndex = session.state.value.selection.selectedPageIndex
            val edit = ImageEditModel(
                id = UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                bounds = NormalizedRect(0.18f, 0.22f, 0.56f, 0.48f),
                imagePath = copied.absolutePath,
                label = copied.name,
            )
            session.execute(AddPageEditCommand(pageIndex, edit))
            activePanel.value = WorkspacePanel.Annotate
        }
    }

    fun replaceSelectedImage(uri: Uri) {
        val selected = uiState.value.selectedEditObject as? ImageEditModel ?: return
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "page-edit-image-replace", ".png") ?: return@launch
            session.execute(ReplaceImageAssetCommand(selected, selected.replaced(copied.absolutePath, copied.name)))
        }
    }

    fun selectPageEdit(editId: String) {
        val document = session.state.value.document ?: return
        val page = document.pages.firstOrNull { page -> page.editObjects.any { it.id == editId } } ?: return
        onPageEditSelectionChanged(page.index, editId)
    }

    fun deleteSelectedEdit() {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(DeletePageEditCommand(selected.pageIndex, selected))
    }

    fun duplicateSelectedEdit() {
        val selected = uiState.value.selectedEditObject ?: return
        val duplicated = selected.duplicated(UUID.randomUUID().toString())
        session.execute(AddPageEditCommand(selected.pageIndex, duplicated))
    }

    fun updateSelectedTextContent(text: String) {
        val selected = uiState.value.selectedEditObject as? TextBoxEditModel ?: return
        session.execute(UpdatePageEditCommand(selected, selected.withText(text)))
    }

    fun updateSelectedTextStyle(
        fontFamily: FontFamilyToken = (uiState.value.selectedEditObject as? TextBoxEditModel)?.fontFamily ?: FontFamilyToken.Sans,
        fontSizeSp: Float = (uiState.value.selectedEditObject as? TextBoxEditModel)?.fontSizeSp ?: 16f,
        textColorHex: String = (uiState.value.selectedEditObject as? TextBoxEditModel)?.textColorHex ?: "#202124",
        alignment: TextAlignment = (uiState.value.selectedEditObject as? TextBoxEditModel)?.alignment ?: TextAlignment.Start,
        opacity: Float = uiState.value.selectedEditObject?.opacity ?: 1f,
        lineSpacingMultiplier: Float = (uiState.value.selectedEditObject as? TextBoxEditModel)?.lineSpacingMultiplier ?: 1.2f,
    ) {
        val selected = uiState.value.selectedEditObject as? TextBoxEditModel ?: return
        val updated = selected.withTypography(fontFamily, fontSizeSp, textColorHex, alignment, lineSpacingMultiplier).withOpacity(opacity) as TextBoxEditModel
        session.execute(UpdatePageEditCommand(selected, updated))
    }

    fun updateSelectedEditRotation(rotationDegrees: Float) {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(UpdatePageEditCommand(selected, selected.rotatedTo(rotationDegrees)))
    }

    fun updateSelectedEditOpacity(opacity: Float) {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(UpdatePageEditCommand(selected, selected.withOpacity(opacity)))
    }

    fun onFormFieldTapped(fieldName: String) {
        selectFormField(fieldName)
    }

    fun selectAnnotation(annotationId: String) {
        val document = session.state.value.document ?: return
        val page = document.pages.firstOrNull { page -> page.annotations.any { it.id == annotationId } } ?: return
        activePanel.value = WorkspacePanel.Annotate
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = page.index,
                selectedAnnotationIds = setOf(annotationId),
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun recolorSelectedAnnotation(colorHex: String) {
        val selected = uiState.value.selectedAnnotation ?: return
        session.execute(UpdateAnnotationCommand(selected.pageIndex, selected, selected.recolored(colorHex, selected.fillColorHex)))
    }

    fun deleteSelectedAnnotation() {
        uiState.value.selectedAnnotation?.let { session.execute(DeleteAnnotationCommand(it.pageIndex, it)) }
    }

    fun duplicateSelectedAnnotation() {
        uiState.value.selectedAnnotation?.let { selected ->
            session.execute(DuplicateAnnotationCommand(selected.pageIndex, selected, selected.duplicated(UUID.randomUUID().toString())))
        }
    }

    fun selectFormField(fieldName: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        activePanel.value = if (field.type.name == "Signature") WorkspacePanel.Sign else WorkspacePanel.Forms
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = field.pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = field.name,
                selectedEditId = null,
            ),
        )
    }

    fun updateTextField(fieldName: String, value: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        val updated = field.copy(value = FormFieldValue.Text(value), signatureStatus = field.signatureStatus)
        session.execute(UpdateFormFieldCommand(field, updated))
    }

    fun toggleBooleanField(fieldName: String, checked: Boolean) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        session.execute(UpdateFormFieldCommand(field, field.copy(value = FormFieldValue.BooleanValue(checked))))
    }

    fun updateChoiceField(fieldName: String, choice: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        session.execute(UpdateFormFieldCommand(field, field.copy(value = FormFieldValue.Choice(choice))))
    }

    fun saveFormProfile(name: String) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val profile = appContainer.formSupportRepository.saveProfile(name, document.formDocument)
            refreshFormSupportData()
            localEvents.emit(EditorSessionEvent.UserMessage("Saved form profile ${profile.name}"))
        }
    }

    fun applyFormProfile(profileId: String) {
        val document = session.state.value.document ?: return
        val profile = formProfiles.value.firstOrNull { it.id == profileId } ?: return
        val updated = document.formDocument.copy(
            fields = document.formDocument.fields.map { field ->
                profile.values[field.name]?.let { value ->
                    field.copy(
                        value = value,
                        signatureStatus = if (value is FormFieldValue.SignatureValue) value.status else field.signatureStatus,
                    )
                } ?: field
            },
        )
        session.execute(ReplaceFormDocumentCommand(document.formDocument, updated))
        localEvents.tryEmit(EditorSessionEvent.UserMessage("Applied profile ${profile.name}"))
    }

    fun importFormProfile(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "form-profile", ".json") ?: return@launch
            val imported = appContainer.formSupportRepository.importProfile(copied)
            refreshFormSupportData()
            applyFormProfile(imported.id)
        }
    }

    fun exportCurrentFormData() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val exportName = document.documentRef.displayName.removeSuffix(".pdf") + "-form-profile.json"
            val destination = File(appContainer.appContext.cacheDir, "form-exports/$exportName")
            val profile = appContainer.formSupportRepository.saveProfile("Export ${document.documentRef.displayName}", document.formDocument)
            appContainer.formSupportRepository.exportProfile(profile, destination)
            refreshFormSupportData()
            localEvents.emit(EditorSessionEvent.UserMessage("Exported form data to ${destination.name}"))
        }
    }

    fun openSignatureCapture(fieldName: String? = uiState.value.selectedFormField?.name) {
        val targetField = fieldName ?: return
        signingFieldName.value = targetField
        signatureCaptureVisible.value = true
        activePanel.value = WorkspacePanel.Sign
    }

    fun dismissSignatureCapture() {
        signatureCaptureVisible.value = false
        signingFieldName.value = null
    }

    fun saveSignatureAndApply(name: String, kind: SignatureKind, capture: SignatureCapture) {
        val fieldName = signingFieldName.value ?: return
        viewModelScope.launch {
            val savedSignature = appContainer.formSupportRepository.saveSignature(name, kind, capture)
            refreshFormSupportData()
            applySavedSignature(fieldName, savedSignature.id)
            dismissSignatureCapture()
            localEvents.emit(EditorSessionEvent.UserMessage("Saved ${kind.name.lowercase()} for $name"))
        }
    }

    fun applySavedSignature(fieldName: String, signatureId: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        val savedSignature = savedSignatures.value.firstOrNull { it.id == signatureId } ?: return
        val signatureValue = FormFieldValue.SignatureValue(
            savedSignatureId = savedSignature.id,
            signerName = savedSignature.name,
            signedAtEpochMillis = System.currentTimeMillis(),
            status = SignatureVerificationStatus.Signed,
            imagePath = savedSignature.imagePath,
            kind = savedSignature.kind,
        )
        session.execute(
            UpdateFormFieldCommand(
                field,
                field.copy(
                    value = signatureValue,
                    signatureStatus = SignatureVerificationStatus.Signed,
                ),
            ),
        )
        viewModelScope.launch { recordActivity(ActivityEventType.Signed, "Signed field ${field.name}") }
        selectFormField(fieldName)
    }

    fun selectPage(index: Int) { selectedPageIndexes.value = selectedPageIndexes.value.toggle(index) }
    fun clearPageSelection() { selectedPageIndexes.value = emptySet() }
    fun movePage(fromIndex: Int, toIndex: Int) { session.execute(ReorderPagesCommand(fromIndex, toIndex)); refreshThumbnailsAsync() }
    fun rotateSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(BatchRotatePagesCommand(it, 90)); refreshThumbnailsAsync() } }
    fun deleteSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(DeletePagesCommand(it)); selectedPageIndexes.value = emptySet(); refreshThumbnailsAsync() } }
    fun duplicateSelectedPages() { effectivePageSelection().toList().sorted().takeIf { it.isNotEmpty() }?.let { session.execute(DuplicatePagesCommand(it)); refreshThumbnailsAsync() } }
    fun extractSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(ExtractPagesCommand(it)); selectedPageIndexes.value = emptySet(); refreshThumbnailsAsync() } }
    fun insertBlankPage() { session.execute(InsertBlankPageCommand(((selectedPageIndexes.value.maxOrNull()?.plus(1)) ?: (session.state.value.selection.selectedPageIndex + 1)))); refreshThumbnailsAsync() }

    fun insertImagePage(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "organize-image", ".png") ?: return@launch
            val bitmap = BitmapFactory.decodeFile(copied.absolutePath) ?: return@launch
            val target = (selectedPageIndexes.value.maxOrNull()?.plus(1)) ?: (session.state.value.selection.selectedPageIndex + 1)
            session.execute(InsertImagePageCommand(target, copied.absolutePath, bitmap.width.toFloat(), bitmap.height.toFloat()))
            refreshThumbnails()
        }
    }

    fun mergeDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val requests = uris.mapIndexed { index, uri -> OpenDocumentRequest.FromUri(uri.toString(), uri.lastPathSegment ?: "merge_${index + 1}.pdf") }
            val pages = repository.importPages(requests)
            val insertIndex = (selectedPageIndexes.value.maxOrNull()?.plus(1)) ?: (session.state.value.document?.pageCount ?: 0)
            session.execute(MergePagesCommand(insertIndex, pages))
            refreshThumbnails()
        }
    }

    fun updateSplitRangeExpression(value: String) { splitRangeExpression.value = value }
    fun splitByRange() { split(SplitRequest(SplitMode.PageRanges, rangeExpression = uiState.value.splitRangeExpression)) }
    fun splitOddPages() { split(SplitRequest(SplitMode.OddPages)) }
    fun splitEvenPages() { split(SplitRequest(SplitMode.EvenPages)) }
    fun splitSelectedPages() { split(SplitRequest(SplitMode.SelectedPages, selectedPageIndexes = effectivePageSelection())) }

    private fun split(request: SplitRequest) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val outputDir = File(appContainer.appContext.cacheDir, "splits/${document.sessionId}")
            val files = repository.split(document, request, outputDir)
            localEvents.tryEmit(EditorSessionEvent.UserMessage("Created ${files.size} split file(s) in ${outputDir.name}"))
        }
    }

    fun signInPersonal(displayName: String) {
        viewModelScope.launch {
            enterpriseState.value = appContainer.enterpriseAdminRepository.signInPersonal(displayName)
            entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
            queueTelemetry("sign_in_personal", mapOf("user" to enterpriseState.value.authSession.displayName))
        }
    }

    fun signInEnterprise(email: String, tenant: TenantConfigurationModel) {
        viewModelScope.launch {
            enterpriseState.value = appContainer.enterpriseAdminRepository.signInEnterprise(email, tenant)
            entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
            queueTelemetry("sign_in_enterprise", mapOf("tenant" to tenant.tenantName))
        }
    }

    fun signOutEnterprise() {
        viewModelScope.launch {
            enterpriseState.value = appContainer.enterpriseAdminRepository.signOut()
            entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
            queueTelemetry("sign_out")
        }
    }

    fun setEnterprisePlan(plan: LicensePlan) {
        viewModelScope.launch {
            enterpriseState.value = enterpriseState.value.copy(plan = plan)
            appContainer.enterpriseAdminRepository.saveState(enterpriseState.value)
            entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
            queueTelemetry("plan_changed", mapOf("plan" to plan.name))
        }
    }

    fun updateEnterprisePrivacy(settings: PrivacySettingsModel) {
        viewModelScope.launch {
            enterpriseState.value = enterpriseState.value.copy(privacySettings = settings)
            appContainer.enterpriseAdminRepository.saveState(enterpriseState.value)
        }
    }

    fun updateEnterprisePolicy(policy: AdminPolicyModel) {
        viewModelScope.launch {
            enterpriseState.value = enterpriseState.value.copy(adminPolicy = policy)
            appContainer.enterpriseAdminRepository.saveState(enterpriseState.value)
            entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
            queueTelemetry("policy_updated")
        }
    }

    fun generateDiagnosticsBundle() {
        viewModelScope.launch {
            val destination = File(appContainer.appContext.cacheDir, "diagnostics/diagnostics-${System.currentTimeMillis()}.json")
            appContainer.enterpriseAdminRepository.diagnosticsBundle(
                destination,
                mapOf(
                    "documentLoaded" to (session.state.value.document != null).toString(),
                    "pageCount" to (session.state.value.document?.pageCount ?: 0).toString(),
                    "activePanel" to activePanel.value.name,
                ),
            )
            diagnosticsBundleCount.value = diagnosticsBundleCount.value + 1
            queueTelemetry("diagnostics_bundle_generated")
            localEvents.emit(EditorSessionEvent.UserMessage("Generated diagnostics bundle ${destination.name}"))
        }
    }
    fun configureAppLock(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            appLockSettings.value = appContainer.securityRepository.updateAppLockSettings(enabled, pin, biometricsEnabled, timeoutSeconds)
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val success = appContainer.securityRepository.unlockWithPin(pin)
            appLockState.value = appContainer.securityRepository.appLockState.value
            if (!success) {
                localEvents.emit(EditorSessionEvent.UserMessage("PIN did not match"))
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch {
            appContainer.securityRepository.unlockWithBiometric()
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            appContainer.securityRepository.lockApp(AppLockReason.Manual)
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun updateDocumentPermissions(permissions: DocumentPermissionModel) {
        replaceSecurityDocument { copy(permissions = permissions) }
    }

    fun updateTenantPolicy(policy: TenantPolicyHooksModel) {
        replaceSecurityDocument { copy(tenantPolicy = policy) }
    }

    fun updatePasswordProtection(enabled: Boolean, userPassword: String, ownerPassword: String) {
        replaceSecurityDocument { copy(passwordProtection = passwordProtection.copy(enabled = enabled, userPassword = userPassword, ownerPassword = ownerPassword)) }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.PasswordProtectionUpdated, "Updated password protection") }
    }

    fun updateWatermark(enabled: Boolean, text: String) {
        replaceSecurityDocument { copy(watermark = watermark.copy(enabled = enabled, text = text)) }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.WatermarkUpdated, "Updated watermark") }
    }

    fun updateMetadataScrub(options: MetadataScrubOptionsModel) {
        replaceSecurityDocument { copy(metadataScrub = options) }
    }

    fun generateInspectionReport() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val report = appContainer.securityRepository.inspectDocument(document)
            replaceSecurityDocument { copy(inspectionReport = report) }
            refreshSecurityData()
        }
    }

    fun markSelectedTextForRedaction() {
        val document = session.state.value.document ?: return
        val selection = selectedTextSelection.value ?: return
        val marks = selection.blocks.mapIndexed { index, block ->
            RedactionMarkModel(
                id = UUID.randomUUID().toString(),
                pageIndex = block.pageIndex,
                bounds = block.bounds,
                label = "Selection ${index + 1}",
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
        if (marks.isEmpty()) return
        replaceSecurityDocument {
            copy(
                redactionWorkflow = redactionWorkflow.copy(
                    marks = redactionWorkflow.marks + marks,
                    previewEnabled = true,
                ),
            )
        }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.RedactionMarked, "Marked ${marks.size} redaction region(s)") }
    }

    fun setRedactionPreview(enabled: Boolean) {
        replaceSecurityDocument { copy(redactionWorkflow = redactionWorkflow.copy(previewEnabled = enabled)) }
    }

    fun removeRedactionMark(markId: String) {
        replaceSecurityDocument { copy(redactionWorkflow = redactionWorkflow.copy(marks = redactionWorkflow.marks.filterNot { it.id == markId })) }
    }

    fun applyRedactions() {
        replaceSecurityDocument {
            copy(
                redactionWorkflow = redactionWorkflow.copy(
                    previewEnabled = false,
                    irreversibleConfirmed = true,
                    marks = redactionWorkflow.marks.map { it.copy(status = RedactionStatus.Applied, appliedAtEpochMillis = System.currentTimeMillis()) },
                ),
            )
        }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.RedactionApplied, "Applied redactions") }
    }

    fun exportAuditTrail() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val destination = File(appContainer.appContext.cacheDir, "audit/${document.documentRef.displayName.removeSuffix(".pdf")}-audit.json")
            appContainer.securityRepository.exportAuditTrail(document.documentRef.sourceKey, destination)
            refreshSecurityData()
            localEvents.emit(EditorSessionEvent.UserMessage("Exported audit trail to ${destination.name}"))
        }
    }
    fun rotateCurrentPage() { session.execute(RotatePageCommand(session.state.value.selection.selectedPageIndex, 90)); refreshThumbnailsAsync() }
    fun reorderFirstPageToEnd() { session.state.value.document?.takeIf { it.pages.size >= 2 }?.let { session.execute(ReorderPagesCommand(0, it.pages.lastIndex)); refreshThumbnailsAsync() } }
    fun undo() { session.undo(); refreshThumbnailsAsync() }
    fun redo() { session.redo(); refreshThumbnailsAsync() }
    fun saveEditable() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        session.manualSave(AnnotationExportMode.Editable)
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved editable PDF")
        }
    }

    fun saveFlattened() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        session.manualSave(AnnotationExportMode.Flatten)
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved flattened PDF")
        }
    }

    fun saveAsEditable() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        File(document.documentRef.workingCopyPath).parentFile?.resolve("editable_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Editable) }
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved editable copy")
        }
    }

    fun saveAsFlattened() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        File(document.documentRef.workingCopyPath).parentFile?.resolve("flattened_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Flatten) }
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved flattened copy")
        }
    }

    private suspend fun refreshFormSupportData() {
        formProfiles.value = appContainer.formSupportRepository.loadProfiles()
        savedSignatures.value = appContainer.formSupportRepository.loadSignatures()
    }

    private suspend fun refreshSearchSupportData(forceSync: Boolean) {
        val document = session.state.value.document ?: return
        isSearchIndexing.value = true
        recentSearches.value = appContainer.documentSearchService.recentSearches(document.documentRef.sourceKey)
        outlineItems.value = appContainer.documentSearchService.outline(document.documentRef)
        if (forceSync || !indexingPolicy.shouldIndexInBackground(document)) {
            appContainer.documentSearchService.ensureIndex(document)
        } else {
            appContainer.searchIndexScheduler.scheduleIfNeeded(document)
        }
        isSearchIndexing.value = false
    }

    private suspend fun refreshAssistantData() {
        appContainer.aiAssistantRepository.refresh(
            document = session.state.value.document,
            selection = selectedTextSelection.value,
            entitlements = entitlements.value,
            enterpriseState = enterpriseState.value,
        )
        appContainer.aiAssistantRepository.refreshProviderCatalog(entitlements.value, enterpriseState.value)
        assistantState.value = appContainer.aiAssistantRepository.state.value
    }
    private suspend fun refreshEnterpriseData() {
        enterpriseState.value = appContainer.enterpriseAdminRepository.loadState()
        entitlements.value = appContainer.enterpriseAdminRepository.resolveEntitlements(enterpriseState.value)
        telemetryEvents.value = appContainer.enterpriseAdminRepository.pendingTelemetry()
    }

    private suspend fun queueTelemetry(name: String, properties: Map<String, String> = emptyMap()) {
        appContainer.enterpriseAdminRepository.queueTelemetry(
            newTelemetryEvent(TelemetryCategory.Product, name, properties),
        )
        telemetryEvents.value = appContainer.enterpriseAdminRepository.pendingTelemetry()
    }
    private suspend fun refreshSecurityData() {
        val document = session.state.value.document ?: return
        appLockSettings.value = appContainer.securityRepository.loadAppLockSettings()
        appLockState.value = appContainer.securityRepository.appLockState.value
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(document.documentRef.sourceKey)
        if (document.security != appContainer.securityRepository.loadDocumentSecurity(document.documentRef.sourceKey)) {
            persistCurrentSecurity()
        }
    }

    private fun replaceSecurityDocument(transform: com.aymanelbanhawy.editor.core.security.SecurityDocumentModel.() -> com.aymanelbanhawy.editor.core.security.SecurityDocumentModel) {
        val current = session.state.value.document ?: return
        val before = current.security
        val after = before.transform()
        session.execute(ReplaceSecurityDocumentCommand(before, after))
        viewModelScope.launch { persistCurrentSecurity() }
    }

    private suspend fun persistCurrentSecurity() {
        val document = session.state.value.document ?: return
        appContainer.securityRepository.persistDocumentSecurity(document.documentRef.sourceKey, document.security)
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(document.documentRef.sourceKey)
    }

    private suspend fun recordSecurityAudit(type: AuditEventType, message: String, metadata: Map<String, String> = emptyMap()) {
        val documentKey = session.state.value.document?.documentRef?.sourceKey ?: "__app__"
        appContainer.securityRepository.recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = type,
                actor = "Ayman",
                message = message,
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = metadata,
            ),
        )
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(documentKey)
    }
    private suspend fun refreshCollaborationData() {
        val document = session.state.value.document ?: return
        shareLinks.value = appContainer.collaborationRepository.shareLinks(document.documentRef.sourceKey)
        reviewThreads.value = appContainer.collaborationRepository.reviewThreads(document.documentRef.sourceKey, reviewFilter.value)
        versionSnapshots.value = appContainer.collaborationRepository.versionSnapshots(document.documentRef.sourceKey)
        activityEvents.value = appContainer.collaborationRepository.activityEvents(document.documentRef.sourceKey)
        pendingSyncCount.value = appContainer.collaborationRepository.pendingSyncOperations(document.documentRef.sourceKey).size
    }

    private fun refreshCollaborationAsync() { viewModelScope.launch { refreshCollaborationData() } }

    private suspend fun recordActivity(type: ActivityEventType, summary: String, threadId: String? = null) {
        val document = session.state.value.document ?: return
        appContainer.collaborationRepository.recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = document.documentRef.sourceKey,
                type = type,
                actor = "Ayman",
                summary = summary,
                createdAtEpochMillis = System.currentTimeMillis(),
                threadId = threadId,
            ),
        )
        refreshCollaborationData()
    }

    private fun focusSearchHit(pageIndex: Int, bounds: NormalizedRect, text: String, selectedIndex: Int) {
        viewModelScope.launch {
            val document = session.state.value.document ?: return@launch
            session.updateSelection(
                session.state.value.selection.copy(
                    selectedPageIndex = pageIndex,
                    selectedAnnotationIds = emptySet(),
                    selectedFormFieldName = null,
                    selectedEditId = null,
                ),
            )
            selectedTextSelection.value = appContainer.documentSearchService.selectionForBounds(document, pageIndex, bounds)
                ?: TextSelectionPayload(pageIndex = pageIndex, text = text, blocks = emptyList())
            searchResults.value = searchResults.value.copy(selectedHitIndex = selectedIndex)
        }
    }

    private fun effectivePageSelection(): Set<Int> = selectedPageIndexes.value.ifEmpty { setOf(session.state.value.selection.selectedPageIndex) }
    private fun refreshThumbnailsAsync() { viewModelScope.launch { refreshThumbnails() } }

    private suspend fun refreshThumbnails() {
        session.state.value.document?.let { thumbnails.value = appContainer.pageThumbnailRepository.thumbnailsFor(it) }
    }

    private fun copyUriToCache(uri: Uri, prefix: String, suffix: String): File? {
        val output = File(appContainer.appContext.cacheDir, "$prefix-${UUID.randomUUID()}$suffix")
        appContainer.appContext.contentResolver.openInputStream(uri)?.use { input -> output.outputStream().use { input.copyTo(it) } } ?: return null
        return output
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(appContainer.createSession(), appContainer.documentRepository, appContainer) as T
            }
        }
    }
}

private fun Set<Int>.toggle(index: Int): Set<Int> = if (index in this) this - index else this + index



















