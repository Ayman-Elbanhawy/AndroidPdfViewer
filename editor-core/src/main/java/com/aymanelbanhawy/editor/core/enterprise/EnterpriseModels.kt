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
enum class CollaborationBackendMode {
    Disabled,
    LocalEmulator,
    RemoteHttp,
}

@Serializable
enum class CollaborationScope {
    TenantOnly,
    SharedWorkspace,
    ExternalGuests,
}

@Serializable
data class CollaborationServiceConfig(
    val backendMode: CollaborationBackendMode = CollaborationBackendMode.LocalEmulator,
    val baseUrl: String = "",
    val apiPath: String = "/v1/collaboration",
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 45_000,
    val requestTimeoutMillis: Long = 60_000,
    val pageSize: Int = 100,
    val retryCount: Int = 3,
    val requireEnterpriseAuth: Boolean = false,
    val allowMeteredNetwork: Boolean = true,
)

@Serializable
data class TenantConfigurationModel(
    val tenantId: String = "personal",
    val tenantName: String = "Personal Workspace",
    val domain: String = "",
    val oidc: OidcProviderConfig = OidcProviderConfig(),
    val collaboration: CollaborationServiceConfig = CollaborationServiceConfig(),
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
    val allowCollaborationSync: Boolean = true,
    val allowExternalSharing: Boolean = true,
    val collaborationScope: CollaborationScope = CollaborationScope.ExternalGuests,
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
    val subjectId: String = "",
    val collaborationCredentialAlias: String? = null,
    val sessionExpiresAtEpochMillis: Long? = null,
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
