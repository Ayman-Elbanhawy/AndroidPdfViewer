package com.aymanelbanhawy.enterprisepdf.app.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions

@Composable
fun ScanImportDialog(
    options: ScanImportOptions,
    onOptionsChanged: (ScanImportOptions) -> Unit,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = options.displayName,
                    onValueChange = { onOptionsChanged(options.copy(displayName = it)) },
                    label = { Text("Output PDF name") },
                )
                ScanOptionRow("Auto-crop", options.autoCrop) { onOptionsChanged(options.copy(autoCrop = it)) }
                ScanOptionRow("Deskew / orient", options.deskew) { onOptionsChanged(options.copy(deskew = it)) }
                ScanOptionRow("Cleanup", options.cleanup) { onOptionsChanged(options.copy(cleanup = it)) }
                Text(
                    "Imported images are normalized into a PDF session and queued for local OCR indexing.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onPickImages) { Text("Choose Images") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScanOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
