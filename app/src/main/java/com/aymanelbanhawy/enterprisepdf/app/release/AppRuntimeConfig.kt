package com.aymanelbanhawy.enterprisepdf.app.release

import android.content.Context
import android.content.RestrictionsManager
import com.aymanelbanhawy.enterprisepdf.app.BuildConfig

data class ManagedAppRestrictions(
    val tenantBaseUrl: String? = null,
    val aiBaseUrl: String? = null,
    val collaborationBaseUrl: String? = null,
    val disableCloudAi: Boolean = false,
    val forceWatermark: String? = null,
    val disableExternalSharing: Boolean = false,
    val secureLogging: Boolean = true,
)

data class AppRuntimeConfig(
    val environment: String,
    val tenantBaseUrl: String,
    val aiBaseUrl: String,
    val collaborationBaseUrl: String,
    val aiCloudEnabledByDefault: Boolean,
    val secureLoggingEnabled: Boolean,
    val defaultExportWatermark: String,
    val certificatePins: List<String>,
    val managedRestrictions: ManagedAppRestrictions,
) {
    val externalSharingAllowed: Boolean
        get() = !managedRestrictions.disableExternalSharing

    val effectiveWatermark: String
        get() = managedRestrictions.forceWatermark?.takeIf { it.isNotBlank() } ?: defaultExportWatermark

    val effectiveCloudAiEnabled: Boolean
        get() = aiCloudEnabledByDefault && !managedRestrictions.disableCloudAi
}

object AppRuntimeConfigLoader {
    fun load(context: Context): AppRuntimeConfig {
        val managedRestrictions = loadManagedRestrictions(context)
        return AppRuntimeConfig(
            environment = BuildConfig.APP_ENVIRONMENT,
            tenantBaseUrl = managedRestrictions.tenantBaseUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.TENANT_BASE_URL,
            aiBaseUrl = managedRestrictions.aiBaseUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.AI_BASE_URL,
            collaborationBaseUrl = managedRestrictions.collaborationBaseUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.COLLABORATION_BASE_URL,
            aiCloudEnabledByDefault = BuildConfig.AI_CLOUD_DEFAULT_ENABLED,
            secureLoggingEnabled = BuildConfig.SECURE_LOGGING_ENABLED && managedRestrictions.secureLogging,
            defaultExportWatermark = BuildConfig.DEFAULT_EXPORT_WATERMARK,
            certificatePins = BuildConfig.CERTIFICATE_PIN_SET.split(',').mapNotNull { pin -> pin.trim().takeIf { it.isNotBlank() } },
            managedRestrictions = managedRestrictions,
        )
    }

    private fun loadManagedRestrictions(context: Context): ManagedAppRestrictions {
        if (!BuildConfig.ENABLE_MANAGED_CONFIG) {
            return ManagedAppRestrictions()
        }
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return ManagedAppRestrictions()
        val restrictions = restrictionsManager.applicationRestrictions
        return ManagedAppRestrictions(
            tenantBaseUrl = restrictions.getString("managed_tenant_base_url"),
            aiBaseUrl = restrictions.getString("managed_ai_base_url"),
            collaborationBaseUrl = restrictions.getString("managed_collaboration_base_url"),
            disableCloudAi = restrictions.getBoolean("managed_disable_cloud_ai", false),
            forceWatermark = restrictions.getString("managed_force_watermark"),
            disableExternalSharing = restrictions.getBoolean("managed_disable_external_sharing", false),
            secureLogging = restrictions.getBoolean("managed_secure_logging", true),
        )
    }
}

