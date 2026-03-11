package com.aymanelbanhawy.enterprisepdf.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.DocumentPermissionModel
import com.aymanelbanhawy.editor.core.security.MetadataScrubOptionsModel
import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel
import com.aymanelbanhawy.editor.core.security.TenantPolicyHooksModel

@Composable
fun SecuritySidebar(
    modifier: Modifier,
    security: SecurityDocumentModel,
    appLockSettings: AppLockSettingsModel,
    auditEvents: List<AuditTrailEventModel>,
    onConfigureAppLock: (Boolean, String, Boolean, Int) -> Unit,
    onLockNow: () -> Unit,
    onUpdatePermissions: (DocumentPermissionModel) -> Unit,
    onUpdateTenantPolicy: (TenantPolicyHooksModel) -> Unit,
    onUpdatePasswordProtection: (Boolean, String, String) -> Unit,
    onUpdateWatermark: (Boolean, String) -> Unit,
    onUpdateMetadataScrub: (MetadataScrubOptionsModel) -> Unit,
    onInspect: () -> Unit,
    onMarkRedaction: () -> Unit,
    onPreviewRedactions: (Boolean) -> Unit,
    onApplyRedactions: () -> Unit,
    onRemoveRedaction: (String) -> Unit,
    onExportAudit: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var timeoutSeconds by remember { mutableStateOf(appLockSettings.lockTimeoutSeconds.toString()) }
    var ownerPassword by remember { mutableStateOf(security.passwordProtection.ownerPassword) }
    var userPassword by remember { mutableStateOf(security.passwordProtection.userPassword) }
    var watermarkText by remember { mutableStateOf(security.watermark.text) }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("App Lock", style = MaterialTheme.typography.titleMedium)
                        SettingRow("Enable lock", appLockSettings.enabled) {
                            onConfigureAppLock(it, pin, appLockSettings.biometricsEnabled, timeoutSeconds.toIntOrNull() ?: appLockSettings.lockTimeoutSeconds)
                        }
                        SettingRow("Use biometrics", appLockSettings.biometricsEnabled) {
                            onConfigureAppLock(appLockSettings.enabled, pin, it, timeoutSeconds.toIntOrNull() ?: appLockSettings.lockTimeoutSeconds)
                        }
                        OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = timeoutSeconds, onValueChange = { timeoutSeconds = it }, label = { Text("Lock timeout seconds") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfigureAppLock(appLockSettings.enabled, pin, appLockSettings.biometricsEnabled, timeoutSeconds.toIntOrNull() ?: appLockSettings.lockTimeoutSeconds) }) { Text("Save lock") }
                            Button(onClick = onLockNow) { Text("Lock now") }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Document Permissions", style = MaterialTheme.typography.titleMedium)
                        PermissionToggle("Allow print", security.permissions.allowPrint) { onUpdatePermissions(security.permissions.copy(allowPrint = it)) }
                        PermissionToggle("Allow copy", security.permissions.allowCopy) { onUpdatePermissions(security.permissions.copy(allowCopy = it)) }
                        PermissionToggle("Allow share", security.permissions.allowShare) { onUpdatePermissions(security.permissions.copy(allowShare = it)) }
                        PermissionToggle("Allow export", security.permissions.allowExport) { onUpdatePermissions(security.permissions.copy(allowExport = it)) }
                        Text("Tenant Hooks", style = MaterialTheme.typography.titleSmall)
                        PermissionToggle("Disable print", security.tenantPolicy.disablePrint) { onUpdateTenantPolicy(security.tenantPolicy.copy(disablePrint = it)) }
                        PermissionToggle("Disable copy", security.tenantPolicy.disableCopy) { onUpdateTenantPolicy(security.tenantPolicy.copy(disableCopy = it)) }
                        PermissionToggle("Disable share", security.tenantPolicy.disableShare) { onUpdateTenantPolicy(security.tenantPolicy.copy(disableShare = it)) }
                        PermissionToggle("Disable export", security.tenantPolicy.disableExport) { onUpdateTenantPolicy(security.tenantPolicy.copy(disableExport = it)) }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Protection", style = MaterialTheme.typography.titleMedium)
                        SettingRow("Password protect", security.passwordProtection.enabled) {
                            onUpdatePasswordProtection(it, userPassword, ownerPassword)
                        }
                        OutlinedTextField(value = userPassword, onValueChange = { userPassword = it }, label = { Text("Open password") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = ownerPassword, onValueChange = { ownerPassword = it }, label = { Text("Owner password") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onUpdatePasswordProtection(security.passwordProtection.enabled, userPassword, ownerPassword) }) { Text("Apply passwords") }
                        SettingRow("Watermark", security.watermark.enabled) {
                            onUpdateWatermark(it, watermarkText)
                        }
                        OutlinedTextField(value = watermarkText, onValueChange = { watermarkText = it }, label = { Text("Watermark text") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onUpdateWatermark(security.watermark.enabled, watermarkText) }) { Text("Update watermark") }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Metadata + Inspection", style = MaterialTheme.typography.titleMedium)
                        PermissionToggle("Scrub metadata on save", security.metadataScrub.enabled) {
                            onUpdateMetadataScrub(security.metadataScrub.copy(enabled = it))
                        }
                        PermissionToggle("Scrub author", security.metadataScrub.scrubAuthor) {
                            onUpdateMetadataScrub(security.metadataScrub.copy(scrubAuthor = it))
                        }
                        PermissionToggle("Scrub title", security.metadataScrub.scrubTitle) {
                            onUpdateMetadataScrub(security.metadataScrub.copy(scrubTitle = it))
                        }
                        Button(onClick = onInspect) { Text("Run inspection") }
                        security.inspectionReport.findings.forEach { finding ->
                            Text("${finding.severity}: ${finding.title} - ${finding.message}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Redaction", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onMarkRedaction) { Text("Mark selected text") }
                        SettingRow("Preview", security.redactionWorkflow.previewEnabled, onPreviewRedactions)
                        Button(onClick = onApplyRedactions) { Text("Apply irreversible redactions") }
                        security.redactionWorkflow.marks.forEach { mark ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Page ${mark.pageIndex + 1}: ${mark.label} (${mark.status.name})", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onRemoveRedaction(mark.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Audit Trail", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onExportAudit) { Text("Export audit log") }
                    }
                }
            }
            items(auditEvents, key = { it.id }) { event ->
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.type.name, style = MaterialTheme.typography.labelLarge)
                        Text(event.message, style = MaterialTheme.typography.bodyMedium)
                        Text(event.actor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(label, checked, onCheckedChange)
}
