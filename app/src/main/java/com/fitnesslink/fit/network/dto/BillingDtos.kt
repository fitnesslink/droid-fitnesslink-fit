package com.fitnesslink.fit.network.dto

import java.math.BigDecimal
import java.util.Date

// FTNSSLNK-281. Mirrors the Billing module's DTOs (billing/api/v1) and iOS
// `Billing.swift`. Display prices are reporting-only — Google Play is
// authoritative for what is actually charged.

/**
 * The single authoritative "may this user see paid content?" answer,
 * computed server-side from store-synced subscription rows.
 */
data class Entitlement(
    val isEntitled: Boolean = false,
    /**
     * `trial` · `active` · `grace` · `expired` · `none` · `pending`.
     * `pending` = Play purchase state PENDING (slow payment method): not
     * entitled, not acknowledged; access unlocks once Play confirms payment.
     */
    val status: String = "none",
    /** `Apple` · `Stripe` · `Google` · `Comped` of the granting subscription. */
    val source: String? = null,
    val currentPeriodEnd: Date? = null,
    val trialEndsAt: Date? = null
) {
    val isTrial: Boolean get() = status == "trial"
    /** Play reported the purchase as PENDING — show "Payment pending", never acknowledge. */
    val isPending: Boolean get() = status.equals("pending", ignoreCase = true)
    val isGoogle: Boolean get() = source.equals("Google", ignoreCase = true)
    /** Payment is failing but access is preserved for the retry window. */
    val isGrace: Boolean get() = status == "grace"
    val isStripe: Boolean get() = source.equals("Stripe", ignoreCase = true)
    val isComped: Boolean get() = source.equals("Comped", ignoreCase = true)
    val isApple: Boolean get() = source.equals("Apple", ignoreCase = true)
    /** No Play subscription backs this entitlement — Play's manage / restore are no-ops. */
    val isManagedOutsidePlayStore: Boolean get() = isStripe || isComped || isApple

    companion object {
        val NONE = Entitlement()
    }
}

/** `GET /plans` row. */
data class SubscriptionPlan(
    val id: String,
    val name: String = "",
    val tier: String = "",
    val displayPriceMonthly: BigDecimal = BigDecimal.ZERO,
    val displayPriceAnnual: BigDecimal = BigDecimal.ZERO,
    val features: String? = null,
    val isActive: Boolean = true,
    val storeProducts: List<PlanStoreProduct> = emptyList()
) {
    /** The Play product id for a term, or null when the backend hasn't been configured with one. */
    fun googleProductId(term: BillingTerm): String? = storeProducts.firstOrNull {
        it.source.equals("Google", ignoreCase = true) && it.term.equals(term.wireValue, ignoreCase = true)
    }?.storeProductId

    /** The Play free-trial offer id for a term (`storeOfferId`), or null when none is configured. */
    fun googleTrialOfferId(term: BillingTerm): String? = storeProducts.firstOrNull {
        it.source.equals("Google", ignoreCase = true) && it.term.equals(term.wireValue, ignoreCase = true)
    }?.storeOfferId?.takeIf { it.isNotBlank() }
}

data class PlanStoreProduct(
    /** `Stripe` · `Apple` · `Google` */
    val source: String = "",
    /** `Monthly` · `Annual` */
    val term: String = "",
    val storeProductId: String = "",
    val storeSubscriptionId: String? = null,
    val storeOfferId: String? = null
)

enum class BillingTerm(val wireValue: String) {
    MONTHLY("Monthly"),
    ANNUAL("Annual")
}

/**
 * `GET /customers/me/checkout-context`. Fetching this also ensures the
 * backend Customer row exists. Both Google fields are nullable so an older
 * server (that doesn't send them) still decodes.
 */
data class CheckoutContext(
    val stripeCustomerId: String? = null,
    val appleAppAccountToken: String? = null,
    /**
     * One free trial per FitnessLink account across every platform. Null
     * (older server) = unknown: fall back to Play's own offer eligibility.
     */
    val trialEligible: Boolean? = null,
    /**
     * Pass exactly this to `setObfuscatedAccountId`. The server rejects (409)
     * a verify whose obfuscated id isn't the caller's.
     */
    val googleObfuscatedAccountId: String? = null
)

/** `GET /subscriptions/me` (404 when the user has never subscribed). */
data class SubscriptionDto(
    val id: String,
    val customerId: String,
    val planId: String,
    /** `Active` · `Canceled` · `PastDue` · `Trial` · `Expired` */
    val status: String = "",
    /** `Stripe` · `Apple` · `Google` */
    val source: String = "",
    val currentPeriodStart: Date? = null,
    val currentPeriodEnd: Date? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val externalSubscriptionId: String? = null,
    /** `PastDue` + a future value = grace (access kept); `PastDue` with null / past = account hold (no access). */
    val gracePeriodEndsAt: Date? = null,
    val isSandbox: Boolean = false
)

/**
 * `POST /subscriptions/restore` body (server `RestorePurchaseRequest`).
 * Apple sends `signedTransaction`; Google sends `purchaseToken`. 409 = the
 * store subscription belongs to another FitnessLink account.
 */
data class RestorePurchaseRequest(
    val source: String,
    val signedTransaction: String? = null,
    val purchaseToken: String? = null
) {
    companion object {
        fun google(purchaseToken: String) = RestorePurchaseRequest(source = "Google", purchaseToken = purchaseToken)
    }
}

/**
 * `POST /subscriptions/google/verify` body. 200 → [Entitlement] (status
 * `pending` while Play's purchase state is PENDING). 403 test purchase not
 * accepted, 409 linked to another account, 422 token invalid / expired,
 * 503 Play verification not configured (transient).
 */
data class VerifyGooglePurchaseRequest(
    val purchaseToken: String,
    val productId: String,
    val packageName: String? = null
)
