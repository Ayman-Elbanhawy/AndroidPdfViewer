package com.aymanelbanhawy.editor.core.enterprise

object EntitlementEngine {
    fun resolve(plan: LicensePlan, policy: AdminPolicyModel): EntitlementStateModel {
        val features = when (plan) {
            LicensePlan.Free -> setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Search,
            )
            LicensePlan.Premium -> setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
            )
            LicensePlan.Enterprise -> FeatureFlag.entries.toMutableSet()
        }.toMutableSet()
        if (!policy.aiEnabled) {
            features.remove(FeatureFlag.Ai)
        }
        if (policy.allowedCloudConnectors == listOf(CloudConnector.LocalFiles)) {
            features.remove(FeatureFlag.CloudConnectors)
        }
        return EntitlementStateModel(plan = plan, features = features)
    }
}

object PolicyEngine {
    fun exportAllowed(state: EnterpriseAdminStateModel): Boolean = !state.adminPolicy.restrictExport

    fun allowedConnectors(state: EnterpriseAdminStateModel): List<CloudConnector> = state.adminPolicy.allowedCloudConnectors

    fun watermarkFor(state: EnterpriseAdminStateModel): String? = state.adminPolicy.forcedWatermarkText.takeIf { it.isNotBlank() }
}
