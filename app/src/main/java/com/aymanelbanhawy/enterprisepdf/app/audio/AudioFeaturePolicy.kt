package com.aymanelbanhawy.enterprisepdf.app.audio

import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel

data class AudioFeatureCapabilities(
    val aiAvailable: Boolean,
    val cloudAiAllowed: Boolean,
    val speechOutputPolicyEnabled: Boolean,
    val voicePromptCaptureAllowed: Boolean,
    val readAloudAllowed: Boolean,
    val spokenAssistantResponsesAllowed: Boolean,
    val voiceCommentsAllowed: Boolean,
) {
    fun assistantAudioEnabled(): Boolean {
        return voicePromptCaptureAllowed || readAloudAllowed || spokenAssistantResponsesAllowed
    }

    fun assistantAudioReason(): String? {
        return when {
            aiAvailable && assistantAudioEnabled() -> null
            !aiAvailable -> "AI features are disabled by enterprise policy."
            !voicePromptCaptureAllowed && !readAloudAllowed && !spokenAssistantResponsesAllowed -> "Voice input and speech output are disabled by enterprise policy."
            else -> "Audio features are restricted by enterprise policy."
        }
    }

    fun voicePromptReason(): String? {
        return when {
            voicePromptCaptureAllowed -> null
            !aiAvailable -> "AI features are disabled by enterprise policy."
            else -> "Voice input is disabled by enterprise policy."
        }
    }

    fun readAloudReason(): String? {
        return when {
            readAloudAllowed -> null
            !speechOutputPolicyEnabled -> "Speech output is disabled by enterprise policy."
            else -> "Read aloud is disabled in assistant settings."
        }
    }

    fun spokenResponseReason(): String? {
        return when {
            spokenAssistantResponsesAllowed -> null
            !speechOutputPolicyEnabled -> "Speech output is disabled by enterprise policy."
            else -> "Spoken assistant responses are disabled in assistant settings."
        }
    }

    fun voiceCommentReason(): String? {
        return if (voiceCommentsAllowed) null else "Voice comments are disabled by enterprise policy."
    }
}

fun resolveAudioFeatureCapabilities(
    policy: AdminPolicyModel,
    privacySettings: PrivacySettingsModel,
    assistantSettings: AssistantSettings,
): AudioFeatureCapabilities {
    val aiAvailable = policy.aiEnabled
    val audioAvailable = policy.audioFeaturesEnabled
    val cloudAiAllowed = aiAvailable && policy.allowCloudAiProviders && !privacySettings.localOnlyMode
    val voicePromptCaptureAllowed = aiAvailable && audioAvailable && policy.voiceInputEnabled && assistantSettings.voicePromptCaptureEnabled
    val speechOutputAllowed = audioAvailable && policy.speechOutputEnabled
    val readAloudAllowed = speechOutputAllowed && assistantSettings.readAloudEnabled
    val spokenAssistantResponsesAllowed = aiAvailable && speechOutputAllowed && assistantSettings.spokenResponsesEnabled
    val voiceCommentsAllowed = audioAvailable && policy.voiceCommentsEnabled
    return AudioFeatureCapabilities(
        aiAvailable = aiAvailable,
        cloudAiAllowed = cloudAiAllowed,
        speechOutputPolicyEnabled = speechOutputAllowed,
        voicePromptCaptureAllowed = voicePromptCaptureAllowed,
        readAloudAllowed = readAloudAllowed,
        spokenAssistantResponsesAllowed = spokenAssistantResponsesAllowed,
        voiceCommentsAllowed = voiceCommentsAllowed,
    )
}
