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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo") }
                    IconButton(onClick = onRedo) { Icon(Icons.Outlined.Redo, contentDescription = "Redo") }
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
                    Button(onClick = onRotateSelected, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Rotate90DegreesCw, null); Text("Rotate") }
                    Button(onClick = onDeleteSelected, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null); Text("Delete") }
                    Button(onClick = onDuplicateSelected, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ContentCopy, null); Text("Duplicate") }
                    Button(onClick = onExtractSelected, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ContentCut, null); Text("Extract") }
                    Button(onClick = onInsertBlankPage, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Description, null); Text("Blank") }
                    Button(onClick = onPickImagePage, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddPhotoAlternate, null); Text("Image Page") }
                    Button(onClick = onPickMergePdfs, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Collections, null); Text("Merge PDFs") }
                    OutlinedTextField(
                        value = state.splitRangeExpression,
                        onValueChange = onUpdateSplitRange,
                        label = { Text("Split Range") },
                        supportingText = { Text("1-3,5,7-8") },
                    )
                    Button(onClick = onSplitByRange, modifier = Modifier.fillMaxWidth()) { Text("Split by Range") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSplitOddPages, modifier = Modifier.weight(1f)) { Text("Odd") }
                        Button(onClick = onSplitEvenPages, modifier = Modifier.weight(1f)) { Text("Even") }
                    }
                    Button(onClick = onSplitSelectedPages, modifier = Modifier.fillMaxWidth()) { Text("Split Selected") }
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
                                .animateItemPlacement()
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
