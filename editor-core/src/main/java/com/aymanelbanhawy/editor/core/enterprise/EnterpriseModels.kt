package com.aymanelbanhawy.editor.core.enterprise

import kotlinx.serialization.Serializable

@Serializable
enum class AuthenticationMode {
    Personal,
    Enterprise,
}

@Serializable
enum class AuthenticationProvider {
    Local,
    Oidc,
}

@Serializable
data class OidcProviderConfig(
    val issuerUrl: String = "",
    val clientId: String = "",
    val redirectUri: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email"),
)

@Serializable
data class TenantConfigurationModel(
    val tenantId: String = "personal",
    val tenantName: String = "Personal Workspace",
    val domain: String = "",
    val oidc: OidcProviderConfig = OidcProviderConfig(),
)

@Serializable
enum class LicensePlan {
    Free,
    Premium,
    Enterprise,
}

@Serializable
enum class FeatureFlag {
    Annotate,
    Organize,
    Forms,
    Sign,
    Search,
    Collaboration,
    Security,
    Ai,
    CloudConnectors,
    AdminConsole,
}

@Serializable
enum class CloudConnector {
    LocalFiles,
    GoogleDrive,
    OneDrive,
    SharePoint,
    Box,
}

@Serializable
data class AdminPolicyModel(
    val retentionDays: Int = 30,
    val restrictExport: Boolean = false,
    val forcedWatermarkText: String = "",
    val allowedCloudConnectors: List<CloudConnector> = listOf(CloudConnector.LocalFiles),
    val aiEnabled: Boolean = false,
    val allowCloudAiProviders: Boolean = false,
)

@Serializable
data class PrivacySettingsModel(
    val telemetryEnabled: Boolean = true,
    val includeDocumentNames: Boolean = false,
    val includeDiagnostics: Boolean = true,
)

@Serializable
data class AuthSessionModel(
    val mode: AuthenticationMode = AuthenticationMode.Personal,
    val provider: AuthenticationProvider = AuthenticationProvider.Local,
    val isSignedIn: Boolean = false,
    val displayName: String = "Guest",
    val email: String = "",
)

@Serializable
data class EnterpriseAdminStateModel(
    val authSession: AuthSessionModel = AuthSessionModel(),
    val tenantConfiguration: TenantConfigurationModel = TenantConfigurationModel(),
    val plan: LicensePlan = LicensePlan.Free,
    val privacySettings: PrivacySettingsModel = PrivacySettingsModel(),
    val adminPolicy: AdminPolicyModel = AdminPolicyModel(),
)

@Serializable
data class EntitlementStateModel(
    val plan: LicensePlan,
    val features: Set<FeatureFlag>,
)

@Serializable
enum class TelemetryCategory {
    Product,
    Admin,
    Diagnostic,
}

@Serializable
data class TelemetryEventModel(
    val id: String,
    val category: TelemetryCategory,
    val name: String,
    val createdAtEpochMillis: Long,
    val properties: Map<String, String> = emptyMap(),
)
