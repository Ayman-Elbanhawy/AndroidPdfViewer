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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.aymanelbanhawy.editor.core.search.SearchResultSet

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
                AssistChip(onClick = onSearch, label = { Text("Search") })
                AssistChip(onClick = onPreviousHit, label = { Text("Prev") })
                AssistChip(onClick = onNextHit, label = { Text("Next") })
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
                    recentSearches.take(4).forEach { recent ->
                        AssistChip(onClick = { onUseRecentSearch(recent) }, label = { Text(recent) })
                    }
                }
            }
            Text("Results (${results.hits.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(results.hits, key = { _, hit -> "${hit.pageIndex}:${hit.preview}" }) { index, hit ->
                    SearchHitCard(hit = hit, selected = index == results.selectedHitIndex, onClick = { onSelectHit(index) })
                }
                if (outlineItems.isNotEmpty()) {
                    item { Divider() }
                    item { Text("Bookmarks", style = MaterialTheme.typography.titleMedium) }
                    appendOutlineItems(outlineItems, depth = 0, onOpenOutlineItem = onOpenOutlineItem)
                }
            }
            if (selectedText.isNotBlank()) {
                Divider()
                Text("Selected text", style = MaterialTheme.typography.titleMedium)
                Text(selectedText, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopySelectedText) { Text("Copy") }
                    TextButton(onClick = onShareSelectedText) { Text("Share") }
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
        TextButton(onClick = { onOpenOutlineItem(item.pageIndex) }) {
            Text("${"  ".repeat(depth)}${item.title}")
        }
    }
    outline.forEach { item ->
        appendOutlineItems(item.children, depth + 1, onOpenOutlineItem)
    }
}
