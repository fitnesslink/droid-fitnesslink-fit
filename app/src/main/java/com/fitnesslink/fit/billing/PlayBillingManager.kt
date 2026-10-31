package com.fitnesslink.fit.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import com.fitnesslink.fit.network.dto.BillingTerm
import com.fitnesslink.fit.network.dto.CheckoutContext
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FTNSSLNK-282 / 524 port for Google Play (iOS `StoreKitManager`). Everything
 * that touches the Play Billing Library lives here; the pure rules (offer
 * selection, response-code copy, server outcome mapping) are in
 * [PlayBillingRules].
 *
 * The contract with the backend, in order:
 *   1. `GET billing/customers/me/checkout-context` — ensures the Customer
 *      row, says whether this FitnessLink account may still take a free
 *      trial (`trialEligible`), and hands back `googleObfuscatedAccountId`.
 *   2. `launchBillingFlow` with exactly that `obfuscatedAccountId`.
 *   3. `POST billing/subscriptions/google/verify {purchaseToken, productId, packageName}`.
 *      The server links and acknowledges the purchase itself.
 *   4. Client `acknowledgePurchase` only as a fallback, only after a 200
 *      that isn't `pending` — never after 403 / 409 / 422 / 503.
 *
 * Anything stranded between 2 and 3 (crash, dead network, 503) stays
 * unacknowledged and is retried by [drainPendingPurchases] at launch and on
 * every foreground. Play auto-refunds after 3 days unacknowledged.
 */
object PlayBillingManager : PurchasesUpdatedListener {
    private const val TAG = "PlayBilling"

    /** Fallbacks when `/plans` hasn't answered — reconciled when plans load. */
    const val MONTHLY_FALLBACK_PRODUCT_ID = "com.fitnesslink.monthly"
    const val ANNUAL_FALLBACK_PRODUCT_ID = "com.fitnesslink.annual"

    /** How long the Play purchase sheet may keep the paywall locked before the UI is released. */
    const val PURCHASE_SHEET_TIMEOUT_MS = 120_000L

    /** Foreground drains closer together than this are skipped. */
    private const val MIN_DRAIN_INTERVAL_MS = 30_000L

    /** One purchasable plan on the paywall (monthly or annual) with the Play offer we'd buy. */
    data class PlanOffer(
        val term: BillingTerm,
        val productId: String,
        val title: String,
        val productDetails: ProductDetails,
        val offerToken: String,
        /** Localized recurring price straight from Play's `pricingPhases`. */
        val formattedPrice: String,
        val priceMicros: Long,
        /** ISO-8601 recurring period, e.g. "P1M". */
        val billingPeriod: String,
        /** ISO-8601 free-trial period when this is the trial offer, else null. */
        val trialPeriod: String?
    ) {
        val hasTrial: Boolean get() = trialPeriod != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var client: BillingClient? = null
    private var connection: CompletableDeferred<BillingClient>? = null
    private var lifecycleHooked = false

    private var productIds: Map<BillingTerm, String> = mapOf(
        BillingTerm.MONTHLY to MONTHLY_FALLBACK_PRODUCT_ID,
        BillingTerm.ANNUAL to ANNUAL_FALLBACK_PRODUCT_ID
    )
    private var trialOfferIds: Map<BillingTerm, String> = emptyMap()
    private var productDetailsCache: Map<BillingTerm, ProductDetails> = emptyMap()
    @Volatile private var checkoutContext: CheckoutContext? = null

    private sealed class PurchaseOutcome {
        data class Purchased(val purchases: List<Purchase>) : PurchaseOutcome()
        data object Cancelled : PurchaseOutcome()
        data object AlreadyOwned : PurchaseOutcome()
        data class Failed(val result: BillingResult) : PurchaseOutcome()
    }

    private var pendingPurchase: CompletableDeferred<PurchaseOutcome>? = null
    /** One backend verify per purchase token at a time (the flow result and the drain can both see a purchase). */
    private val inFlightVerifies = mutableMapOf<String, Deferred<Entitlement>>()
    private val drainMutex = Mutex()
    @Volatile private var lastDrainAtMs = 0L
    /** Tokens the server refused terminally (403 / 409 / 422) this process — the drain stops re-sending them. */
    private val terminalTokens = mutableSetOf<String>()

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
    /** Purchase tokens the server refused as linked to another account (409). Left unacknowledged. */
    private val _collidedPurchaseTokens = MutableStateFlow<Set<String>>(emptySet())
    val collidedPurchaseTokens: StateFlow<Set<String>> = _collidedPurchaseTokens.asStateFlow()
    /** Purchases Play holds as PENDING (slow payment method) — "Payment pending". */
    private val _pendingPaymentTokens = MutableStateFlow<Set<String>>(emptySet())
    val pendingPaymentTokens: StateFlow<Set<String>> = _pendingPaymentTokens.asStateFlow()
    /** Completed Play purchases the server couldn't confirm yet (503 / network) — the drain retries them. */
    private val _unconfirmedPurchaseTokens = MutableStateFlow<Set<String>>(emptySet())
    val unconfirmedPurchaseTokens: StateFlow<Set<String>> = _unconfirmedPurchaseTokens.asStateFlow()

    /**
     * Idempotent; `FitnessLinkApp.onCreate` calls it (main thread). Hooks the
     * process lifecycle so pending purchases drain on app start and on every
     * foreground (530).
     */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (lifecycleHooked) return
        lifecycleHooked = true
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (AuthManager.isAuthenticated.value) scope.launch { drainPendingPurchases() }
                }
            })
        } catch (e: Exception) {
            // Off the main thread — the gate's explicit drain still covers login.
            lifecycleHooked = false
            Log.w(TAG, "lifecycle hook skipped: ${e.message}")
        }
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
                // Release anyone still waiting on setup; the next call reconnects.
                if (deferred.isActive) {
                    deferred.completeExceptionally(BillingException.StoreError(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, null))
                }
                connection = null
            }
        })
        return deferred.await()
    }

    /** Runs a Play call, reconnecting once if the service dropped underneath it. */
    private suspend fun <T> withPlay(block: suspend (BillingClient) -> T): T = try {
        block(ensureConnected())
    } catch (e: BillingException.StoreError) {
        if (e.responseCode != BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) throw e
        connection = null
        block(ensureConnected())
    }

    // MARK: - Products

    /**
     * Plans (product + trial offer ids) and checkout-context (trial
     * eligibility) from the backend, then Play product details. Monthly first.
     */
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
                trialOfferIds = BillingTerm.entries.mapNotNull { term ->
                    plans.firstNotNullOfOrNull { it.googleTrialOfferId(term) }?.let { term to it }
                }.toMap()
            } catch (e: Exception) {
                Log.w(TAG, "plans unavailable, using pinned product ids: ${e.message}")
            }
            try {
                checkoutContext = ApiClient.billingApi.getCheckoutContext()
            } catch (e: Exception) {
                // Re-fetched before the purchase sheet; until then Play's eligibility decides the CTA.
                Log.w(TAG, "checkout-context unavailable: ${e.message}")
            }

            val result = withPlay { billing ->
                billing.queryProductDetails(
                    QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            productIds.values.distinct().map {
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId(it)
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build()
                            }
                        )
                        .build()
                )
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw BillingException.StoreError(result.billingResult.responseCode, result.billingResult.debugMessage)
            }
            val details = result.productDetailsList.orEmpty()
            productDetailsCache = BillingTerm.entries.mapNotNull { term ->
                val id = productIds[term] ?: return@mapNotNull null
                details.firstOrNull { it.productId == id }?.let { term to it }
            }.toMap()
            rebuildOffers()
            _loadError.value = if (_offers.value.isEmpty()) "Subscriptions aren't available right now. Please try again." else null
        } catch (e: Exception) {
            _loadError.value = e.message ?: "Subscriptions aren't available right now. Please try again."
        } finally {
            _isLoadingProducts.value = false
        }
    }

    private fun rebuildOffers() {
        val trialEligible = checkoutContext?.trialEligible
        _offers.value = BillingTerm.entries.mapNotNull { term ->
            productDetailsCache[term]?.let { offerFor(term, it, trialOfferIds[term], trialEligible) }
        }
    }

    private fun offerFor(term: BillingTerm, details: ProductDetails, trialOfferId: String?, trialEligible: Boolean?): PlanOffer? {
        val candidates = details.subscriptionOfferDetails.orEmpty().map { o ->
            PlayBillingRules.OfferCandidate(
                offerId = o.offerId,
                offerToken = o.offerToken,
                phases = o.pricingPhases.pricingPhaseList.map {
                    PlayBillingRules.PricingPhase(it.priceAmountMicros, it.formattedPrice, it.billingPeriod)
                }
            )
        }
        val choice = PlayBillingRules.selectOffer(candidates, trialOfferId, trialEligible) ?: return null
        return PlanOffer(
            term = term,
            productId = details.productId,
            title = when (term) {
                BillingTerm.MONTHLY -> "Monthly"
                BillingTerm.ANNUAL -> "Annual"
            },
            productDetails = details,
            offerToken = choice.candidate.offerToken,
            formattedPrice = choice.formattedPrice,
            priceMicros = choice.priceMicros,
            billingPeriod = choice.billingPeriod,
            trialPeriod = choice.trialPeriod
        )
    }

    // MARK: - Purchase

    /**
     * Buys [offer] and returns the entitlement the server computed afterwards.
     * Null when the user cancelled, the purchase is pending (slow payment
     * method — see [pendingPaymentTokens]), or the Play sheet never answered
     * (watchdog) — none is an error. Throws [BillingException] with copy.
     *
     * 535: an account the server already entitles never reaches the Play
     * sheet — the fresh entitlement is adopted and returned instead.
     */
    suspend fun purchase(activity: Activity, offer: PlanOffer): Entitlement? {
        if (_purchaseInFlight.value) return null
        _purchaseInFlight.value = true
        _purchaseStalled.value = false
        try {
            val current = try { ApiClient.billingApi.getEntitlement() } catch (e: Exception) { throw BillingException.ServerUnreachable }
            if (current.isEntitled) {
                EntitlementStore.apply(current)
                return current
            }

            // Before the sheet on purpose: ensures the Customer row, refreshes trial eligibility, gives the account id.
            val context = try { ApiClient.billingApi.getCheckoutContext() } catch (e: Exception) { throw BillingException.ServerUnreachable }
            val eligibilityChanged = context.trialEligible != checkoutContext?.trialEligible
            checkoutContext = context
            if (eligibilityChanged) rebuildOffers()
            // Never sell a trial the server just revoked; rebuilt offers now show the plain price.
            if (offer.hasTrial && offer(offer.term)?.hasTrial == false) throw BillingException.TrialNoLongerAvailable
            val toBuy = offer

            val accountId = context.googleObfuscatedAccountId?.takeIf { it.isNotBlank() }
                // Older server without the field: it doesn't check the id either.
                ?: AuthManager.currentUser.value?.id?.toString()

            val deferred = CompletableDeferred<PurchaseOutcome>()
            pendingPurchase = deferred
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(toBuy.productDetails)
                            .setOfferToken(toBuy.offerToken)
                            .build()
                    )
                )
                .apply { if (accountId != null) setObfuscatedAccountId(accountId) }
                .build()
            val launch = withPlay { it.launchBillingFlow(activity, params) }
            when (launch.responseCode) {
                BillingClient.BillingResponseCode.OK -> Unit
                BillingClient.BillingResponseCode.USER_CANCELED -> return null
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return restoreForAlreadyOwned()
                else -> throw BillingException.StoreError(launch.responseCode, launch.debugMessage)
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
                PurchaseOutcome.AlreadyOwned -> restoreForAlreadyOwned()
                is PurchaseOutcome.Failed -> throw BillingException.StoreError(outcome.result.responseCode, outcome.result.debugMessage)
                is PurchaseOutcome.Purchased -> {
                    val purchase = outcome.purchases.firstOrNull { toBuy.productId in it.products } ?: outcome.purchases.firstOrNull()
                    when {
                        purchase == null -> null
                        purchase.purchaseState == Purchase.PurchaseState.PENDING -> {
                            _pendingPaymentTokens.value = _pendingPaymentTokens.value + purchase.purchaseToken
                            null
                        }
                        else -> verifyAndAcknowledge(purchase, duringRestore = false)
                    }
                }
            }
        } finally {
            pendingPurchase = null
            _purchaseInFlight.value = false
        }
    }

    /** `ITEM_ALREADY_OWNED` → link what this Google account owns instead of erroring (531). */
    private suspend fun restoreForAlreadyOwned(): Entitlement? = when (val r = restorePurchases()) {
        is RestoreResult.Restored -> r.entitlement
        is RestoreResult.AlreadyPremium -> r.entitlement
        RestoreResult.PaymentPending -> null
        RestoreResult.NothingToRestore -> throw BillingException.NoActiveSubscription
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
                    // Renewal / pending payment approved / stalled sheet finally answering.
                    list.forEach { p ->
                        when (p.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> scope.launch { handle(p) }
                            Purchase.PurchaseState.PENDING -> _pendingPaymentTokens.value = _pendingPaymentTokens.value + p.purchaseToken
                            else -> Unit
                        }
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
            verifyAndAcknowledge(purchase, duringRestore = false)
        } catch (e: Exception) {
            Log.w(TAG, "deferred verify failed (will retry on next foreground): ${e.message}")
        }
    }

    /**
     * Server verify (or restore) → fallback acknowledge → adopt entitlement.
     * Records the per-token notice state and rethrows the mapped
     * [BillingException]. Never acknowledges after a failure or a `pending`.
     */
    private suspend fun verifyAndAcknowledge(purchase: Purchase, duringRestore: Boolean): Entitlement {
        val token = purchase.purchaseToken
        inFlightVerifies[token]?.let { return it.await() }
        val task = scope.async {
            val entitlement = try {
                val productId = purchase.products.firstOrNull().orEmpty()
                if (duringRestore) {
                    ApiClient.billingApi.restore(RestorePurchaseRequest.google(token))
                } else {
                    ApiClient.billingApi.verifyGoogle(VerifyGooglePurchaseRequest(token, productId, appContext?.packageName))
                }
            } catch (e: Exception) {
                val failure = PlayBillingRules.failureFor(e, duringRestore)
                when {
                    failure === BillingException.AlreadyLinkedToAnotherAccount ->
                        _collidedPurchaseTokens.value = _collidedPurchaseTokens.value + token
                    failure is BillingException.VerificationDelayed ->
                        _unconfirmedPurchaseTokens.value = _unconfirmedPurchaseTokens.value + token
                }
                if (PlayBillingRules.isTerminal(failure)) {
                    synchronized(terminalTokens) { terminalTokens += token }
                    _unconfirmedPurchaseTokens.value = _unconfirmedPurchaseTokens.value - token
                }
                throw failure
            }

            _unconfirmedPurchaseTokens.value = _unconfirmedPurchaseTokens.value - token
            _collidedPurchaseTokens.value = _collidedPurchaseTokens.value - token
            val outcome = PlayBillingRules.outcomeOf(entitlement)
            if (outcome == PlayBillingRules.VerifyOutcome.PENDING) {
                _pendingPaymentTokens.value = _pendingPaymentTokens.value + token
            } else {
                _pendingPaymentTokens.value = _pendingPaymentTokens.value - token
            }
            // The server acknowledges after linking; this is the no-op-if-acked fallback.
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                PlayBillingRules.shouldAcknowledge(outcome, purchase.isAcknowledged)
            ) {
                try {
                    val ack = withPlay {
                        it.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
                    }
                    if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "fallback acknowledge returned ${ack.responseCode} (server likely acked already)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "fallback acknowledge failed: ${e.message}")
                }
            }
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

    // MARK: - Restore / drain / re-verify

    /**
     * 531. If the server already entitles this account through a non-Play
     * source, say so and skip Play. Otherwise restore every active Play
     * subscription this Google account owns and adopt the entitlement.
     * Throws [BillingException] (409 linked, 503 retry, Play errors).
     */
    suspend fun restorePurchases(): RestoreResult {
        val server = try { ApiClient.billingApi.getEntitlement() } catch (e: Exception) {
            throw PlayBillingRules.failureFor(e, duringRestore = true)
        }
        PlayBillingRules.alreadyPremiumSource(server)?.let { source ->
            EntitlementStore.apply(server)
            return RestoreResult.AlreadyPremium(server, source)
        }

        val owned = ownedSubscriptions()
        val purchased = owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = owned.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
        if (pending.isNotEmpty()) _pendingPaymentTokens.value = _pendingPaymentTokens.value + pending.map { it.purchaseToken }

        val attempts = purchased.map { purchase ->
            try {
                Result.success(verifyAndAcknowledge(purchase, duringRestore = true))
            } catch (e: Exception) {
                Result.failure(PlayBillingRules.failureFor(e, duringRestore = true))
            }
        }
        val result = PlayBillingRules.summarizeRestore(attempts, playHasPendingOnly = pending.isNotEmpty())
        if (result is RestoreResult.Restored) EntitlementStore.apply(result.entitlement)
        return result
    }

    /**
     * 530: purchases stranded between purchase and verify (crash, dead
     * network, 503). Runs at app start and every foreground (process
     * lifecycle) and when the gate appears; concurrent calls coalesce and
     * calls within 30 s of the last are skipped unless [force]. A 409 leaves
     * the purchase unacknowledged and raises the collision notice.
     */
    suspend fun drainPendingPurchases(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastDrainAtMs < MIN_DRAIN_INTERVAL_MS) return
        if (!drainMutex.tryLock()) return
        try {
            lastDrainAtMs = System.currentTimeMillis()
            val owned = try { ownedSubscriptions() } catch (e: Exception) { Log.w(TAG, "drain skipped: ${e.message}"); return }
            // Play's current view replaces ours: a pending payment it no longer reports was declined or completed.
            _pendingPaymentTokens.value = owned.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
                .map { it.purchaseToken }.toSet()
            val skip = synchronized(terminalTokens) { terminalTokens.toSet() }
            owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged && it.purchaseToken !in skip }
                .forEach { handle(it) }
        } finally {
            drainMutex.unlock()
        }
    }

    /** Re-offer every owned subscription to the server — run when the cached entitlement is about to lapse. */
    suspend fun reverifyCurrentPurchases() {
        val purchases = try { ownedSubscriptions() } catch (e: Exception) { return }
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { handle(it) }
    }

    /** The Play product id this Google account currently subscribes to, for the Manage deep link. */
    suspend fun ownedProductId(): String? = try {
        ownedSubscriptions().firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }?.products?.firstOrNull()
    } catch (e: Exception) {
        null
    }

    private suspend fun ownedSubscriptions(): List<Purchase> {
        val result = withPlay {
            it.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        }
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingException.StoreError(result.billingResult.responseCode, result.billingResult.debugMessage)
        }
        val ours = productIds.values.toSet()
        return result.purchasesList.filter { p -> p.products.any { it in ours } }
    }

    // MARK: - Manage

    /** Play's subscription centre, scoped to our subscription when we know which one (generic URL otherwise). */
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
        checkoutContext = null
        lastDrainAtMs = 0L
        synchronized(terminalTokens) { terminalTokens.clear() }
        _purchaseInFlight.value = false
        _purchaseStalled.value = false
        _collidedPurchaseTokens.value = emptySet()
        _pendingPaymentTokens.value = emptySet()
        _unconfirmedPurchaseTokens.value = emptySet()
    }
}
