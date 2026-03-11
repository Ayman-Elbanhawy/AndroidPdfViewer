package com.aymanelbanhawy.enterprisepdf.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.aymanelbanhawy.editor.core.model.displayLabel

@Composable
fun EditInspectorSidebar(
    modifier: Modifier,
    editObjects: List<PageEditModel>,
    selectedEditObject: PageEditModel?,
    onSelectEdit: (String) -> Unit,
    onAddTextBox: () -> Unit,
    onAddImage: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onReplaceSelectedImage: () -> Unit,
    onTextChanged: (String) -> Unit,
    onFontFamilyChanged: (FontFamilyToken) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onTextColorChanged: (String) -> Unit,
    onTextAlignmentChanged: (TextAlignment) -> Unit,
    onLineSpacingChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Objects", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddTextBox) { Text("Add Text") }
                Button(onClick = onAddImage) { Text("Add Image") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onDuplicateSelected, label = { Text("Duplicate") })
                AssistChip(onClick = onDeleteSelected, label = { Text("Delete") })
                if (selectedEditObject is ImageEditModel) {
                    AssistChip(onClick = onReplaceSelectedImage, label = { Text("Replace") })
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(editObjects, key = { it.id }) { edit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (edit.id == selectedEditObject?.id) 4.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onSelectEdit(edit.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(edit.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(edit.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            selectedEditObject?.let { selected ->
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Inspector", style = MaterialTheme.typography.titleSmall)
                    when (selected) {
                        is TextBoxEditModel -> {
                            OutlinedTextField(
                                value = selected.text,
                                onValueChange = onTextChanged,
                                label = { Text("Text") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                            )
                            Text("Font")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FontFamilyToken.entries.forEach { family ->
                                    FilterChip(selected = selected.fontFamily == family, onClick = { onFontFamilyChanged(family) }, label = { Text(family.name) })
                                }
                            }
                            Text("Alignment")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextAlignment.entries.forEach { alignment ->
                                    FilterChip(selected = selected.alignment == alignment, onClick = { onTextAlignmentChanged(alignment) }, label = { Text(alignment.name) })
                                }
                            }
                            OutlinedTextField(
                                value = selected.textColorHex,
                                onValueChange = onTextColorChanged,
                                label = { Text("Text color") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text("Font size ${selected.fontSizeSp.toInt()}")
                            Slider(value = selected.fontSizeSp, onValueChange = onFontSizeChanged, valueRange = 8f..48f)
                            Text("Line spacing ${"%.2f".format(selected.lineSpacingMultiplier)}")
                            Slider(value = selected.lineSpacingMultiplier, onValueChange = onLineSpacingChanged, valueRange = 0.9f..2f)
                        }
                        is ImageEditModel -> {
                            Text(selected.label, style = MaterialTheme.typography.bodyMedium)
                            Text(selected.imagePath, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Opacity ${"%.2f".format(selected.opacity)}")
                    Slider(value = selected.opacity, onValueChange = onOpacityChanged, valueRange = 0.1f..1f)
                    Text("Rotation ${selected.rotationDegrees.toInt()}°")
                    Slider(value = selected.rotationDegrees, onValueChange = onRotationChanged, valueRange = -180f..180f)
                }
            }
        }
    }
}

