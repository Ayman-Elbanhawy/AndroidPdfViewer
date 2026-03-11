package com.aymanelbanhawy.enterprisepdf.app.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.aymanelbanhawy.editor.core.command.ReplaceFormDocumentCommand
import com.aymanelbanhawy.editor.core.command.ReplaceImageAssetCommand
import com.aymanelbanhawy.editor.core.command.RotatePageCommand
import com.aymanelbanhawy.editor.core.command.UpdateAnnotationCommand
import com.aymanelbanhawy.editor.core.command.UpdateFormFieldCommand
import com.aymanelbanhawy.editor.core.command.UpdatePageEditCommand
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
import com.aymanelbanhawy.editor.core.organize.SplitMode
import com.aymanelbanhawy.editor.core.organize.SplitRequest
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class WorkspacePanel {
    Annotate,
    Forms,
    Sign,
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
    private val localEvents = MutableSharedFlow<EditorSessionEvent>(extraBufferCapacity = 16)

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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    val events: Flow<EditorSessionEvent> = merge(session.events, localEvents)

    init {
        viewModelScope.launch {
            session.openDocument(appContainer.seedDocumentRequest())
            refreshThumbnails()
            refreshFormSupportData()
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
            else -> session.onActionSelected(action)
        }
    }

    fun showEditor() { organizeVisible.value = false }
    fun showOrganize() { organizeVisible.value = true; refreshThumbnailsAsync() }
    fun onToolSelected(tool: AnnotationTool) { activeTool.value = tool; activePanel.value = WorkspacePanel.Annotate }
    fun toggleAnnotationSidebar() { sidebarVisible.value = !sidebarVisible.value }

    fun onAnnotationCreated(annotation: AnnotationModel) {
        session.execute(AddAnnotationCommand(annotation.pageIndex, annotation))
        activeTool.value = AnnotationTool.Select
    }

    fun onAnnotationUpdated(before: AnnotationModel, after: AnnotationModel) {
        session.execute(UpdateAnnotationCommand(before.pageIndex, before, after))
    }

    fun onAnnotationSelectionChanged(pageIndex: Int, annotationIds: Set<String>) {
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

    fun rotateCurrentPage() { session.execute(RotatePageCommand(session.state.value.selection.selectedPageIndex, 90)); refreshThumbnailsAsync() }
    fun reorderFirstPageToEnd() { session.state.value.document?.takeIf { it.pages.size >= 2 }?.let { session.execute(ReorderPagesCommand(0, it.pages.lastIndex)); refreshThumbnailsAsync() } }
    fun undo() { session.undo(); refreshThumbnailsAsync() }
    fun redo() { session.redo(); refreshThumbnailsAsync() }
    fun saveEditable() = session.manualSave(AnnotationExportMode.Editable)
    fun saveFlattened() = session.manualSave(AnnotationExportMode.Flatten)

    fun saveAsEditable() {
        session.state.value.document?.let { document ->
            File(document.documentRef.workingCopyPath).parentFile?.resolve("editable_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Editable) }
        }
    }

    fun saveAsFlattened() {
        session.state.value.document?.let { document ->
            File(document.documentRef.workingCopyPath).parentFile?.resolve("flattened_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Flatten) }
        }
    }

    private suspend fun refreshFormSupportData() {
        formProfiles.value = appContainer.formSupportRepository.loadProfiles()
        savedSignatures.value = appContainer.formSupportRepository.loadSignatures()
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
