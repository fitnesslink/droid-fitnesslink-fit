package com.fitnesslink.fit.ui.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.billing.AppLinks
import com.fitnesslink.fit.billing.BillingException
import com.fitnesslink.fit.billing.BillingPresentation
import com.fitnesslink.fit.billing.EntitlementState
import com.fitnesslink.fit.billing.EntitlementStore
import com.fitnesslink.fit.billing.PlayBillingManager
import com.fitnesslink.fit.network.dto.BillingTerm
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch

/**
 * FTNSSLNK-283 port of iOS `PaywallView`. The gate: nothing in FitnessLink
 * is free, so this is not dismissible — the only ways past are a completed
 * purchase, a successful restore, or signing out.
 *
 * Play's subscription policy requires everything shown here: price and
 * duration, the trial terms when one is offered, a Restore control, and
 * Terms / Privacy links. Removing any of them is a policy rejection.
 */
@Composable
fun PaywallScreen(
    onEntitled: () -> Unit = {},
    onLogout: () -> Unit = { AuthManager.logout() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offers by PlayBillingManager.offers.collectAsState()
    val isLoadingProducts by PlayBillingManager.isLoadingProducts.collectAsState()
    val loadError by PlayBillingManager.loadError.collectAsState()
    val purchaseInFlight by PlayBillingManager.purchaseInFlight.collectAsState()
    val purchaseStalled by PlayBillingManager.purchaseStalled.collectAsState()
    val collided by PlayBillingManager.collidedPurchaseTokens.collectAsState()
    val verificationPending by PlayBillingManager.verificationPendingTokens.collectAsState()
    val entitlementState by EntitlementStore.state.collectAsState()

    var selectedTerm by remember { mutableStateOf(BillingTerm.MONTHLY) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PlayBillingManager.attach(context)
        if (offers.isEmpty()) PlayBillingManager.loadProducts()
    }
    // A purchase verified out-of-band (renewal, stalled sheet answering late)
    // flips the store; let the host move on.
    LaunchedEffect(entitlementState) {
        if (entitlementState is EntitlementState.Entitled) onEntitled()
    }

    val selected = offers.firstOrNull { it.term == selectedTerm } ?: offers.firstOrNull()
    val monthly = offers.firstOrNull { it.term == BillingTerm.MONTHLY }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "FitnessLink",
                modifier = Modifier.width(134.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Everything, unlocked", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Workouts, programs, nutrition and progress tracking — one membership.",
                fontSize = 15.sp,
                color = White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BenefitRow(Icons.Default.FitnessCenter, "Every workout and multi-week program")
                BenefitRow(Icons.Default.Restaurant, "Meal plans, macros and grocery lists")
                BenefitRow(Icons.Default.TrendingUp, "Progress, measurements and reports")
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Plan cards
            when {
                isLoadingProducts && offers.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = White) }

                loadError != null && offers.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(loadError ?: "", fontSize = 14.sp, color = White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Try Again",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FLPrimary,
                        modifier = Modifier.clickable { scope.launch { PlayBillingManager.loadProducts() } }
                    )
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    offers.forEach { offer ->
                        val savings = if (offer.term == BillingTerm.ANNUAL) {
                            BillingPresentation.savingsBadge(monthly?.priceMicros, offer.priceMicros)
                        } else null
                        PlanCard(
                            title = offer.title,
                            priceLine = BillingPresentation.priceLine(offer.formattedPrice, offer.billingPeriod),
                            isSelected = selected?.term == offer.term,
                            savingsBadge = savings,
                            onClick = { selectedTerm = offer.term }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notices
            val entitlementError = (entitlementState as? EntitlementState.Error)?.message
            if (entitlementError != null && errorMessage == null) {
                Notice(entitlementError, Color(0xFFFFB74D))
                Text(
                    "Retry",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clickable { scope.launch { EntitlementStore.refresh(force = true) } }
                )
            }
            if (collided.isNotEmpty() && errorMessage == null) {
                Notice(BillingException.AlreadyLinkedToAnotherAccount.message ?: "", Color(0xFFFFB74D))
            }
            if (verificationPending.isNotEmpty() && errorMessage == null) {
                Notice(BillingException.VerificationUnavailable.message ?: "", Color(0xFFFFB74D))
            }
            if (purchaseStalled && errorMessage == null) {
                Notice(
                    "Google Play didn't respond. Tap to try again — if it keeps happening, close and reopen FitnessLink.",
                    Color(0xFFFFB74D)
                )
            }
            errorMessage?.let { Notice(it, Color(0xFFEF5350)) }

            ActionButtonView(
                text = if (selected?.hasTrial == true) BillingPresentation.trialButtonText(selected.trialPeriod) else "Subscribe",
                isEnabled = selected != null,
                isLoading = purchaseInFlight,
                onClick = {
                    val offer = selected ?: return@ActionButtonView
                    val activity = context.findActivity() ?: run {
                        errorMessage = "Couldn't open Google Play from here. Please try again."
                        return@ActionButtonView
                    }
                    errorMessage = null
                    PlayBillingManager.clearPurchaseStalled()
                    scope.launch {
                        try {
                            val entitlement = PlayBillingManager.purchase(activity, offer)
                            if (entitlement?.isEntitled == true) onEntitled()
                            // null = cancelled / pending / stalled — stay put, say nothing.
                        } catch (e: BillingException.VerificationUnavailable) {
                            // Shown by the verificationPending notice.
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Something went wrong. Please try again."
                        }
                    }
                }
            )

            selected?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    BillingPresentation.disclosure(it.formattedPrice, it.billingPeriod, it.trialPeriod),
                    fontSize = 12.sp,
                    color = White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (isRestoring) {
                CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    "Restore Purchases",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White,
                    modifier = Modifier.clickable {
                        errorMessage = null
                        isRestoring = true
                        scope.launch {
                            try {
                                val entitlement = PlayBillingManager.restorePurchases()
                                if (entitlement.isEntitled) onEntitled()
                                else errorMessage = BillingException.NoActiveSubscription.message
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Restore failed. Please try again."
                            } finally {
                                isRestoring = false
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LinkText("Terms of Use") { context.openUrl(AppLinks.TERMS_OF_USE) }
                LinkText("Privacy Policy") { context.openUrl(AppLinks.PRIVACY_POLICY) }
            }
            Spacer(modifier = Modifier.height(14.dp))
            // The only exit that isn't a purchase — a user on the wrong account has to be able to get out.
            Text(
                "Sign Out",
                fontSize = 13.sp,
                color = White.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onLogout).padding(8.dp)
            )
        }
    }
}

@Composable
private fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Text(text, fontSize = 15.sp, color = White.copy(alpha = 0.9f))
    }
}

@Composable
private fun PlanCard(title: String, priceLine: String, isSelected: Boolean, savingsBadge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(White.copy(alpha = if (isSelected) 0.16f else 0.08f))
            .border(1.5.dp, if (isSelected) FLPrimary else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) FLPrimary else White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
            // Price and duration straight from Play — backend display prices are reporting-only.
            Text(priceLine, fontSize = 13.sp, color = White.copy(alpha = 0.7f))
        }
        if (savingsBadge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FLPrimary)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(savingsBadge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Text(
        text,
        fontSize = 13.sp,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 12.sp, color = White.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClick))
}

/** Walk the ContextWrapper chain to the hosting Activity (needed by `launchBillingFlow`). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}
