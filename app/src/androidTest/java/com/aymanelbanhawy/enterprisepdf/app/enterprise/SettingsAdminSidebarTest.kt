package com.aymanelbanhawy.enterprisepdf.app.enterprise

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import org.junit.Rule
import org.junit.Test

class SettingsAdminSidebarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun aiToggleIsReadOnlyWhenPlanDoesNotIncludeAi() {
        composeRule.setContent {
            MaterialTheme {
                SettingsAdminSidebar(
                    modifier = Modifier,
                    state = EnterpriseAdminStateModel(
                        plan = LicensePlan.Premium,
                        adminPolicy = AdminPolicyModel(aiEnabled = true),
                    ),
                    entitlements = EntitlementStateModel(LicensePlan.Premium, emptySet()),
                    telemetryEvents = emptyList(),
                    diagnosticsCount = 0,
                    connectorAccounts = emptyList(),
                    connectorJobs = emptyList(),
                    onSignInPersonal = {},
                    onSignInEnterprise = { _, _ -> },
                    onSignOut = {},
                    onSetPlan = {},
                    onUpdatePrivacy = {},
                    onUpdatePolicy = {},
                    onGenerateDiagnostics = {},
                    onRefreshRemoteState = {},
                    onFlushTelemetry = {},
                    onSaveConnectorAccount = {},
                    onTestConnectorConnection = {},
                    onOpenConnectorDocument = { _, _, _ -> },
                    onSyncConnectorTransfers = {},
                    onCleanupConnectorCache = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-admin-ai-enabled-switch").assertIsNotEnabled()
        composeRule.onNodeWithTag("settings-admin-ai-enabled-message")
            .assertTextContains("AI is not included in the current plan.")
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun aiToggleIsEnabledWhenEntitlementIncludesAi() {
        composeRule.setContent {
            MaterialTheme {
                SettingsAdminSidebar(
                    modifier = Modifier,
                    state = EnterpriseAdminStateModel(
                        plan = LicensePlan.Enterprise,
                        adminPolicy = AdminPolicyModel(aiEnabled = false),
                    ),
                    entitlements = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Ai)),
                    telemetryEvents = emptyList(),
                    diagnosticsCount = 0,
                    connectorAccounts = emptyList(),
                    connectorJobs = emptyList(),
                    onSignInPersonal = {},
                    onSignInEnterprise = { _, _ -> },
                    onSignOut = {},
                    onSetPlan = {},
                    onUpdatePrivacy = {},
                    onUpdatePolicy = {},
                    onGenerateDiagnostics = {},
                    onRefreshRemoteState = {},
                    onFlushTelemetry = {},
                    onSaveConnectorAccount = {},
                    onTestConnectorConnection = {},
                    onOpenConnectorDocument = { _, _, _ -> },
                    onSyncConnectorTransfers = {},
                    onCleanupConnectorCache = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-admin-ai-enabled-switch").assertIsEnabled()
        composeRule.onNodeWithTag("settings-admin-ai-enabled-message")
            .assertTextContains("Entitlement grants the feature; admin policy may restrict it.")
    }
}
