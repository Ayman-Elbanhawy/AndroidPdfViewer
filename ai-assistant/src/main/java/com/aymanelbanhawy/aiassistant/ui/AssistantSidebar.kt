package com.aymanelbanhawy.aiassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistiveSuggestionType
import com.aymanelbanhawy.aiassistant.core.SemanticSearchCard

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AssistantSidebar(
    modifier: Modifier = Modifier,
    state: AssistantUiState,
    onPromptChanged: (String) -> Unit,
    onAskPdf: () -> Unit,
    onSummarizeDocument: () -> Unit,
    onSummarizePage: () -> Unit,
    onExtractActionItems: () -> Unit,
    onExplainSelection: () -> Unit,
    onSemanticSearch: () -> Unit,
    onPrivacyModeChanged: (AssistantPrivacyMode) -> Unit,
    onOpenCitation: (Int) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Assistant", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.availability.reason ?: "Grounded answers use page and region citations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.availability.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ask about this PDF") },
                    minLines = 3,
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantPrivacyMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.privacyMode == mode,
                            onClick = { onPrivacyModeChanged(mode) },
                            label = { Text(mode.name) },
                        )
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantIconButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Ask PDF", enabled = state.availability.enabled && !state.isWorking, onClick = onAskPdf)
                    AssistantIconButton(icon = Icons.Outlined.Subject, tooltip = "Summarize Document", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizeDocument)
                    AssistantIconButton(icon = Icons.Outlined.Description, tooltip = "Summarize Page", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizePage)
                    AssistantIconButton(icon = Icons.Outlined.Checklist, tooltip = "Extract Action Items", enabled = state.availability.enabled && !state.isWorking, onClick = onExtractActionItems)
                    AssistantIconButton(icon = Icons.Outlined.HelpOutline, tooltip = "Explain Selection", enabled = state.availability.enabled && !state.isWorking, onClick = onExplainSelection)
                    AssistantIconButton(icon = Icons.Outlined.Search, tooltip = "Semantic Search", enabled = state.availability.enabled && !state.isWorking, onClick = onSemanticSearch)
                }
            }
            state.latestResult?.let { result ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(result.headline, style = MaterialTheme.typography.titleMedium)
                        Text(result.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (result.actionItems.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Action Items", style = MaterialTheme.typography.titleSmall)
                            result.actionItems.forEach { item -> Text("- $item", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (result.citations.isNotEmpty()) {
                    item { Text("Citations", style = MaterialTheme.typography.titleSmall) }
                    items(result.citations, key = { it.id }) { citation ->
                        Surface(onClick = { onOpenCitation(citation.anchor.pageIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(citation.title, style = MaterialTheme.typography.labelLarge)
                                Text("${citation.anchor.pageLabel} • ${citation.anchor.regionLabel}", style = MaterialTheme.typography.labelMedium)
                                Text(citation.anchor.quote, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (state.semanticCards.isNotEmpty()) {
                item { Text("Semantic Results", style = MaterialTheme.typography.titleSmall) }
                items(state.semanticCards, key = { it.id }) { card ->
                    SemanticCard(card = card, onOpen = { onOpenCitation(card.anchor.pageIndex) })
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item { Text("Assistive Suggestions", style = MaterialTheme.typography.titleSmall) }
                items(state.suggestions, key = { it.id }) { suggestion ->
                    Surface(onClick = { onOpenCitation(suggestion.anchor.pageIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(suggestion.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (suggestion.type == AssistiveSuggestionType.FormAutofill) "Form autofill" else "Redaction review",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(suggestion.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (state.conversation.isNotEmpty()) {
                item { Text("Recent AI Thread", style = MaterialTheme.typography.titleSmall) }
                items(state.conversation.reversed(), key = { it.id }) { message ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(message.role.name, style = MaterialTheme.typography.labelLarge)
                            Text(message.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantIconButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(icon, contentDescription = tooltip)
            }
        }
    }
}

@Composable
private fun SemanticCard(
    card: SemanticSearchCard,
    onOpen: () -> Unit,
) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, style = MaterialTheme.typography.labelLarge)
                Text(card.snippet, style = MaterialTheme.typography.bodySmall)
            }
            Text(String.format("%.2f", card.score), modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}
