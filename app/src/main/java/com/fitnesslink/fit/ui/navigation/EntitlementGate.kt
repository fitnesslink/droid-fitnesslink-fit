package com.fitnesslink.fit.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Root gate between auth and the personalization / main tabs (iOS `FitnessLinkApp`:
 * splash while the entitlement resolves, then PaywallView when not entitled).
 * Wave 4.5 replaces this pass-through with EntitlementStore-driven gating.
 */
@Composable
fun EntitlementGate(content: @Composable () -> Unit) {
    content()
}
