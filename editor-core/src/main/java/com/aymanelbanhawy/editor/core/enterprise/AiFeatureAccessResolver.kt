package com.aymanelbanhawy.editor.core.enterprise

data class AiFeatureAccessResolution(
    val entitled: Boolean,
    val policyEnabled: Boolean,
) {
    val available: Boolean
        get() = entitled && policyEnabled

    fun unavailableReason(): String? = when {
        available -> null
        !entitled -> ENTITLEMENT_REQUIRED_MESSAGE
        else -> POLICY_DISABLED_MESSAGE
    }

    fun adminControlMessage(): String = when {
        !entitled -> ENTITLEMENT_REQUIRED_MESSAGE
        else -> "Entitlement grants the feature; admin policy may restrict it."
    }

    companion object {
        const val ENTITLEMENT_REQUIRED_MESSAGE = "AI is not included in the current plan."
        const val POLICY_DISABLED_MESSAGE = "Tenant policy has disabled AI assistance."
    }
}

object AiFeatureAccessResolver {
    fun resolve(
        entitlements: EntitlementStateModel,
        policy: AdminPolicyModel,
    ): AiFeatureAccessResolution {
        return AiFeatureAccessResolution(
            entitled = FeatureFlag.Ai in entitlements.features,
            policyEnabled = policy.aiEnabled,
        )
    }
}
