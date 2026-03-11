package com.aymanelbanhawy.enterprisepdf.app.collaboration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventModel

@Composable
fun ActivitySidebar(
    modifier: Modifier,
    events: List<ActivityEventModel>,
    pendingSyncCount: Int,
    onSyncNow: () -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Activity", style = MaterialTheme.typography.titleLarge)
                AssistChip(onClick = onSyncNow, label = { Text("Sync ${pendingSyncCount}") })
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(event.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${event.actor} · ${event.createdAtEpochMillis}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
