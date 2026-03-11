package com.aymanelbanhawy.enterprisepdf.app.organize

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OrganizePagesScreen(
    state: EditorUiState,
    events: Flow<EditorSessionEvent>,
    onBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onRotateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onExtractSelected: () -> Unit,
    onInsertBlankPage: () -> Unit,
    onPickImagePage: () -> Unit,
    onPickMergePdfs: () -> Unit,
    onUpdateSplitRange: (String) -> Unit,
    onSplitByRange: () -> Unit,
    onSplitOddPages: () -> Unit,
    onSplitEvenPages: () -> Unit,
    onSplitSelectedPages: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(events) {
        events.collectLatest { event ->
            if (event is EditorSessionEvent.UserMessage) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organize Pages") },
                navigationIcon = {
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, tooltip = "Back", onClick = onBack)
                },
                actions = {
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Undo, tooltip = "Undo", onClick = onUndo)
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Redo, tooltip = "Redo", onClick = onRedo)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.28f),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Batch Actions", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTooltipButton(icon = Icons.Outlined.Rotate90DegreesCw, tooltip = "Rotate Selected", onClick = onRotateSelected)
                        IconTooltipButton(icon = Icons.Outlined.Delete, tooltip = "Delete Selected", onClick = onDeleteSelected)
                        IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Duplicate Selected", onClick = onDuplicateSelected)
                        IconTooltipButton(icon = Icons.Outlined.ContentCut, tooltip = "Extract Selected", onClick = onExtractSelected)
                        IconTooltipButton(icon = Icons.Outlined.Description, tooltip = "Insert Blank Page", onClick = onInsertBlankPage)
                        IconTooltipButton(icon = Icons.Outlined.AddPhotoAlternate, tooltip = "Insert Image Page", onClick = onPickImagePage)
                        IconTooltipButton(icon = Icons.Outlined.Collections, tooltip = "Merge PDFs", onClick = onPickMergePdfs)
                    }
                    OutlinedTextField(
                        value = state.splitRangeExpression,
                        onValueChange = onUpdateSplitRange,
                        label = { Text("Split Range") },
                        supportingText = { Text("1-3,5,7-8") },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTooltipButton(icon = Icons.Outlined.CallSplit, tooltip = "Split by Range", onClick = onSplitByRange)
                        IconTooltipButton(icon = Icons.Outlined.Filter1, tooltip = "Split Odd Pages", onClick = onSplitOddPages)
                        IconTooltipButton(icon = Icons.Outlined.Filter2, tooltip = "Split Even Pages", onClick = onSplitEvenPages)
                        IconTooltipButton(icon = Icons.Outlined.ContentCut, tooltip = "Split Selected Pages", onClick = onSplitSelectedPages)
                    }
                    Text("${state.selectedPageIndexes.size} selected", style = MaterialTheme.typography.bodyMedium)
                    Text("Drag a thumbnail to reorder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.thumbnails, key = { it.pageIndex }) { thumbnail ->
                        val isSelected = thumbnail.pageIndex in state.selectedPageIndexes
                        PageThumbnailCard(
                            thumbnail = thumbnail,
                            label = state.session.document?.pages?.getOrNull(thumbnail.pageIndex)?.label ?: "${thumbnail.pageIndex + 1}",
                            selected = isSelected,
                            modifier = Modifier
                                .animateItem()
                                .onGloballyPositioned { coordinates -> itemBounds[thumbnail.pageIndex] = coordinates.boundsInParent() }
                                .pointerInput(state.thumbnails) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = thumbnail.pageIndex
                                            targetIndex = thumbnail.pageIndex
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val position = change.position
                                            targetIndex = itemBounds.entries.firstOrNull { it.value.contains(position) }?.key ?: targetIndex
                                        },
                                        onDragEnd = {
                                            if (draggedIndex >= 0 && targetIndex >= 0 && draggedIndex != targetIndex) {
                                                onMovePage(draggedIndex, targetIndex)
                                            }
                                            draggedIndex = -1
                                            targetIndex = -1
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            targetIndex = -1
                                        },
                                    )
                                },
                            onClick = { onSelectPage(thumbnail.pageIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    thumbnail: ThumbnailDescriptor,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bitmap = remember(thumbnail.imagePath) { BitmapFactory.decodeFile(thumbnail.imagePath)?.asImageBitmap() }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 6.dp else 1.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = label, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Preview", textAlign = TextAlign.Center)
                }
            }
            Text("Page $label", style = MaterialTheme.typography.labelLarge)
        }
    }
}
