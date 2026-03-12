package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import com.aymanelbanhawy.editor.core.migration.AiAssistantMigrationSummary
import kotlinx.serialization.json.Json

object AiAssistantMigrationSupport {
    suspend fun normalizePersistedState(context: Context): AiAssistantMigrationSummary {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val database = newAiAssistantDatabase(context)
        val store = RoomAiProviderSettingsStore(database.providerSettingsDao(), json)
        val current = store.load()
        val normalizedProfiles = current.profiles
            .ifEmpty { defaultProviderProfiles() }
            .distinctBy { it.id }
            .map { profile ->
                profile.copy(
                    displayName = profile.displayName.ifBlank { profile.kind.name },
                    endpointUrl = profile.endpointUrl.trim(),
                    modelId = profile.modelId.trim(),
                    requestTimeoutSeconds = profile.requestTimeoutSeconds.coerceIn(15, 300),
                    retryCount = profile.retryCount.coerceIn(0, 4),
                )
            }
        val selectedProviderId = normalizedProfiles.firstOrNull { it.id == current.selectedProviderId }?.id
            ?: normalizedProfiles.firstOrNull()?.id
            ?: DEFAULT_PROVIDER_ID
        val normalized = current.copy(
            selectedProviderId = selectedProviderId,
            profiles = normalizedProfiles,
        )
        val changed = normalized != current
        if (changed) {
            store.save(normalized)
        }
        return AiAssistantMigrationSummary(
            normalizedProfileCount = if (changed) normalizedProfiles.size else 0,
            message = if (changed) {
                "Normalized ${normalizedProfiles.size} AI provider profile(s) and preserved provider selection."
            } else {
                "AI assistant settings already matched the current schema."
            },
        )
    }
}
