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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun SearchSidebar(
    modifier: Modifier,
    query: String,
    results: SearchResultSet,
    recentSearches: List<String>,
    outlineItems: List<OutlineItem>,
    selectedText: String,
    isIndexing: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectHit: (Int) -> Unit,
    onPreviousHit: () -> Unit,
    onNextHit: () -> Unit,
    onUseRecentSearch: (String) -> Unit,
    onOpenOutlineItem: (Int) -> Unit,
    onCopySelectedText: () -> Unit,
    onShareSelectedText: () -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Search", style = MaterialTheme.typography.titleLarge)
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
            if (recentSearches.isNotEmpty()) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium)
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
            Text("Results (${results.hits.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(results.hits, key = { _, hit -> "${hit.pageIndex}:${hit.preview}" }) { index, hit ->
                    SearchHitCard(hit = hit, selected = index == results.selectedHitIndex, onClick = { onSelectHit(index) })
                }
                if (outlineItems.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item { Text("Bookmarks", style = MaterialTheme.typography.titleMedium) }
                    appendOutlineItems(outlineItems, depth = 0, onOpenOutlineItem = onOpenOutlineItem)
                }
            }
            if (selectedText.isNotBlank()) {
                HorizontalDivider()
                Text("Selected text", style = MaterialTheme.typography.titleMedium)
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
