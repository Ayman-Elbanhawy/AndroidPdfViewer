package com.aymanelbanhawy.aiassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AiProviderKind
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistiveSuggestionType
import com.aymanelbanhawy.aiassistant.core.SemanticSearchCard
import com.aymanelbanhawy.aiassistant.core.toDraft

@OptIn(ExperimentalLayoutApi::class)
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
    onProviderDraftChanged: (AiProviderDraft) -> Unit,
    onSaveProvider: () -> Unit,
    onRefreshProviders: () -> Unit,
    onTestConnection: () -> Unit,
    onCancelRequest: () -> Unit,
    onOpenCitation: (Int) -> Unit,
) {
    val providerState = state.providerRuntime
    val draft = providerState.draft
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Assistant", style = MaterialTheme.typography.titleLarge)
                    Text(state.availability.reason ?: providerState.diagnosticsMessage.ifBlank { "Grounded answers use page and region citations." }, style = MaterialTheme.typography.bodyMedium, color = if (state.availability.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            }
            item {
                OutlinedTextField(value = state.prompt, onValueChange = onPromptChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Ask about this PDF") }, minLines = 3)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantPrivacyMode.entries.forEach { mode ->
                        FilterChip(selected = state.settings.privacyMode == mode, onClick = { onPrivacyModeChanged(mode) }, label = { Text(mode.name) })
                    }
                }
            }
            item {
                Text("Provider", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProviderKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = {
                                val profile = providerState.profiles.firstOrNull { it.kind == kind }
                                onProviderDraftChanged((profile?.toDraft() ?: draft.copy(kind = kind)))
                            },
                            label = { Text(kind.name) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(value = draft.displayName, onValueChange = { onProviderDraftChanged(draft.copy(displayName = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Provider name") })
            }
            item {
                OutlinedTextField(value = draft.endpointUrl, onValueChange = { onProviderDraftChanged(draft.copy(endpointUrl = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Endpoint URL") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Link, contentDescription = null) })
            }
            item {
                OutlinedTextField(value = draft.apiKeyInput, onValueChange = { onProviderDraftChanged(draft.copy(apiKeyInput = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("API key / token") }, visualTransformation = PasswordVisualTransformation())
            }
            item {
                OutlinedTextField(value = draft.modelId, onValueChange = { onProviderDraftChanged(draft.copy(modelId = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.ModelTraining, contentDescription = null) })
            }
            if (providerState.availableModels.isNotEmpty()) {
                item { Text("Available models", style = MaterialTheme.typography.titleSmall) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        providerState.availableModels.take(8).forEach { model ->
                            FilterChip(selected = draft.modelId == model.id, onClick = { onProviderDraftChanged(draft.copy(modelId = model.id)) }, label = { Text(model.displayName) })
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantActionButton(icon = Icons.Outlined.CheckCircleOutline, tooltip = "Save Provider", onClick = onSaveProvider)
                    AssistantActionButton(icon = Icons.Outlined.NetworkCheck, tooltip = "Test Connection", onClick = onTestConnection)
                    AssistantActionButton(icon = Icons.Outlined.ModelTraining, tooltip = "Refresh Models", onClick = onRefreshProviders)
                    if (state.isWorking) {
                        AssistantActionButton(icon = Icons.Outlined.StopCircle, tooltip = "Cancel Request", onClick = onCancelRequest)
                    }
                }
            }
            providerState.activeCapabilities?.let { capabilities ->
                item {
                    Text("Capabilities: streaming=${capabilities.supportsStreaming}, json=${capabilities.supportsJsonMode}, maxContext=${capabilities.maxContextHint ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            providerState.lastError?.let { error ->
                item { Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (providerState.discoveredLocalApps.isNotEmpty()) {
                item { Text("Detected local AI apps", style = MaterialTheme.typography.titleSmall) }
                items(providerState.discoveredLocalApps, key = { it.packageName }) { app ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(app.appName, style = MaterialTheme.typography.labelLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantActionButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Ask PDF", enabled = state.availability.enabled && !state.isWorking, onClick = onAskPdf)
                    AssistantActionButton(icon = Icons.AutoMirrored.Outlined.Subject, tooltip = "Summarize Document", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizeDocument)
                    AssistantActionButton(icon = Icons.Outlined.Description, tooltip = "Summarize Page", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizePage)
                    AssistantActionButton(icon = Icons.Outlined.Checklist, tooltip = "Extract Action Items", enabled = state.availability.enabled && !state.isWorking, onClick = onExtractActionItems)
                    AssistantActionButton(icon = Icons.AutoMirrored.Outlined.HelpOutline, tooltip = "Explain Selection", enabled = state.availability.enabled && !state.isWorking, onClick = onExplainSelection)
                    AssistantActionButton(icon = Icons.Outlined.Search, tooltip = "Semantic Search", enabled = state.availability.enabled && !state.isWorking, onClick = onSemanticSearch)
                }
            }
            if (providerState.streamPreview.isNotBlank() && state.isWorking) {
                item { Text(providerState.streamPreview, style = MaterialTheme.typography.bodyMedium) }
            }
            state.latestResult?.let { result ->
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(result.headline, style = MaterialTheme.typography.titleMedium); Text(result.body, style = MaterialTheme.typography.bodyMedium) } }
                if (result.actionItems.isNotEmpty()) {
                    item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Action Items", style = MaterialTheme.typography.titleSmall); result.actionItems.forEach { item -> Text("- $item", style = MaterialTheme.typography.bodyMedium) } } }
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
                items(state.semanticCards, key = { it.id }) { card -> SemanticCard(card = card, onOpen = { onOpenCitation(card.anchor.pageIndex) }) }
            }
            if (state.suggestions.isNotEmpty()) {
                item { Text("Assistive Suggestions", style = MaterialTheme.typography.titleSmall) }
                items(state.suggestions, key = { it.id }) { suggestion ->
                    Surface(onClick = { onOpenCitation(suggestion.anchor.pageIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(suggestion.title, style = MaterialTheme.typography.labelLarge)
                            Text(if (suggestion.type == AssistiveSuggestionType.FormAutofill) "Form autofill" else "Redaction review", style = MaterialTheme.typography.labelMedium)
                            Text(suggestion.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticCard(card: SemanticSearchCard, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, style = MaterialTheme.typography.labelLarge)
                Text(card.snippet, style = MaterialTheme.typography.bodySmall)
            }
            Text(String.format("%.2f", card.score), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AssistantActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
        androidx.compose.material3.Icon(icon, contentDescription = tooltip)
    }
}

