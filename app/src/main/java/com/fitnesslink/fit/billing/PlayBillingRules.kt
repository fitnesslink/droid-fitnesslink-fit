package com.fitnesslink.fit.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.Entitlement
import java.util.Locale

/**
 * Billing failures (and the few "not a failure, but say something" results)
 * the paywall / Subscription screen can explain. Port of iOS `BillingError`.
 * Every message is user-facing copy.
 */
sealed class BillingException(message: String) : Exception(message) {
    /** Server 409: the Play subscription is linked to a different FitnessLink account. Never acknowledged. */
    data object AlreadyLinkedToAnotherAccount : BillingException(
        "This Google Play subscription is linked to another FitnessLink account. Sign in with that account, or contact support."
    ) { private fun readResolve(): Any = AlreadyLinkedToAnotherAccount }

    /** Server 403: the sandbox gate refused a test (license-tester) purchase. */
    data object TestPurchaseNotAccepted : BillingException(
        "Test purchases aren't accepted for this account. Use a regular Google Play payment method, or contact support."
    ) { private fun readResolve(): Any = TestPurchaseNotAccepted }

    /** Server 422: Play says the token is invalid or expired. */
    data object PurchaseNotValid : BillingException(
        "Google Play couldn't confirm this purchase — it may have expired or been refunded. Please try again, or contact support."
    ) { private fun readResolve(): Any = PurchaseNotValid }

    /**
     * 503 (Play verification not configured), other 5xx, network or timeout.
     * Transient: the purchase stays unacknowledged and the drain retries it.
     */
    data class VerificationDelayed(val duringRestore: Boolean) : BillingException(
        if (duringRestore) {
            "We couldn't reach FitnessLink to restore your purchase. Check your connection and try again in a moment."
        } else {
            "Your purchase went through, but FitnessLink couldn't confirm it yet. We'll keep trying — your access unlocks as soon as it's confirmed."
        }
    )

    data object SessionExpired : BillingException("Your session expired. Please sign in again.") {
        private fun readResolve(): Any = SessionExpired
    }

    /** Couldn't load checkout-context / entitlement before opening Play. Nothing was charged. */
    data object ServerUnreachable : BillingException(
        "We couldn't reach FitnessLink. Check your connection and try again."
    ) { private fun readResolve(): Any = ServerUnreachable }

    data object NoActiveSubscription : BillingException("No active subscription was found for this Google account.") {
        private fun readResolve(): Any = NoActiveSubscription
    }

    /** Play holds the purchase as PENDING (slow payment method). */
    data object PaymentPending : BillingException(
        "Payment pending. Your access unlocks as soon as Google Play confirms the payment."
    ) { private fun readResolve(): Any = PaymentPending }

    /** The server revoked trial eligibility between showing the paywall and buying. Offers were rebuilt. */
    data object TrialNoLongerAvailable : BillingException(
        "This account has already used its free trial. Check the updated price, then tap Subscribe."
    ) { private fun readResolve(): Any = TrialNoLongerAvailable }

    /** A Play Billing Library response code other than OK / USER_CANCELED. */
    data class StoreError(val responseCode: Int, val debug: String? = null) : BillingException(
        PlayBillingRules.responseCodeMessage(responseCode) ?: PlayBillingRules.GENERIC_STORE_MESSAGE
    ) {
        val isRetryable: Boolean get() = PlayBillingRules.isRetryable(responseCode)
    }
}

/** Result of a Restore Purchases tap (iOS `StoreKitManager.restorePurchases` + the 531 pre-check). */
sealed class RestoreResult {
    /** The server re-linked a Play purchase; [entitlement] is what it computed. */
    data class Restored(val entitlement: Entitlement) : RestoreResult()

    /** Already entitled through another source — Play was not consulted. */
    data class AlreadyPremium(val entitlement: Entitlement, val sourceName: String) : RestoreResult()

    /** This Google account owns no active FitnessLink subscription. */
    data object NothingToRestore : RestoreResult()

    /** The only purchase Play holds is still PENDING. */
    data object PaymentPending : RestoreResult()
}

/**
 * Pure Play Billing rules — no Android or Play types beyond the response
 * code constants (compile-time ints), so everything here is JVM-testable.
 */
object PlayBillingRules {
    const val GENERIC_STORE_MESSAGE = "Google Play isn't available right now. Please try again."

    /** `BillingResponseCode.SERVICE_TIMEOUT` (deprecated in the library, still delivered by older Play Stores). */
    private const val SERVICE_TIMEOUT = -3

    // MARK: - Offers

    /** One Play pricing phase, stripped of Play types. */
    data class PricingPhase(val priceMicros: Long, val formattedPrice: String, val billingPeriod: String)

    /** One `SubscriptionOfferDetails`, stripped of Play types. `offerId == null` is the base plan. */
    data class OfferCandidate(val offerId: String?, val offerToken: String, val phases: List<PricingPhase>)

    /** What the paywall shows and buys for one plan. */
    data class OfferChoice(
        val candidate: OfferCandidate,
        /** True only when this is the configured trial offer and the account is eligible. */
        val isTrial: Boolean,
        /** Recurring (post-trial) localized price from Play. */
        val formattedPrice: String,
        val priceMicros: Long,
        /** ISO-8601 recurring period, e.g. "P1M". */
        val billingPeriod: String,
        /** ISO-8601 free-phase length when [isTrial], else null. */
        val trialPeriod: String?
    )

    /**
     * 533 offer selection: the plan's trial offer (`storeOfferId`) only when
     * the server says the account is trial-eligible AND Play returned that
     * offer (Play filters out offers the Google account isn't eligible for);
     * otherwise the base-plan offer. `trialEligible == null` (older server)
     * defers to Play's filtering alone.
     */
    fun selectOffer(candidates: List<OfferCandidate>, trialOfferId: String?, trialEligible: Boolean?): OfferChoice? {
        val trial = if (trialEligible != false && !trialOfferId.isNullOrBlank()) {
            candidates.firstOrNull { it.offerId == trialOfferId }
        } else null
        if (trial != null) choice(trial, isTrial = true)?.let { return it }
        val base = candidates.firstOrNull { it.offerId == null }
            // No base-plan entry (shouldn't happen): any offer without a free phase.
            ?: candidates.firstOrNull { c -> c.phases.none { it.priceMicros == 0L } }
            ?: return null
        return choice(base, isTrial = false)
    }

    private fun choice(candidate: OfferCandidate, isTrial: Boolean): OfferChoice? {
        val recurring = candidate.phases.lastOrNull { it.priceMicros > 0 } ?: return null
        val free = if (isTrial) candidate.phases.firstOrNull { it.priceMicros == 0L } else null
        return OfferChoice(
            candidate = candidate,
            isTrial = isTrial && free != null,
            formattedPrice = recurring.formattedPrice,
            priceMicros = recurring.priceMicros,
            billingPeriod = recurring.billingPeriod,
            trialPeriod = free?.billingPeriod
        )
    }

    // MARK: - Play response codes

    /** Copy for every `BillingResponseCode`. Null = say nothing (OK, user cancelled). */
    fun responseCodeMessage(code: Int): String? = when (code) {
        BillingResponseCode.OK, BillingResponseCode.USER_CANCELED -> null
        SERVICE_TIMEOUT -> "Google Play took too long to respond. Please try again."
        BillingResponseCode.FEATURE_NOT_SUPPORTED ->
            "Subscriptions aren't supported by the Play Store on this device. Update the Google Play Store app, then try again."
        BillingResponseCode.SERVICE_DISCONNECTED -> "Lost the connection to Google Play. Please try again."
        BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play can't be reached right now. Check your connection and try again."
        BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play Billing isn't available. Make sure you're signed in to the Play Store and it's up to date, then try again."
        BillingResponseCode.ITEM_UNAVAILABLE -> "This plan isn't available for your Google account or country right now."
        BillingResponseCode.DEVELOPER_ERROR -> "Something went wrong setting up the purchase. Please try again later or contact support."
        BillingResponseCode.ERROR -> "Google Play ran into a problem. Please try again."
        BillingResponseCode.ITEM_ALREADY_OWNED ->
            "This Google account already has a FitnessLink subscription. Tap Restore Purchases to link it."
        BillingResponseCode.ITEM_NOT_OWNED -> "This Google account doesn't own that subscription."
        BillingResponseCode.NETWORK_ERROR -> "Network problem talking to Google Play. Check your connection and try again."
        else -> GENERIC_STORE_MESSAGE
    }

    /** Codes worth a "try again" (connection / transient service trouble). */
    fun isRetryable(code: Int): Boolean = code in setOf(
        SERVICE_TIMEOUT,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.BILLING_UNAVAILABLE,
        BillingResponseCode.ERROR,
        BillingResponseCode.NETWORK_ERROR
    )

    // MARK: - Server outcomes

    enum class VerifyOutcome { VERIFIED, PENDING }

    /** A 200 from verify / restore. `pending` is never acknowledged. */
    fun outcomeOf(entitlement: Entitlement): VerifyOutcome =
        if (entitlement.isPending) VerifyOutcome.PENDING else VerifyOutcome.VERIFIED

    /** Client-side fallback acknowledgement: only after a 200 that isn't `pending`, and only if Play still shows it unacked. */
    fun shouldAcknowledge(outcome: VerifyOutcome, alreadyAcknowledged: Boolean): Boolean =
        outcome == VerifyOutcome.VERIFIED && !alreadyAcknowledged

    /**
     * Map a verify / restore failure onto copy. 403 / 409 / 422 are terminal
     * for that token; everything else (503, other 5xx, network, an older
     * server without the route) is "retry later".
     */
    fun failureFor(error: Throwable, duringRestore: Boolean): BillingException = when (error) {
        is BillingException -> error
        is ApiError.Forbidden -> BillingException.TestPurchaseNotAccepted
        is ApiError.Unauthorized -> BillingException.SessionExpired
        is ApiError.ServerError -> when (error.statusCode) {
            403 -> BillingException.TestPurchaseNotAccepted
            409 -> BillingException.AlreadyLinkedToAnotherAccount
            422 -> BillingException.PurchaseNotValid
            else -> BillingException.VerificationDelayed(duringRestore)
        }
        else -> BillingException.VerificationDelayed(duringRestore)
    }

    /** Terminal failures: the drain stops re-sending this token for the rest of the process. */
    fun isTerminal(failure: BillingException): Boolean = when (failure) {
        BillingException.AlreadyLinkedToAnotherAccount,
        BillingException.TestPurchaseNotAccepted,
        BillingException.PurchaseNotValid -> true
        else -> false
    }

    // MARK: - Restore

    /**
     * 531 pre-check: entitled through a non-Play source means Play has nothing
     * to restore — returns the source's display name ("the App Store", "the
     * web", "FitnessLink"), else null.
     */
    fun alreadyPremiumSource(entitlement: Entitlement): String? {
        if (!entitlement.isEntitled || entitlement.isGoogle) return null
        return when (entitlement.source?.lowercase(Locale.US)) {
            "apple" -> "the App Store"
            "stripe" -> "the web"
            "comped" -> "FitnessLink"
            null, "" -> null
            else -> entitlement.source
        }
    }

    /**
     * Fold per-purchase restore attempts into one answer. Any entitled
     * success wins; otherwise a collision, then any other failure, is thrown;
     * otherwise pending vs nothing.
     */
    fun summarizeRestore(attempts: List<Result<Entitlement>>, playHasPendingOnly: Boolean): RestoreResult {
        val successes = attempts.mapNotNull { it.getOrNull() }
        successes.firstOrNull { it.isEntitled }?.let { return RestoreResult.Restored(it) }
        val failures = attempts.mapNotNull { it.exceptionOrNull() }
        failures.firstOrNull { it === BillingException.AlreadyLinkedToAnotherAccount }?.let { throw it }
        failures.firstOrNull()?.let { throw it }
        if (successes.any { it.isPending } || (attempts.isEmpty() && playHasPendingOnly)) return RestoreResult.PaymentPending
        return RestoreResult.NothingToRestore
    }

    /** Copy for a restore result. */
    fun restoreMessage(result: RestoreResult): String = when (result) {
        is RestoreResult.Restored -> "Your subscription has been restored."
        is RestoreResult.AlreadyPremium -> "You already have Premium via ${result.sourceName}."
        RestoreResult.NothingToRestore -> BillingException.NoActiveSubscription.message!!
        RestoreResult.PaymentPending -> BillingException.PaymentPending.message!!
    }
}
