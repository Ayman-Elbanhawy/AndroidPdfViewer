package com.aymanelbanhawy.editor.core.enterprise

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiFeatureAccessResolverTest {
    @Test
    fun freeWithoutAiEntitlementAndPolicyEnabled_isUnavailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Free, emptySet()),
                policy = AdminPolicyModel(aiEnabled = true),
            ).available,
        ).isFalse()
    }

    @Test
    fun premiumWithoutAiEntitlementAndPolicyEnabled_isUnavailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Premium, setOf(FeatureFlag.Sign)),
                policy = AdminPolicyModel(aiEnabled = true),
            ).available,
        ).isFalse()
    }

    @Test
    fun premiumWithAiEntitlementAndPolicyDisabled_isUnavailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Premium, setOf(FeatureFlag.Ai)),
                policy = AdminPolicyModel(aiEnabled = false),
            ).available,
        ).isFalse()
    }

    @Test
    fun premiumWithAiEntitlementAndPolicyEnabled_isAvailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Premium, setOf(FeatureFlag.Ai)),
                policy = AdminPolicyModel(aiEnabled = true),
            ).available,
        ).isTrue()
    }

    @Test
    fun enterpriseWithoutAiEntitlementAndPolicyEnabled_isUnavailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.AdminConsole)),
                policy = AdminPolicyModel(aiEnabled = true),
            ).available,
        ).isFalse()
    }

    @Test
    fun enterpriseWithAiEntitlementAndPolicyEnabled_isAvailable() {
        assertThat(
            AiFeatureAccessResolver.resolve(
                entitlements = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Ai, FeatureFlag.AdminConsole)),
                policy = AdminPolicyModel(aiEnabled = true),
            ).available,
        ).isTrue()
    }
}
