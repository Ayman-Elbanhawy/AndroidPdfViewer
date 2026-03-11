package com.aymanelbanhawy.enterprisepdf.app.connectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountDraft
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorCredentialType
import com.aymanelbanhawy.editor.core.connectors.ConnectorTransferJobModel
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectorSettingsSection(
    modifier: Modifier = Modifier,
    accounts: List<ConnectorAccountModel>,
    jobs: List<ConnectorTransferJobModel>,
    onSaveAccount: (ConnectorAccountDraft) -> Unit,
    onTestConnection: (String) -> Unit,
    onOpenRemoteDocument: (String, String, String) -> Unit,
    onSyncTransfers: () -> Unit,
    onCleanupCache: () -> Unit,
) {
    var connectorType by remember { mutableStateOf(CloudConnector.LocalFiles) }
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var credentialType by remember { mutableStateOf(ConnectorCredentialType.None) }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var remotePath by remember { mutableStateOf("") }
    var remoteDisplayName by remember { mutableStateOf("document.pdf") }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connectors", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CloudConnector.LocalFiles, CloudConnector.WebDav, CloudConnector.DocumentProvider).forEach { type ->
                    FilterChip(
                        selected = connectorType == type,
                        onClick = { connectorType = type },
                        label = { Text(type.label()) },
                    )
                }
            }
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Account name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL or root path") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectorCredentialType.entries.forEach { type ->
                    FilterChip(selected = credentialType == type, onClick = { credentialType = type }, label = { Text(type.name) })
                }
            }
            if (credentialType != ConnectorCredentialType.None) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("API key / password") }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(
                    icon = Icons.Outlined.Link,
                    tooltip = "Save Connector Account",
                    onClick = {
                        onSaveAccount(
                            ConnectorAccountDraft(
                                connectorType = connectorType,
                                displayName = displayName,
                                baseUrl = baseUrl,
                                credentialType = credentialType,
                                username = username,
                                secret = secret,
                            ),
                        )
                        secret = ""
                    },
                )
                IconTooltipButton(icon = Icons.Outlined.CloudSync, tooltip = "Sync Transfers", onClick = onSyncTransfers)
                IconTooltipButton(icon = Icons.Outlined.CleaningServices, tooltip = "Evict Connector Cache", onClick = onCleanupCache)
            }

            if (accounts.isNotEmpty()) {
                Text("Available accounts", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = { selectedAccountId = account.id },
                            label = { Text(account.displayName) },
                        )
                    }
                }
                selectedAccountId.takeIf { it.isNotBlank() }?.let { accountId ->
                    val account = accounts.firstOrNull { it.id == accountId }
                    account?.let {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(it.connectorType.label(), style = MaterialTheme.typography.labelLarge)
                                Text(it.baseUrl, style = MaterialTheme.typography.bodySmall)
                                Text("Capabilities: ${buildString {
                                    if (it.supportsOpen) append("open ")
                                    if (it.supportsSave) append("save ")
                                    if (it.supportsShare) append("share ")
                                    if (it.supportsResumableTransfer) append("resume")
                                }.trim()}".trim(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text("Remote path / file URI") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = remoteDisplayName, onValueChange = { remoteDisplayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTooltipButton(icon = Icons.Outlined.VpnKey, tooltip = "Test Connection", onClick = { onTestConnection(accountId) })
                        IconTooltipButton(
                            icon = Icons.Outlined.CloudDownload,
                            tooltip = "Open Remote Document",
                            enabled = remotePath.isNotBlank(),
                            onClick = { onOpenRemoteDocument(accountId, remotePath, remoteDisplayName.ifBlank { "document.pdf" }) },
                        )
                    }
                }
            }

            if (jobs.isNotEmpty()) {
                Text("Transfers", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jobs.take(8).forEach { job ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(job.remotePath, style = MaterialTheme.typography.bodyMedium)
                                    Text(job.status.name, style = MaterialTheme.typography.bodySmall)
                                }
                                IconTooltipButton(icon = Icons.Outlined.CloudUpload, tooltip = "Sync Queue", onClick = onSyncTransfers)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun CloudConnector.label(): String = when (this) {
    CloudConnector.LocalFiles -> "Local Files"
    CloudConnector.GoogleDrive -> "Google Drive"
    CloudConnector.OneDrive -> "OneDrive"
    CloudConnector.SharePoint -> "SharePoint"
    CloudConnector.Box -> "Box"
    CloudConnector.WebDav -> "WebDAV"
    CloudConnector.DocumentProvider -> "Document Provider"
}