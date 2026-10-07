package com.fitnesslink.fit.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.BillingTerm
import com.fitnesslink.fit.network.dto.Entitlement
import com.fitnesslink.fit.network.dto.RestorePurchaseRequest
import com.fitnesslink.fit.network.dto.VerifyGooglePurchaseRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Billing failures the paywall / Subscription screen can explain (iOS `BillingError`). */
sealed class BillingException(message: String) : Exception(message) {
    /** Server 409: the Play subscription is linked to a different FitnessLink account. */
    data object AlreadyLinkedToAnotherAccount : BillingException(
        "This subscription is already linked to another FitnessLink account. Sign in with that account, or contact support."
    ) { private fun readResolve(): Any = AlreadyLinkedToAnotherAccount }

    /**
     * The purchase went through on Play but the backend has no Google verify
     * route yet (verify 404 → restore 400). The purchase is left
     * unacknowledged so the next launch retries; access unlocks once the
     * server can confirm it.
     */
    data object VerificationUnavailable : BillingException(
        "Your purchase went through, but FitnessLink couldn't confirm it yet. We'll keep trying — your access unlocks as soon as it's confirmed."
    ) { private fun readResolve(): Any = VerificationUnavailable }

    data object NoActiveSubscription : BillingException("No active subscription was found for this Google account.") {
        private fun readResolve(): Any = NoActiveSubscription
    }

    data class StoreError(val responseCode: Int, val debug: String?) : BillingException(
        "Google Play isn't available right now${if (debug.isNullOrBlank()) "" else " ($debug)"}. Please try again."
    )
}

/**
 * FTNSSLNK-282 port for Google Play (iOS `StoreKitManager`). Everything that
 * touches the Play Billing Library lives here.
 *
 * The contract with the backend, in order, and it matters:
 *   1. `GET billing/customers/me/checkout-context` (ensures the Customer row)
 *   2. `launchBillingFlow` with the user id as `obfuscatedAccountId`
 *   3. `POST billing/subscriptions/google/verify {purchaseToken, productId}`
 *      — does NOT exist on the backend yet: a 404 falls back to
 *      `POST subscriptions/restore {source: Google, purchaseToken}`, which the
 *      backend currently answers 400 ("not yet supported"). Both are treated
 *      as "verification pending".
 *   4. **only then** `acknowledgePurchase`.
 *
 * Acknowledging before the server has recorded the purchase would hand the
 * user a receipt we have no record of, so an unverified purchase is left
 * unacknowledged and retried by [drainPendingPurchases] on every launch.
 * Note Play auto-refunds purchases that stay unacknowledged for 3 days —
 * the backend Google verify route is therefore launch-blocking.
 */
object PlayBillingManager : PurchasesUpdatedListener {
    private const val TAG = "PlayBilling"

    /** Fallbacks when `/plans` hasn't answered — reconciled when plans load. */
    const val MONTHLY_FALLBACK_PRODUCT_ID = "com.fitnesslink.monthly"
    const val ANNUAL_FALLBACK_PRODUCT_ID = "com.fitnesslink.annual"

    /** How long the Play purchase sheet may keep the paywall locked before the UI is released. */
    const val PURCHASE_SHEET_TIMEOUT_MS = 120_000L

    /** One purchasable plan on the paywall (monthly or annual) with the Play offer we'd buy. */
    data class PlanOffer(
        val term: BillingTerm,
        val productId: String,
        val title: String,
        val productDetails: ProductDetails,
        val offerToken: String,
        val formattedPrice: String,
        val priceMicros: Long,
        /** ISO-8601 recurring period, e.g. "P1M". */
        val billingPeriod: String,
        /** ISO-8601 free-trial period when this offer starts with a free phase, else null. Play only returns offers the user is eligible for. */
        val trialPeriod: String?
    ) {
        val hasTrial: Boolean get() = trialPeriod != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var client: BillingClient? = null
    private var connection: CompletableDeferred<BillingClient>? = null

    private var productIds: Map<BillingTerm, String> = mapOf(
        BillingTerm.MONTHLY to MONTHLY_FALLBACK_PRODUCT_ID,
        BillingTerm.ANNUAL to ANNUAL_FALLBACK_PRODUCT_ID
    )

    private sealed class PurchaseOutcome {
        data class Purchased(val purchases: List<Purchase>) : PurchaseOutcome()
        data object Cancelled : PurchaseOutcome()
        data object AlreadyOwned : PurchaseOutcome()
        data class Failed(val result: BillingResult) : PurchaseOutcome()
    }

    private var pendingPurchase: CompletableDeferred<PurchaseOutcome>? = null
    /** One backend verify per purchase token at a time (the flow result and the pending drain can both see a purchase). */
    private val inFlightVerifies = mutableMapOf<String, Deferred<Entitlement>>()

    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    val offers: StateFlow<List<PlanOffer>> = _offers.asStateFlow()
    private val _isLoadingProducts = MutableStateFlow(false)
    val isLoadingProducts: StateFlow<Boolean> = _isLoadingProducts.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    private val _purchaseInFlight = MutableStateFlow(false)
    val purchaseInFlight: StateFlow<Boolean> = _purchaseInFlight.asStateFlow()
    /** Set when the watchdog released the paywall because Play never answered. */
    private val _purchaseStalled = MutableStateFlow(false)
    val purchaseStalled: StateFlow<Boolean> = _purchaseStalled.asStateFlow()
    /** Purchase tokens the server refused as owned by another account (409). */
    private val _collidedPurchaseTokens = MutableStateFlow<Set<String>>(emptySet())
    val collidedPurchaseTokens: StateFlow<Set<String>> = _collidedPurchaseTokens.asStateFlow()
    /** Purchases Play completed that the backend cannot verify yet (missing Google route). */
    private val _verificationPendingTokens = MutableStateFlow<Set<String>>(emptySet())
    val verificationPendingTokens: StateFlow<Set<String>> = _verificationPendingTokens.asStateFlow()

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun offer(term: BillingTerm): PlanOffer? = _offers.value.firstOrNull { it.term == term }
    fun offer(productId: String): PlanOffer? = _offers.value.firstOrNull { it.productId == productId }

    // MARK: - Connection

    private fun buildClient(context: Context): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    private suspend fun ensureConnected(): BillingClient {
        client?.takeIf { it.isReady }?.let { return it }
        connection?.let { if (it.isActive) return it.await() }
        val context = appContext ?: throw BillingException.StoreError(BillingClient.BillingResponseCode.DEVELOPER_ERROR, "billing not attached")
        val c = client ?: buildClient(context).also { client = it }
        val deferred = CompletableDeferred<BillingClient>()
        connection = deferred
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) deferred.complete(c)
                else deferred.completeExceptionally(BillingException.StoreError(result.responseCode, result.debugMessage))
            }

            override fun onBillingServiceDisconnected() {
                connection = null
            }
        })
        return deferred.await()
    }

    // MARK: - Products

    /** Plans from the backend (for product ids), then Play product details. Monthly first. */
    suspend fun loadProducts() {
        if (_isLoadingProducts.value) return
        _isLoadingProducts.value = true
        try {
            try {
                val plans = ApiClient.billingApi.getPlans()
                val mapped = BillingTerm.entries.mapNotNull { term ->
                    plans.firstNotNullOfOrNull { it.googleProductId(term) }?.let { term to it }
                }.toMap()
                if (mapped.isNotEmpty()) productIds = productIds + mapped
            } catch (e: Exception) {
                Log.w(TAG, "plans unavailable, using pinned product ids: ${e.message}")
            }

            val billing = ensureConnected()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.values.distinct().map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
                )
                .build()
            val result = billing.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw BillingException.StoreError(result.billingResult.responseCode, result.billingResult.debugMessage)
            }
            val details = result.productDetailsList.orEmpty()
            val loaded = BillingTerm.entries.mapNotNull { term ->
                val id = productIds[term] ?: return@mapNotNull null
                details.firstOrNull { it.productId == id }?.let { offerFor(term, it) }
            }
            _offers.value = loaded
            _loadError.value = if (loaded.isEmpty()) "Subscriptions aren't available right now. Please try again." else null
        } catch (e: Exception) {
            _loadError.value = e.message ?: "Subscriptions aren't available right now. Please try again."
        } finally {
            _isLoadingProducts.value = false
        }
    }

    /**
     * Pick the offer to buy: the one with a free-trial phase if Play returned
     * one (Play filters out offers the account isn't eligible for), else the
     * base plan, else the first.
     */
    private fun offerFor(term: BillingTerm, details: ProductDetails): PlanOffer? {
        val offers = details.subscriptionOfferDetails ?: return null
        val chosen = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
            ?: return null
        val phases = chosen.pricingPhases.pricingPhaseList
        val paid = phases.firstOrNull { it.priceAmountMicros > 0 } ?: return null
        val free = phases.firstOrNull { it.priceAmountMicros == 0L }
        val title = when (term) {
            BillingTerm.MONTHLY -> "Monthly"
            BillingTerm.ANNUAL -> "Annual"
        }
        return PlanOffer(
            term = term,
            productId = details.productId,
            title = title,
            productDetails = details,
            offerToken = chosen.offerToken,
            formattedPrice = paid.formattedPrice,
            priceMicros = paid.priceAmountMicros,
            billingPeriod = paid.billingPeriod,
            trialPeriod = free?.billingPeriod
        )
    }

    // MARK: - Purchase

    /**
     * Buys [offer] and returns the entitlement the server computed afterwards.
     * Null when the user cancelled, the purchase is pending (slow payment
     * method), or the Play sheet never answered (watchdog) — none is an error.
     */
    suspend fun purchase(activity: Activity, offer: PlanOffer): Entitlement? {
        if (_purchaseInFlight.value) return null
        _purchaseInFlight.value = true
        _purchaseStalled.value = false
        try {
            // Before the sheet on purpose: ensures the backend Customer row exists.
            try { ApiClient.billingApi.getCheckoutContext() } catch (e: ApiError.Offline) { throw e }
            val accountId = AuthManager.currentUser.value?.id?.toString()
            val billing = ensureConnected()

            val deferred = CompletableDeferred<PurchaseOutcome>()
            pendingPurchase = deferred
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(offer.productDetails)
                            .setOfferToken(offer.offerToken)
                            .build()
                    )
                )
                .apply { if (accountId != null) setObfuscatedAccountId(accountId) }
                .build()
            val launch = billing.launchBillingFlow(activity, params)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                pendingPurchase = null
                if (launch.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) return restorePurchases()
                throw BillingException.StoreError(launch.responseCode, launch.debugMessage)
            }

            val outcome = withTimeoutOrNull(PURCHASE_SHEET_TIMEOUT_MS) { deferred.await() }
            if (outcome == null) {
                // Released by the watchdog. Whatever Play eventually delivers
                // still reaches onPurchasesUpdated → handle() and is verified.
                _purchaseStalled.value = true
                return null
            }
            return when (outcome) {
                PurchaseOutcome.Cancelled -> null
                PurchaseOutcome.AlreadyOwned -> restorePurchases()
                is PurchaseOutcome.Failed -> throw BillingException.StoreError(outcome.result.responseCode, outcome.result.debugMessage)
                is PurchaseOutcome.Purchased -> {
                    val purchase = outcome.purchases.firstOrNull { offer.productId in it.products } ?: outcome.purchases.firstOrNull()
                    when {
                        purchase == null -> null
                        purchase.purchaseState == Purchase.PurchaseState.PENDING -> null
                        else -> verifyAndAcknowledge(purchase)
                    }
                }
            }
        } finally {
            pendingPurchase = null
            _purchaseInFlight.value = false
        }
    }

    /** Dismisses the stalled notice once the user acts again. */
    fun clearPurchaseStalled() { _purchaseStalled.value = false }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val waiting = pendingPurchase
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                if (waiting != null && waiting.isActive) {
                    waiting.complete(PurchaseOutcome.Purchased(list))
                } else {
                    // Renewal / delayed approval / stalled sheet finally answering.
                    list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                        scope.launch { handle(p) }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> waiting?.complete(PurchaseOutcome.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> waiting?.complete(PurchaseOutcome.AlreadyOwned)
            else -> waiting?.complete(PurchaseOutcome.Failed(result))
        }
    }

    /** A purchase arriving outside a flow we're awaiting. Transient failures leave it for the next drain. */
    private suspend fun handle(purchase: Purchase) {
        try {
            verifyAndAcknowledge(purchase)
        } catch (_: BillingException.AlreadyLinkedToAnotherAccount) {
            _collidedPurchaseTokens.value = _collidedPurchaseTokens.value + purchase.purchaseToken
        } catch (_: BillingException.VerificationUnavailable) {
            // recorded in verificationPendingTokens by verifyAndAcknowledge
        } catch (e: Exception) {
            Log.w(TAG, "deferred verify failed (will retry next launch): ${e.message}")
        }
    }

    /**
     * Server verify → acknowledge → adopt entitlement. Throws
     * [BillingException.AlreadyLinkedToAnotherAccount] on 409,
     * [BillingException.VerificationUnavailable] while the backend lacks the
     * Google route, and rethrows transient errors (offline, 5xx).
     */
    private suspend fun verifyAndAcknowledge(purchase: Purchase): Entitlement {
        val token = purchase.purchaseToken
        inFlightVerifies[token]?.let { return it.await() }
        val task = scope.async {
            val entitlement = verifyOnServer(purchase)
            if (!purchase.isAcknowledged) {
                val billing = ensureConnected()
                val ack = billing.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
                )
                if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledge failed (${ack.responseCode}); Play will re-deliver")
                }
            }
            _verificationPendingTokens.value = _verificationPendingTokens.value - token
            EntitlementStore.apply(entitlement)
            entitlement
        }
        inFlightVerifies[token] = task
        try {
            return task.await()
        } finally {
            inFlightVerifies.remove(token)
        }
    }

    private suspend fun verifyOnServer(purchase: Purchase): Entitlement {
        val token = purchase.purchaseToken
        val productId = purchase.products.firstOrNull().orEmpty()
        val packageName = appContext?.packageName
        try {
            return ApiClient.billingApi.verifyGoogle(VerifyGooglePurchaseRequest(token, productId, packageName))
        } catch (e: ApiError.NotFound) {
            // No Google verify route on this backend yet — fall back to restore.
            Log.w(TAG, "google/verify is 404 on this backend; falling back to subscriptions/restore")
        } catch (e: ApiError.ServerError) {
            if (e.isConflict) throw BillingException.AlreadyLinkedToAnotherAccount
            throw e
        }
        try {
            return ApiClient.billingApi.restore(RestorePurchaseRequest.google(token))
        } catch (e: ApiError.ServerError) {
            if (e.isConflict) throw BillingException.AlreadyLinkedToAnotherAccount
            if (e.isBadRequest) {
                // "Restore is not yet supported for source 'Google'." — backend gap.
                _verificationPendingTokens.value = _verificationPendingTokens.value + token
                throw BillingException.VerificationUnavailable
            }
            throw e
        } catch (e: ApiError.NotFound) {
            _verificationPendingTokens.value = _verificationPendingTokens.value + token
            throw BillingException.VerificationUnavailable
        }
    }

    // MARK: - Restore / drain / re-verify

    /** Re-links whatever this Google account owns onto the signed-in FitnessLink account. */
    suspend fun restorePurchases(): Entitlement {
        val purchases = ownedSubscriptions()
        var latest: Entitlement? = null
        var collision: BillingException? = null
        var pending: BillingException? = null
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            try {
                latest = verifyAndAcknowledge(purchase)
            } catch (e: BillingException.AlreadyLinkedToAnotherAccount) {
                collision = e
            } catch (e: BillingException.VerificationUnavailable) {
                pending = e
            }
        }
        latest?.let { return it }
        collision?.let { throw it }
        pending?.let { throw it }
        // Nothing to restore is not a failure — report the server's current view.
        return ApiClient.billingApi.getEntitlement().also { EntitlementStore.apply(it) }
    }

    /** Purchases stranded between purchase and verify (crash, dead network, kill). Runs at launch. */
    suspend fun drainPendingPurchases() {
        val purchases = try { ownedSubscriptions() } catch (e: Exception) { Log.w(TAG, "drain skipped: ${e.message}"); return }
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { handle(it) }
    }

    /** Re-offer every owned subscription to the server — run when the cached entitlement is about to lapse. */
    suspend fun reverifyCurrentPurchases() {
        val purchases = try { ownedSubscriptions() } catch (e: Exception) { return }
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { handle(it) }
    }

    private suspend fun ownedSubscriptions(): List<Purchase> {
        val billing = ensureConnected()
        val result = billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingException.StoreError(result.billingResult.responseCode, result.billingResult.debugMessage)
        }
        val ours = productIds.values.toSet()
        return result.purchasesList.filter { p -> p.products.any { it in ours } }
    }

    // MARK: - Manage

    /** Play's subscription centre, scoped to our subscription when we know which one. */
    fun openManageSubscriptions(context: Context, productId: String? = null) {
        val url = AppLinks.playSubscriptions(productId, context.packageName)
        val play = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.vending")
        try {
            if (play.resolveActivity(context.packageManager) != null) context.startActivity(play)
            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.w(TAG, "manage subscriptions failed: ${e.message}")
        }
    }

    /** Clears per-account purchase state on logout (the client connection outlives accounts). */
    fun reset() {
        pendingPurchase?.complete(PurchaseOutcome.Cancelled)
        pendingPurchase = null
        _purchaseInFlight.value = false
        _purchaseStalled.value = false
        _collidedPurchaseTokens.value = emptySet()
        _verificationPendingTokens.value = emptySet()
    }
}
