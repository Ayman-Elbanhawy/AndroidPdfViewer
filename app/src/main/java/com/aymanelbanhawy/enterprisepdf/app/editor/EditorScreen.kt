package com.aymanelbanhawy.enterprisepdf.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.forms.FormsSidebar
import com.aymanelbanhawy.enterprisepdf.app.forms.SignatureCaptureDialog
import com.github.barteksc.pdfviewer.bridge.PdfSessionViewport
import com.github.barteksc.pdfviewer.bridge.PdfViewportCallbacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    events: Flow<EditorSessionEvent>,
    onActionSelected: (EditorAction) -> Unit,
    onDocumentLoaded: (Int) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onToolSelected: (AnnotationTool) -> Unit,
    onAnnotationCreated: (AnnotationModel) -> Unit,
    onAnnotationUpdated: (AnnotationModel, AnnotationModel) -> Unit,
    onAnnotationSelectionChanged: (Int, Set<String>) -> Unit,
    onFormFieldTapped: (String) -> Unit,
    onPageEditSelectionChanged: (Int, String?) -> Unit,
    onPageEditUpdated: (com.aymanelbanhawy.editor.core.model.PageEditModel, com.aymanelbanhawy.editor.core.model.PageEditModel) -> Unit,
    onSidebarToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onRecolorSelected: (String) -> Unit,
    onSelectAnnotation: (String) -> Unit,
    onSelectFormField: (String) -> Unit,
    onTextFieldChanged: (String, String) -> Unit,
    onBooleanFieldChanged: (String, Boolean) -> Unit,
    onChoiceFieldChanged: (String, String) -> Unit,
    onSaveFormProfile: (String) -> Unit,
    onApplyFormProfile: (String) -> Unit,
    onExportFormData: () -> Unit,
    onImportProfile: () -> Unit,
    onOpenSignatureCapture: (String) -> Unit,
    onApplySavedSignature: (String, String) -> Unit,
    onDismissSignatureCapture: () -> Unit,
    onSaveSignatureCapture: (String, SignatureKind, com.aymanelbanhawy.editor.core.forms.SignatureCapture) -> Unit,
    onAddTextBox: () -> Unit,
    onAddImage: () -> Unit,
    onSelectEditObject: (String) -> Unit,
    onDeleteSelectedEdit: () -> Unit,
    onDuplicateSelectedEdit: () -> Unit,
    onReplaceSelectedImage: () -> Unit,
    onSelectedEditTextChanged: (String) -> Unit,
    onSelectedEditFontFamilyChanged: (FontFamilyToken) -> Unit,
    onSelectedEditFontSizeChanged: (Float) -> Unit,
    onSelectedEditColorChanged: (String) -> Unit,
    onSelectedEditAlignmentChanged: (TextAlignment) -> Unit,
    onSelectedEditLineSpacingChanged: (Float) -> Unit,
    onSelectedEditOpacityChanged: (Float) -> Unit,
    onSelectedEditRotationChanged: (Float) -> Unit,
    onRotatePage: () -> Unit,
    onReorderPages: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveEditable: () -> Unit,
    onSaveFlattened: () -> Unit,
    onSaveAsEditable: () -> Unit,
    onSaveAsFlattened: () -> Unit,
    onShareRequested: (EditorSessionEvent.ShareDocument) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowExpanded by remember { mutableStateOf(false) }
    val session = state.session
    val document = session.document

    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                is EditorSessionEvent.ShareDocument -> onShareRequested(event)
                is EditorSessionEvent.UserMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (state.signatureCaptureVisible) {
        SignatureCaptureDialog(onDismiss = onDismissSignatureCapture, onSave = onSaveSignatureCapture)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document?.documentRef?.displayName ?: "Enterprise PDF Editor")
                        Text(
                            when {
                                document == null -> "No document"
                                session.isLoading -> "Loading document"
                                else -> "Page ${session.selection.selectedPageIndex + 1} / ${document.pageCount}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    IconButton(enabled = session.undoRedoState.canUndo, onClick = onUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo") }
                    IconButton(enabled = session.undoRedoState.canRedo, onClick = onRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo") }
                    IconButton(onClick = onSaveEditable) { Icon(Icons.Outlined.FileOpen, contentDescription = "Save editable") }
                    IconButton(onClick = { onActionSelected(EditorAction.Share) }) { Icon(Icons.Outlined.IosShare, contentDescription = "Share") }
                    IconButton(onClick = { overflowExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(text = { Text("Save Flattened") }, onClick = { overflowExpanded = false; onSaveFlattened() })
                        DropdownMenuItem(text = { Text("Save Editable Copy") }, onClick = { overflowExpanded = false; onSaveAsEditable() })
                        DropdownMenuItem(text = { Text("Save Flattened Copy") }, onClick = { overflowExpanded = false; onSaveAsFlattened() })
                        DropdownMenuItem(text = { Text("Import Form Profile") }, onClick = { overflowExpanded = false; onImportProfile() })
                        DropdownMenuItem(text = { Text(if (state.annotationSidebarVisible) "Hide Sidebar" else "Show Sidebar") }, onClick = { overflowExpanded = false; onSidebarToggle() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorAction.entries.forEach { action ->
                        FilterChip(
                            selected = when (action) {
                                EditorAction.Annotate -> state.activePanel == WorkspacePanel.Annotate
                                EditorAction.Forms -> state.activePanel == WorkspacePanel.Forms
                                EditorAction.Sign -> state.activePanel == WorkspacePanel.Sign
                                else -> false
                            },
                            onClick = { onActionSelected(action) },
                            label = { Text(action.name) },
                        )
                    }
                }
                if (state.activePanel == WorkspacePanel.Annotate) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnnotationTool.entries.forEach { tool ->
                            FilterChip(selected = state.activeTool == tool, onClick = { onToolSelected(tool) }, label = { Text(tool.label()) })
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("#F9AB00", "#0B57D0", "#B3261E", "#137333", "#5E35B1").forEach { colorHex ->
                            ColorChip(colorHex = colorHex, onClick = { onRecolorSelected(colorHex) })
                        }
                        AssistChip(onClick = onAddTextBox, label = { Text("Add Text") })
                        AssistChip(onClick = onAddImage, label = { Text("Add Image") })
                        AssistChip(onClick = onDuplicateSelected, label = { Text("Dup Ann") })
                        AssistChip(onClick = onDeleteSelected, label = { Text("Del Ann") })
                        AssistChip(onClick = onRotatePage, label = { Text("Rotate") })
                        AssistChip(onClick = onReorderPages, label = { Text("Reorder") })
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    PdfSessionViewport(
                        modifier = Modifier.fillMaxSize(),
                        document = document,
                        selection = session.selection,
                        activeTool = state.activeTool,
                        currentPage = session.selection.selectedPageIndex,
                        callbacks = PdfViewportCallbacks(
                            onDocumentLoaded = onDocumentLoaded,
                            onPageChanged = onPageChanged,
                            onAnnotationCreated = onAnnotationCreated,
                            onAnnotationUpdated = onAnnotationUpdated,
                            onAnnotationSelectionChanged = onAnnotationSelectionChanged,
                            onFormFieldTapped = onFormFieldTapped,
                            onPageEditSelected = onPageEditSelectionChanged,
                            onPageEditUpdated = onPageEditUpdated,
                        ),
                    )
                }
            }
            when {
                state.activePanel == WorkspacePanel.Annotate && state.selectedEditObject != null && state.annotationSidebarVisible -> {
                    EditInspectorSidebar(
                        modifier = Modifier.width(360.dp).fillMaxHeight().padding(12.dp),
                        editObjects = state.currentPageEditObjects,
                        selectedEditObject = state.selectedEditObject,
                        onSelectEdit = onSelectEditObject,
                        onAddTextBox = onAddTextBox,
                        onAddImage = onAddImage,
                        onDeleteSelected = onDeleteSelectedEdit,
                        onDuplicateSelected = onDuplicateSelectedEdit,
                        onReplaceSelectedImage = onReplaceSelectedImage,
                        onTextChanged = onSelectedEditTextChanged,
                        onFontFamilyChanged = onSelectedEditFontFamilyChanged,
                        onFontSizeChanged = onSelectedEditFontSizeChanged,
                        onTextColorChanged = onSelectedEditColorChanged,
                        onTextAlignmentChanged = onSelectedEditAlignmentChanged,
                        onLineSpacingChanged = onSelectedEditLineSpacingChanged,
                        onOpacityChanged = onSelectedEditOpacityChanged,
                        onRotationChanged = onSelectedEditRotationChanged,
                    )
                }
                state.activePanel == WorkspacePanel.Annotate && state.annotationSidebarVisible -> {
                    AnnotationSidebar(
                        modifier = Modifier.width(320.dp).fillMaxHeight().padding(12.dp),
                        annotations = state.currentPageAnnotations,
                        selectedAnnotationId = state.selectedAnnotation?.id,
                        onSelectAnnotation = onSelectAnnotation,
                    )
                }
                state.annotationSidebarVisible -> {
                    FormsSidebar(
                        modifier = Modifier.width(360.dp).fillMaxHeight().padding(12.dp),
                        activeSignMode = state.activePanel == WorkspacePanel.Sign,
                        fields = state.currentPageFormFields,
                        selectedField = state.selectedFormField,
                        validationMessage = session.formValidationSummary.issueFor(state.selectedFormField?.name.orEmpty())?.message,
                        profiles = state.formProfiles,
                        signatures = state.savedSignatures,
                        onSelectField = onSelectFormField,
                        onTextChanged = onTextFieldChanged,
                        onBooleanChanged = onBooleanFieldChanged,
                        onChoiceChanged = onChoiceFieldChanged,
                        onSaveProfile = onSaveFormProfile,
                        onApplyProfile = onApplyFormProfile,
                        onExportFormData = onExportFormData,
                        onOpenSignatureCapture = onOpenSignatureCapture,
                        onApplySignature = onApplySavedSignature,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationSidebar(
    modifier: Modifier,
    annotations: List<AnnotationModel>,
    selectedAnnotationId: String?,
    onSelectAnnotation: (String) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null)
                Text("Annotations", style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(annotations, key = { it.id }) { annotation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (annotation.id == selectedAnnotationId) 4.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onSelectAnnotation(annotation.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(annotation.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(annotation.commentThread.author, style = MaterialTheme.typography.bodyMedium)
                            Text(annotation.commentThread.status.name, style = MaterialTheme.typography.bodySmall)
                            if (annotation.text.isNotBlank()) {
                                Text(annotation.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (annotation.commentThread.replies.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                    annotation.commentThread.replies.take(2).forEach { reply ->
                                        Text("${reply.author}: ${reply.message}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChip(colorHex: String, onClick: () -> Unit) {
    val color = Color(android.graphics.Color.parseColor(colorHex))
    TextButton(onClick = onClick) {
        Box(modifier = Modifier.clip(CircleShape).background(color).width(28.dp).padding(vertical = 14.dp))
    }
}

private fun AnnotationTool.label(): String = when (this) {
    AnnotationTool.Select -> "Select"
    AnnotationTool.Highlight -> "Highlight"
    AnnotationTool.Underline -> "Underline"
    AnnotationTool.Strikeout -> "Strikeout"
    AnnotationTool.FreehandInk -> "Ink"
    AnnotationTool.Rectangle -> "Rectangle"
    AnnotationTool.Ellipse -> "Ellipse"
    AnnotationTool.Arrow -> "Arrow"
    AnnotationTool.Line -> "Line"
    AnnotationTool.StickyNote -> "Note"
    AnnotationTool.TextBox -> "Text"
}
