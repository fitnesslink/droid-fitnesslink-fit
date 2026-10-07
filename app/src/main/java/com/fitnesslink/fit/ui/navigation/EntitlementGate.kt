package com.fitnesslink.fit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.billing.EntitlementState
import com.fitnesslink.fit.billing.EntitlementStore
import com.fitnesslink.fit.billing.PlayBillingManager
import com.fitnesslink.fit.ui.auth.SplashScreen
import com.fitnesslink.fit.ui.billing.PaywallScreen
import kotlinx.coroutines.launch

/**
 * Root gate between auth and the personalization / main tabs (iOS
 * `FitnessLinkApp`): splash while the entitlement resolves, PaywallScreen
 * when not entitled (or when the server can't be reached and nothing cached
 * is trustworthy — fail closed), content otherwise.
 *
 * Appearing means the user just authenticated (or the session restored), so
 * this is also the "refresh on login" hook, the pending-purchase drain, and
 * the near-expiry re-verification from iOS `StoreKitManager`.
 */
@Composable
fun EntitlementGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val state by EntitlementStore.state.collectAsState()

    LaunchedEffect(Unit) {
        EntitlementStore.attach(context)
        PlayBillingManager.attach(context)
        launch { EntitlementStore.refresh(force = true) }
        launch {
            PlayBillingManager.drainPendingPurchases()
            if (EntitlementStore.isNearExpiry) PlayBillingManager.reverifyCurrentPurchases()
        }
    }

    when (state) {
        is EntitlementState.Resolving -> SplashScreen()
        is EntitlementState.Entitled -> content()
        is EntitlementState.NotEntitled, is EntitlementState.Error -> PaywallScreen(
            onEntitled = { /* the store already flipped; recomposition shows content */ },
            onLogout = { AuthManager.logout() }
        )
    }
}
