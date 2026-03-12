package com.aymanelbanhawy.enterprisepdf.app.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.runtime.RuntimeBreadcrumbModel
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsSnapshot
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun DiagnosticsSidebar(
    modifier: Modifier,
    snapshot: RuntimeDiagnosticsSnapshot,
    onRefresh: () -> Unit,
    onRepair: () -> Unit,
    onCleanupCaches: () -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Runtime Diagnostics", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Refresh, tooltip = "Refresh Diagnostics", onClick = onRefresh)
                            IconTooltipButton(icon = Icons.Outlined.BuildCircle, tooltip = "Run Repair", onClick = onRepair)
                            IconTooltipButton(icon = Icons.Outlined.CleaningServices, tooltip = "Clean Caches", onClick = onCleanupCaches)
                        }
                        Text("Startup: ${snapshot.startupElapsedMillis} ms")
                        Text("Last open: ${snapshot.lastDocumentOpenElapsedMillis} ms")
                        Text("Last save: ${snapshot.lastSaveElapsedMillis} ms")
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Caches", style = MaterialTheme.typography.titleSmall)
                        Text("Thumbnails: ${snapshot.cache.thumbnailFileCount} files / ${snapshot.cache.thumbnailBytes} bytes")
                        Text("Connector cache: ${snapshot.cache.connectorCacheFileCount} files / ${snapshot.cache.connectorCacheBytes} bytes")
                        Text("Exports: ${snapshot.cache.exportCacheFileCount} files / ${snapshot.cache.exportCacheBytes} bytes")
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Queues", style = MaterialTheme.typography.titleSmall)
                        Text("Pending OCR: ${snapshot.queues.pendingOcrJobs}")
                        Text("Running OCR: ${snapshot.queues.runningOcrJobs}")
                        Text("Failed OCR: ${snapshot.queues.failedOcrJobs}")
                        Text("Pending sync: ${snapshot.queues.pendingSyncOperations}")
                        Text("Connector transfers: ${snapshot.queues.connectorTransferJobs}")
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Provider Health", style = MaterialTheme.typography.titleSmall)
                        snapshot.providerHealth.ifEmpty { listOf() }.forEach { provider ->
                            Text("${provider.name}: ${provider.status} - ${provider.detail}")
                        }
                        if (snapshot.providerHealth.isEmpty()) {
                            Text("No provider diagnostics recorded yet.")
                        }
                    }
                }
            }
            item { Text("Recent Failures", style = MaterialTheme.typography.titleSmall) }
            items(snapshot.recentFailures, key = { it.id }) { breadcrumb ->
                BreadcrumbCard(breadcrumb)
            }
            item { Text("Recent Breadcrumbs", style = MaterialTheme.typography.titleSmall) }
            items(snapshot.recentBreadcrumbs, key = { it.id }) { breadcrumb ->
                BreadcrumbCard(breadcrumb)
            }
        }
    }
}

@Composable
private fun BreadcrumbCard(breadcrumb: RuntimeBreadcrumbModel) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(breadcrumb.eventName, style = MaterialTheme.typography.labelLarge)
            Text("${breadcrumb.category.name} | ${breadcrumb.level.name}", style = MaterialTheme.typography.bodySmall)
            Text(breadcrumb.message, style = MaterialTheme.typography.bodySmall)
            breadcrumb.metadata.forEach { (key, value) ->
                Text("$key: $value", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
