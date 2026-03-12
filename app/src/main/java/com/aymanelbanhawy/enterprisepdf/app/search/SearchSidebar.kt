package com.aymanelbanhawy.enterprisepdf.app.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.ocr.OcrJobSummary
import com.aymanelbanhawy.editor.core.ocr.OcrModelDeliveryMode
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import kotlin.math.roundToInt

@Composable
fun SearchSidebar(
    modifier: Modifier,
    query: String,
    results: SearchResultSet,
    recentSearches: List<String>,
    outlineItems: List<OutlineItem>,
    selectedText: String,
    isIndexing: Boolean,
    ocrJobs: List<OcrJobSummary>,
    ocrSettings: OcrSettingsModel,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectHit: (Int) -> Unit,
    onPreviousHit: () -> Unit,
    onNextHit: () -> Unit,
    onUseRecentSearch: (String) -> Unit,
    onOpenOutlineItem: (Int) -> Unit,
    onCopySelectedText: () -> Unit,
    onShareSelectedText: () -> Unit,
    onOcrSettingsChanged: (OcrSettingsModel) -> Unit,
    onSaveOcrSettings: () -> Unit,
    onPauseOcr: (Int?) -> Unit,
    onResumeOcr: (Int?) -> Unit,
    onRerunOcr: (Int?) -> Unit,
    onOpenOcrPage: (Int) -> Unit,
) {
    Surface(modifier = modifier.semantics { paneTitle = "Search panel" }, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Search", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Find in document") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.Search, tooltip = "Search", onClick = onSearch)
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, tooltip = "Previous Result", onClick = onPreviousHit)
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.ArrowForward, tooltip = "Next Result", onClick = onNextHit)
            }
            if (isIndexing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 2.dp), strokeWidth = 2.dp)
                    Text("Refreshing search index", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OcrSection(
                jobs = ocrJobs,
                settings = ocrSettings,
                onSettingsChanged = onOcrSettingsChanged,
                onSaveSettings = onSaveOcrSettings,
                onPauseOcr = onPauseOcr,
                onResumeOcr = onResumeOcr,
                onRerunOcr = onRerunOcr,
                onOpenOcrPage = onOpenOcrPage,
            )
            if (recentSearches.isNotEmpty()) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentSearches.take(6).forEach { recent ->
                        IconTooltipButton(
                            icon = Icons.Outlined.History,
                            tooltip = "Run recent search: $recent",
                            onClick = { onUseRecentSearch(recent) },
                        )
                    }
                }
            }
            Text("Results (${results.hits.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(results.hits, key = { _, hit -> "${hit.pageIndex}:${hit.preview}" }) { index, hit ->
                    SearchHitCard(hit = hit, selected = index == results.selectedHitIndex, onClick = { onSelectHit(index) })
                }
                if (outlineItems.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item { Text("Bookmarks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    appendOutlineItems(outlineItems, depth = 0, onOpenOutlineItem = onOpenOutlineItem)
                }
            }
            if (selectedText.isNotBlank()) {
                HorizontalDivider()
                Text("Selected text", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(selectedText, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Copy Selected Text", onClick = onCopySelectedText)
                    IconTooltipButton(icon = Icons.Outlined.IosShare, tooltip = "Share Selected Text", onClick = onShareSelectedText)
                }
            }
        }
    }
}

@Composable
private fun OcrSection(
    jobs: List<OcrJobSummary>,
    settings: OcrSettingsModel,
    onSettingsChanged: (OcrSettingsModel) -> Unit,
    onSaveSettings: () -> Unit,
    onPauseOcr: (Int?) -> Unit,
    onResumeOcr: (Int?) -> Unit,
    onRerunOcr: (Int?) -> Unit,
    onOpenOcrPage: (Int) -> Unit,
) {
    val completed = jobs.count { it.status == OcrJobStatus.Completed }
    val failed = jobs.count { it.status == OcrJobStatus.Failed }
    val paused = jobs.count { it.status == OcrJobStatus.Paused }
    val running = jobs.count { it.status == OcrJobStatus.Running }
    val pending = jobs.count { it.status == OcrJobStatus.Pending || it.status == OcrJobStatus.RetryScheduled }
    val progress = if (jobs.isEmpty()) 0 else jobs.map { it.progressPercent.coerceIn(0, 100) }.average().roundToInt()

    HorizontalDivider()
    Text("OCR", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(
        if (jobs.isEmpty()) {
            "No OCR jobs for this document yet. Imported scans will show progress here as pages become searchable."
        } else {
            "Progress $progress% · Completed $completed/${jobs.size} · Running $running · Pending $pending · Paused $paused · Failed $failed"
        },
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTooltipButton(icon = Icons.Outlined.Save, tooltip = "Save OCR Settings", onClick = onSaveSettings)
        IconTooltipButton(icon = Icons.Outlined.PauseCircleOutline, tooltip = "Pause All OCR", onClick = { onPauseOcr(null) })
        IconTooltipButton(icon = Icons.Outlined.PlayCircleOutline, tooltip = "Resume All OCR", onClick = { onResumeOcr(null) })
        IconTooltipButton(icon = Icons.Outlined.Sync, tooltip = "Re-run All OCR", onClick = { onRerunOcr(null) })
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = settings.languageHints.joinToString(", "),
        onValueChange = { raw -> onSettingsChanged(settings.copy(languageHints = raw.split(',').map { it.trim() }.filter { it.isNotBlank() })) },
        label = { Text("Language hints") },
        supportingText = { Text("Comma-separated BCP-47 hints, for example: en, ar, fr") },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = settings.timeoutSeconds.toString(),
            onValueChange = { value -> value.toIntOrNull()?.let { onSettingsChanged(settings.copy(timeoutSeconds = it.coerceIn(5, 120))) } },
            label = { Text("Timeout") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = settings.maxRetryCount.toString(),
            onValueChange = { value -> value.toIntOrNull()?.let { onSettingsChanged(settings.copy(maxRetryCount = it.coerceIn(0, 6))) } },
            label = { Text("Retries") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = settings.pagesPerWorkerBatch.toString(),
            onValueChange = { value -> value.toIntOrNull()?.let { onSettingsChanged(settings.copy(pagesPerWorkerBatch = it.coerceIn(1, 24))) } },
            label = { Text("Batch") },
            singleLine = true,
        )
    }
    ToggleRow(
        label = "Platform-managed OCR delivery",
        checked = settings.deliveryMode == OcrModelDeliveryMode.PlatformManaged,
        onCheckedChange = {
            onSettingsChanged(
                settings.copy(
                    deliveryMode = if (it) OcrModelDeliveryMode.PlatformManaged else OcrModelDeliveryMode.Bundled,
                ),
            )
        },
    )
    ToggleRow(
        label = "Embed OCR session data on export",
        checked = settings.embedSessionDataOnExport,
        onCheckedChange = { onSettingsChanged(settings.copy(embedSessionDataOnExport = it)) },
    )
    ToggleRow(
        label = "Binarize before OCR",
        checked = settings.preprocessing.binarize,
        onCheckedChange = {
            onSettingsChanged(
                settings.copy(
                    preprocessing = settings.preprocessing.copy(binarize = it),
                ),
            )
        },
    )
    if (jobs.isNotEmpty()) {
        jobs.take(10).forEach { job ->
            OcrJobCard(job = job, onPauseOcr = onPauseOcr, onResumeOcr = onResumeOcr, onRerunOcr = onRerunOcr, onOpenOcrPage = onOpenOcrPage)
        }
        if (jobs.size > 10) {
            Text("Showing first 10 OCR jobs. Remaining pages continue in the background.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OcrJobCard(
    job: OcrJobSummary,
    onPauseOcr: (Int?) -> Unit,
    onResumeOcr: (Int?) -> Unit,
    onRerunOcr: (Int?) -> Unit,
    onOpenOcrPage: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
        onClick = { onOpenOcrPage(job.pageIndex) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Page ${job.pageIndex + 1} · ${job.status.name}", style = MaterialTheme.typography.labelLarge)
            Text("Progress ${job.progressPercent}% · Attempt ${job.attemptCount}/${job.maxAttempts}", style = MaterialTheme.typography.bodySmall)
            job.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (job.diagnostics.isNotEmpty()) {
                Text(job.diagnostics.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.canPause) {
                    IconTooltipButton(icon = Icons.Outlined.PauseCircleOutline, tooltip = "Pause OCR for page ${job.pageIndex + 1}", onClick = { onPauseOcr(job.pageIndex) })
                }
                if (job.canResume) {
                    IconTooltipButton(icon = Icons.Outlined.PlayCircleOutline, tooltip = "Resume OCR for page ${job.pageIndex + 1}", onClick = { onResumeOcr(job.pageIndex) })
                }
                if (job.canRetry) {
                    IconTooltipButton(icon = Icons.Outlined.Refresh, tooltip = "Re-run OCR for page ${job.pageIndex + 1}", onClick = { onRerunOcr(job.pageIndex) })
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SearchHitCard(
    hit: SearchHit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Page ${hit.pageIndex + 1}", style = MaterialTheme.typography.labelLarge)
            Text(hit.preview, style = MaterialTheme.typography.bodyMedium)
            Text(hit.source.name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun LazyListScope.appendOutlineItems(
    outline: List<OutlineItem>,
    depth: Int,
    onOpenOutlineItem: (Int) -> Unit,
) {
    itemsIndexed(outline, key = { _, item -> "${depth}:${item.pageIndex}:${item.title}" }) { _, item ->
        IconTooltipButton(
            icon = Icons.Outlined.BookmarkBorder,
            tooltip = "Open bookmark: ${item.title}",
            onClick = { onOpenOutlineItem(item.pageIndex) },
        )
    }
    outline.forEach { item ->
        appendOutlineItems(item.children, depth + 1, onOpenOutlineItem)
    }
}

