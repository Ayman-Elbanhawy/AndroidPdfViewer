package com.aymanelbanhawy.enterprisepdf.app.enterprise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
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
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun SettingsAdminSidebar(
    modifier: Modifier,
    state: EnterpriseAdminStateModel,
    entitlements: EntitlementStateModel,
    telemetryEvents: List<TelemetryEventModel>,
    diagnosticsCount: Int,
    onSignInPersonal: (String) -> Unit,
    onSignInEnterprise: (String, TenantConfigurationModel) -> Unit,
    onSignOut: () -> Unit,
    onSetPlan: (LicensePlan) -> Unit,
    onUpdatePrivacy: (PrivacySettingsModel) -> Unit,
    onUpdatePolicy: (AdminPolicyModel) -> Unit,
    onGenerateDiagnostics: () -> Unit,
) {
    var personalName by remember { mutableStateOf(state.authSession.displayName) }
    var enterpriseEmail by remember { mutableStateOf(state.authSession.email) }
    var tenantName by remember { mutableStateOf(state.tenantConfiguration.tenantName) }
    var tenantDomain by remember { mutableStateOf(state.tenantConfiguration.domain) }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Authentication", style = MaterialTheme.typography.titleMedium)
                        Text("Mode: ${state.authSession.mode.name}")
                        OutlinedTextField(value = personalName, onValueChange = { personalName = it }, label = { Text("Personal display name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = enterpriseEmail, onValueChange = { enterpriseEmail = it }, label = { Text("Enterprise email") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("Tenant name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tenantDomain, onValueChange = { tenantDomain = it }, label = { Text("Tenant domain") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Person, tooltip = "Use Personal Mode", onClick = { onSignInPersonal(personalName) })
                            IconTooltipButton(icon = Icons.Outlined.Apartment, tooltip = "Use Enterprise Mode", onClick = {
                                onSignInEnterprise(
                                    enterpriseEmail,
                                    TenantConfigurationModel(
                                        tenantId = tenantDomain.ifBlank { "enterprise" },
                                        tenantName = tenantName.ifBlank { "Enterprise Tenant" },
                                        domain = tenantDomain,
                                        oidc = state.tenantConfiguration.oidc.copy(issuerUrl = if (tenantDomain.isBlank()) "" else "https://$tenantDomain/.well-known/openid-configuration"),
                                    ),
                                )
                            })
                            IconTooltipButton(icon = Icons.Outlined.Logout, tooltip = "Sign Out", onClick = onSignOut)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Tenant + License", style = MaterialTheme.typography.titleMedium)
                        Text("Tenant: ${state.tenantConfiguration.tenantName}")
                        Text("Plan: ${state.plan.name}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Badge, tooltip = "Use Free Plan", selected = state.plan == LicensePlan.Free, onClick = { onSetPlan(LicensePlan.Free) })
                            IconTooltipButton(icon = Icons.Outlined.WorkspacePremium, tooltip = "Use Premium Plan", selected = state.plan == LicensePlan.Premium, onClick = { onSetPlan(LicensePlan.Premium) })
                            IconTooltipButton(icon = Icons.Outlined.Apartment, tooltip = "Use Enterprise Plan", selected = state.plan == LicensePlan.Enterprise, onClick = { onSetPlan(LicensePlan.Enterprise) })
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Entitlements", style = MaterialTheme.typography.titleMedium)
                        FeatureFlag.entries.forEach { flag ->
                            Text("${flag.name}: ${flag in entitlements.features}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Admin Policy", style = MaterialTheme.typography.titleMedium)
                        ToggleRow("Restrict export", state.adminPolicy.restrictExport) { onUpdatePolicy(state.adminPolicy.copy(restrictExport = it)) }
                        ToggleRow("AI enabled", state.adminPolicy.aiEnabled) { onUpdatePolicy(state.adminPolicy.copy(aiEnabled = it)) }
                        ToggleRow("Allow cloud AI providers", state.adminPolicy.allowCloudAiProviders) { onUpdatePolicy(state.adminPolicy.copy(allowCloudAiProviders = it)) }
                        ToggleRow("Allow Google Drive", CloudConnector.GoogleDrive in state.adminPolicy.allowedCloudConnectors) {
                            val connectors = state.adminPolicy.allowedCloudConnectors.toMutableSet()
                            if (it) connectors.add(CloudConnector.GoogleDrive) else connectors.remove(CloudConnector.GoogleDrive)
                            onUpdatePolicy(state.adminPolicy.copy(allowedCloudConnectors = connectors.toList().ifEmpty { listOf(CloudConnector.LocalFiles) }))
                        }
                        ToggleRow("Allow SharePoint", CloudConnector.SharePoint in state.adminPolicy.allowedCloudConnectors) {
                            val connectors = state.adminPolicy.allowedCloudConnectors.toMutableSet()
                            if (it) connectors.add(CloudConnector.SharePoint) else connectors.remove(CloudConnector.SharePoint)
                            onUpdatePolicy(state.adminPolicy.copy(allowedCloudConnectors = connectors.toList().ifEmpty { listOf(CloudConnector.LocalFiles) }))
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Privacy + Diagnostics", style = MaterialTheme.typography.titleMedium)
                        ToggleRow("Telemetry enabled", state.privacySettings.telemetryEnabled) { onUpdatePrivacy(state.privacySettings.copy(telemetryEnabled = it)) }
                        ToggleRow("Include document names", state.privacySettings.includeDocumentNames) { onUpdatePrivacy(state.privacySettings.copy(includeDocumentNames = it)) }
                        ToggleRow("Include diagnostics", state.privacySettings.includeDiagnostics) { onUpdatePrivacy(state.privacySettings.copy(includeDiagnostics = it)) }
                        IconTooltipButton(icon = Icons.Outlined.CloudDone, tooltip = "Generate Diagnostics Bundle", onClick = onGenerateDiagnostics)
                        Text("Bundles created: $diagnosticsCount")
                    }
                }
            }
            items(telemetryEvents.take(20), key = { it.id }) { event ->
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.name, style = MaterialTheme.typography.labelLarge)
                        Text(event.category.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


