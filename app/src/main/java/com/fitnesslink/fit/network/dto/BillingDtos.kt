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
    /** `trial` · `active` · `grace` · `expired` · `none` */
    val status: String = "none",
    /** `Apple` · `Stripe` · `Google` · `Comped` of the granting subscription. */
    val source: String? = null,
    val currentPeriodEnd: Date? = null,
    val trialEndsAt: Date? = null
) {
    val isTrial: Boolean get() = status == "trial"
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
 * backend Customer row exists. `appleAppAccountToken` is the account
 * correlation token Apple uses; Play has no equivalent field yet — pass the
 * user id as `obfuscatedAccountId` on the Play purchase instead.
 */
data class CheckoutContext(
    val stripeCustomerId: String? = null,
    val appleAppAccountToken: String? = null
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
    val externalSubscriptionId: String? = null
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
 * TODO(backend): `POST /subscriptions/google/verify` does not exist yet —
 * the platform only has `apple/verify` plus a `restore` that accepts a
 * Google `purchaseToken`. Shape mirrors `VerifyAppleTransactionRequest`
 * with the Play Billing identifiers; confirm with the backend before use.
 */
data class VerifyGooglePurchaseRequest(
    val purchaseToken: String,
    val productId: String,
    val packageName: String? = null
)
