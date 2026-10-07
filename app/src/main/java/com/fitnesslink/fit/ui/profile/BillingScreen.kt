package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.billing.AppLinks
import com.fitnesslink.fit.billing.BillingException
import com.fitnesslink.fit.billing.BillingPresentation
import com.fitnesslink.fit.billing.EntitlementStore
import com.fitnesslink.fit.billing.PlayBillingManager
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.SubscriptionDto
import com.fitnesslink.fit.ui.billing.openUrl
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FTNSSLNK-284 port of iOS `BillingView`: subscription status and the
 * controls each store allows. Play requires cancel / plan changes for a
 * Play subscription to go through its own subscription centre; a Stripe
 * (web) subscription can't be touched from here at all, so that case is
 * read-only with a pointer to the web portal; comped access has nothing to
 * manage.
 */
@Composable
fun BillingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entitlementState by EntitlementStore.state.collectAsState()
    val entitlement = entitlementState.entitlement
    var subscription by remember { mutableStateOf<SubscriptionDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRestoring by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateText: (Date) -> String = { dateFormat.format(it) }

    suspend fun load() {
        isLoading = true
        EntitlementStore.attach(context)
        EntitlementStore.refresh(force = true)
        // 404 (never subscribed) is nil, not an error.
        subscription = try { ApiClient.billingApi.getMySubscription() } catch (_: ApiError.NotFound) { null } catch (_: Exception) { subscription }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Subscription", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(top = 8.dp))

            if (isLoading && subscription == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FLPrimary)
                }
            } else {
                val pill = BillingPresentation.statusPill(entitlement, subscription)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            BillingPresentation.planName(entitlement, subscription),
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(pill)
                    }
                    BillingPresentation.renewalLine(entitlement, subscription, dateText)?.let {
                        InfoLine(Icons.Default.CalendarToday, it)
                    }
                    if (entitlement.isTrial) {
                        entitlement.trialEndsAt?.let {
                            InfoLine(Icons.Default.Schedule, "Your free trial ends ${dateText(it)} — you'll be charged then unless you cancel.")
                        }
                    }
                    entitlement.source?.let {
                        InfoLine(Icons.Default.CreditCard, "Billed through ${BillingPresentation.billingSourceName(it)}")
                    }
                }

                if (entitlement.isGrace) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(OrangeTheme.copy(alpha = 0.12f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeTheme)
                        Text("There's a problem with your payment method. Update it to keep your access.", fontSize = 13.sp, color = TextPrimaryColor)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White)) {
                    when (BillingPresentation.actions(entitlement)) {
                        BillingPresentation.Actions.COMPED -> Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = FLPrimary)
                            Text("Your access is on us — there's nothing to manage or pay.", fontSize = 13.sp, color = TextSecondaryColor)
                        }
                        BillingPresentation.Actions.WEB -> ActionRow("Manage on the web", Icons.Default.Language) {
                            context.openUrl(AppLinks.WEB_BILLING_PORTAL)
                        }
                        BillingPresentation.Actions.APPLE -> {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PhoneIphone, contentDescription = null, tint = FLPrimary)
                                Text("This subscription is billed through the App Store. Manage it from your iPhone or iPad.", fontSize = 13.sp, color = TextSecondaryColor)
                            }
                            SeparatorView(modifier = Modifier.padding(horizontal = 20.dp))
                            RestoreRow(isRestoring) {
                                restore(scope, onStart = { isRestoring = true; message = null }, onDone = { m -> isRestoring = false; message = m }) {
                                    subscription = try { ApiClient.billingApi.getMySubscription() } catch (_: Exception) { subscription }
                                }
                            }
                        }
                        BillingPresentation.Actions.PLAY -> {
                            ActionRow("Manage subscription", Icons.Default.Settings) {
                                message = null
                                PlayBillingManager.attach(context)
                                PlayBillingManager.openManageSubscriptions(context, subscription?.externalSubscriptionId?.takeIf { it.isNotBlank() }?.let { PlayBillingManager.offer(it)?.productId })
                            }
                            SeparatorView(modifier = Modifier.padding(horizontal = 20.dp))
                            RestoreRow(isRestoring) {
                                PlayBillingManager.attach(context)
                                restore(scope, onStart = { isRestoring = true; message = null }, onDone = { m -> isRestoring = false; message = m }) {
                                    subscription = try { ApiClient.billingApi.getMySubscription() } catch (_: Exception) { subscription }
                                }
                            }
                        }
                    }
                }

                message?.let {
                    Text(it, fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}

private fun restore(
    scope: kotlinx.coroutines.CoroutineScope,
    onStart: () -> Unit,
    onDone: (String?) -> Unit,
    reloadSubscription: suspend () -> Unit
) {
    onStart()
    scope.launch {
        val result = try {
            val restored = PlayBillingManager.restorePurchases()
            reloadSubscription()
            if (restored.isEntitled) "Your subscription has been restored." else BillingException.NoActiveSubscription.message
        } catch (e: Exception) {
            e.message
        }
        onDone(result)
    }
}

@Composable
private fun StatusPill(pill: BillingPresentation.StatusPill) {
    val bg = when (pill.tone) {
        BillingPresentation.PillTone.BRAND -> FLPrimary
        BillingPresentation.PillTone.WARNING -> OrangeTheme
        BillingPresentation.PillTone.NEUTRAL -> MediumGray
    }
    Box(modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(pill.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 14.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, fontSize = 16.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RestoreRow(isRestoring: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isRestoring, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(if (isRestoring) "Restoring…" else "Restore Purchases", fontSize = 16.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        if (isRestoring) CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(16.dp))
        else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
    }
}
