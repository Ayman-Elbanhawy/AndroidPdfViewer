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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.collaboration.ReviewFilterModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadState
import com.aymanelbanhawy.editor.core.collaboration.ShareLinkModel
import com.aymanelbanhawy.editor.core.collaboration.VersionSnapshotModel

@Composable
fun ReviewSidebar(
    modifier: Modifier,
    shareLinks: List<ShareLinkModel>,
    reviewThreads: List<ReviewThreadModel>,
    snapshots: List<VersionSnapshotModel>,
    filter: ReviewFilterModel,
    pendingSyncCount: Int,
    onCreateShareLink: () -> Unit,
    onCreateSnapshot: () -> Unit,
    onSyncNow: () -> Unit,
    onFilterChanged: (ReviewFilterModel) -> Unit,
    onAddThread: (String, String) -> Unit,
    onAddReply: (String, String) -> Unit,
    onToggleResolved: (String, Boolean) -> Unit,
) {
    var draftTitle by remember { mutableStateOf("") }
    var draftMessage by remember { mutableStateOf("") }
    var replyByThreadId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Review", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onCreateShareLink, label = { Text("Share Link") })
                AssistChip(onClick = onCreateSnapshot, label = { Text("Snapshot") })
                AssistChip(onClick = onSyncNow, label = { Text("Sync ${pendingSyncCount}") })
            }
            Text("Thread filters", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.state == null, onClick = { onFilterChanged(filter.copy(state = null)) }, label = { Text("All") })
                FilterChip(selected = filter.state == ReviewThreadState.Open, onClick = { onFilterChanged(filter.copy(state = ReviewThreadState.Open)) }, label = { Text("Open") })
                FilterChip(selected = filter.state == ReviewThreadState.Resolved, onClick = { onFilterChanged(filter.copy(state = ReviewThreadState.Resolved)) }, label = { Text("Resolved") })
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = filter.query,
                onValueChange = { onFilterChanged(filter.copy(query = it)) },
                label = { Text("Search threads") },
                singleLine = true,
            )
            Text("New thread", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = draftTitle, onValueChange = { draftTitle = it }, label = { Text("Title") })
            OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = draftMessage, onValueChange = { draftMessage = it }, label = { Text("Comment with @mentions") })
            TextButton(onClick = { onAddThread(draftTitle, draftMessage); draftTitle = ""; draftMessage = "" }) { Text("Add Review Thread") }
            if (shareLinks.isNotEmpty()) {
                Text("Share links", style = MaterialTheme.typography.titleMedium)
                shareLinks.take(2).forEach { link ->
                    Text("${link.title} - ${link.permission.name} - ${link.shareUrl}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (snapshots.isNotEmpty()) {
                Text("Versions", style = MaterialTheme.typography.titleMedium)
                snapshots.take(3).forEach { snapshot ->
                    Text("${snapshot.label} · pages ${snapshot.comparison.pageCountDelta} · comments ${snapshot.comparison.commentDelta}", style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reviewThreads, key = { it.id }) { thread ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(thread.title, style = MaterialTheme.typography.titleSmall)
                            Text("${thread.createdBy} · ${thread.state.name}", style = MaterialTheme.typography.labelMedium)
                            thread.comments.forEach { comment ->
                                Text("${comment.author}: ${comment.message}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = replyByThreadId[thread.id].orEmpty(),
                                onValueChange = { replyByThreadId = replyByThreadId + (thread.id to it) },
                                label = { Text("Reply") },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val reply = replyByThreadId[thread.id].orEmpty()
                                    if (reply.isNotBlank()) {
                                        onAddReply(thread.id, reply)
                                        replyByThreadId = replyByThreadId + (thread.id to "")
                                    }
                                }) { Text("Reply") }
                                TextButton(onClick = { onToggleResolved(thread.id, thread.state != ReviewThreadState.Resolved) }) {
                                    Text(if (thread.state == ReviewThreadState.Resolved) "Reopen" else "Resolve")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
