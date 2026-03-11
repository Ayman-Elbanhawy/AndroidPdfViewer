package com.aymanelbanhawy.editor.core.enterprise

import android.content.Context
import com.aymanelbanhawy.editor.core.data.EnterpriseSettingsDao
import com.aymanelbanhawy.editor.core.data.EnterpriseSettingsEntity
import com.aymanelbanhawy.editor.core.data.TelemetryEventDao
import com.aymanelbanhawy.editor.core.data.TelemetryEventEntity
import java.io.File
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface EnterpriseAdminRepository {
    suspend fun loadState(): EnterpriseAdminStateModel
    suspend fun saveState(state: EnterpriseAdminStateModel)
    suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel
    suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel
    suspend fun signOut(): EnterpriseAdminStateModel
    suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel
    suspend fun queueTelemetry(event: TelemetryEventModel)
    suspend fun pendingTelemetry(): List<TelemetryEventModel>
    suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File
}

class DefaultEnterpriseAdminRepository(
    private val context: Context,
    private val settingsDao: EnterpriseSettingsDao,
    private val telemetryDao: TelemetryEventDao,
    private val json: Json,
) : EnterpriseAdminRepository {

    override suspend fun loadState(): EnterpriseAdminStateModel {
        return settingsDao.get()?.let { json.decodeFromString(EnterpriseAdminStateModel.serializer(), it.payloadJson) }
            ?: EnterpriseAdminStateModel()
    }

    override suspend fun saveState(state: EnterpriseAdminStateModel) {
        settingsDao.upsert(
            EnterpriseSettingsEntity(
                singletonId = SINGLETON_ID,
                payloadJson = json.encodeToString(EnterpriseAdminStateModel.serializer(), state),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel {
        val updated = loadState().copy(
            authSession = AuthSessionModel(
                mode = AuthenticationMode.Personal,
                provider = AuthenticationProvider.Local,
                isSignedIn = true,
                displayName = displayName.ifBlank { "Personal User" },
            ),
            tenantConfiguration = TenantConfigurationModel(),
        )
        saveState(updated)
        return updated
    }

    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel {
        val updated = loadState().copy(
            authSession = AuthSessionModel(
                mode = AuthenticationMode.Enterprise,
                provider = AuthenticationProvider.Oidc,
                isSignedIn = true,
                displayName = email.substringBefore('@').ifBlank { tenant.tenantName },
                email = email,
            ),
            tenantConfiguration = tenant,
        )
        saveState(updated)
        return updated
    }

    override suspend fun signOut(): EnterpriseAdminStateModel {
        val updated = loadState().copy(authSession = AuthSessionModel())
        saveState(updated)
        return updated
    }

    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel {
        return EntitlementEngine.resolve(state.plan, state.adminPolicy)
    }

    override suspend fun queueTelemetry(event: TelemetryEventModel) {
        val state = loadState()
        if (!state.privacySettings.telemetryEnabled) return
        val properties = if (state.privacySettings.includeDocumentNames) event.properties else event.properties.filterKeys { !it.contains("document", ignoreCase = true) }
        telemetryDao.upsert(
            TelemetryEventEntity(
                id = event.id,
                category = event.category.name,
                name = event.name,
                createdAtEpochMillis = event.createdAtEpochMillis,
                propertiesJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), properties),
            ),
        )
    }

    override suspend fun pendingTelemetry(): List<TelemetryEventModel> {
        return telemetryDao.all().map {
            TelemetryEventModel(
                id = it.id,
                category = TelemetryCategory.valueOf(it.category),
                name = it.name,
                createdAtEpochMillis = it.createdAtEpochMillis,
                properties = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it.propertiesJson),
            )
        }
    }

    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File {
        destination.parentFile?.mkdirs()
        val state = loadState()
        val payload = mapOf(
            "generatedAt" to System.currentTimeMillis().toString(),
            "authMode" to state.authSession.mode.name,
            "plan" to state.plan.name,
            "tenant" to state.tenantConfiguration.tenantName,
            "telemetryEnabled" to state.privacySettings.telemetryEnabled.toString(),
            "summary" to appSummary.entries.joinToString(";") { "${it.key}=${it.value}" },
            "queuedTelemetryCount" to pendingTelemetry().size.toString(),
        )
        destination.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), payload))
        return destination
    }

    companion object {
        private const val SINGLETON_ID = "enterprise-admin"
    }
}

fun newTelemetryEvent(category: TelemetryCategory, name: String, properties: Map<String, String> = emptyMap()): TelemetryEventModel {
    return TelemetryEventModel(
        id = UUID.randomUUID().toString(),
        category = category,
        name = name,
        createdAtEpochMillis = System.currentTimeMillis(),
        properties = properties,
    )
}

