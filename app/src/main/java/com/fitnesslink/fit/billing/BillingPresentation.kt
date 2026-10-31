package com.fitnesslink.fit.billing

import com.fitnesslink.fit.network.dto.Entitlement
import com.fitnesslink.fit.network.dto.SubscriptionDto
import java.util.Locale

/**
 * Pure presentation rules for the Subscription screen and the paywall
 * (iOS `BillingView` copy + `PaywallView.periodName`). Kept free of
 * Android / Compose so the derivations are unit-testable.
 */
object BillingPresentation {

    enum class PillTone { BRAND, WARNING, NEUTRAL }

    data class StatusPill(val label: String, val tone: PillTone)

    /** iOS `BillingView.statusLabel` + `statusColor`, plus Play's pending payment and account hold. */
    fun statusPill(entitlement: Entitlement, subscription: SubscriptionDto?, nowMs: Long = System.currentTimeMillis()): StatusPill {
        if (entitlement.isComped) return StatusPill("COMPLIMENTARY", PillTone.BRAND)
        if (isAccountHold(entitlement, subscription, nowMs)) return StatusPill("ON HOLD", PillTone.WARNING)
        if (isGrace(entitlement, subscription, nowMs)) return StatusPill("PAYMENT ISSUE", PillTone.WARNING)
        return when (entitlement.status.lowercase(Locale.US)) {
            "trial" -> StatusPill(
                if (subscription?.cancelAtPeriodEnd == true) "TRIAL · CANCELS" else "FREE TRIAL",
                PillTone.BRAND
            )
            "active" -> StatusPill(
                if (subscription?.cancelAtPeriodEnd == true) "CANCELS SOON" else "ACTIVE",
                PillTone.BRAND
            )
            "pending" -> StatusPill("PAYMENT PENDING", PillTone.WARNING)
            "expired" -> StatusPill("EXPIRED", PillTone.NEUTRAL)
            else -> StatusPill("NONE", PillTone.NEUTRAL)
        }
    }

    // MARK: - Grace / account hold (534)

    /** Payment failing, access kept: server `grace`, or `PastDue` with a future `gracePeriodEndsAt`. */
    fun isGrace(entitlement: Entitlement, subscription: SubscriptionDto?, nowMs: Long): Boolean {
        if (entitlement.isGrace) return true
        if (!subscription.isPastDue()) return false
        val graceEnd = subscription?.gracePeriodEndsAt ?: return false
        return graceEnd.time > nowMs
    }

    /** Payment failing, access suspended: `PastDue` with no (or a past) grace period, and not entitled. */
    fun isAccountHold(entitlement: Entitlement, subscription: SubscriptionDto?, nowMs: Long): Boolean {
        if (entitlement.isEntitled || !subscription.isPastDue()) return false
        val graceEnd = subscription?.gracePeriodEndsAt
        return graceEnd == null || graceEnd.time <= nowMs
    }

    private fun SubscriptionDto?.isPastDue(): Boolean = this?.status.equals("PastDue", ignoreCase = true)

    enum class BannerKind { GRACE, HOLD }

    data class PaymentBanner(val kind: BannerKind, val text: String, val opensPlay: Boolean)

    /** Grace / account-hold banner, with "where to fix it" copy for the billing source. */
    fun paymentBanner(
        entitlement: Entitlement,
        subscription: SubscriptionDto?,
        nowMs: Long,
        dateText: (java.util.Date) -> String
    ): PaymentBanner? {
        val source = actions(entitlement, subscription)
        val where = when (source) {
            Actions.PLAY -> "in Google Play"
            Actions.APPLE -> "on your iPhone (Settings → Apple ID → Payment & Shipping)"
            Actions.WEB -> "on the web"
            Actions.COMPED -> return null
        }
        val opensPlay = source == Actions.PLAY
        if (isAccountHold(entitlement, subscription, nowMs)) {
            return PaymentBanner(
                BannerKind.HOLD,
                "Your subscription is on hold because of a payment problem. Update your payment method $where to get your access back.",
                opensPlay
            )
        }
        if (isGrace(entitlement, subscription, nowMs)) {
            val by = subscription?.gracePeriodEndsAt?.let { " by ${dateText(it)}" }.orEmpty()
            return PaymentBanner(
                BannerKind.GRACE,
                "There's a problem with your payment. Update your payment method $where$by to keep your access.",
                opensPlay
            )
        }
        return null
    }

    /** iOS `BillingView.planName`. */
    fun planName(entitlement: Entitlement, subscription: SubscriptionDto?): String {
        if (entitlement.isComped) return "FitnessLink Premium"
        val source = subscription?.source ?: return "FitnessLink Premium"
        return when (source.lowercase(Locale.US)) {
            "google", "apple" -> "FitnessLink Premium"
            else -> "FitnessLink Premium (Web)"
        }
    }

    /**
     * iOS `BillingView.renewalLine`. `dateText` formats the period end; a
     * comped period end is a far-future sentinel so it is never printed.
     */
    fun renewalLine(entitlement: Entitlement, subscription: SubscriptionDto?, dateText: (java.util.Date) -> String): String? {
        if (entitlement.isComped) return "Doesn't expire"
        val end = entitlement.currentPeriodEnd ?: return null
        if (subscription?.cancelAtPeriodEnd == true) return "Cancels on ${dateText(end)}"
        return if (entitlement.isEntitled) "Renews ${dateText(end)}" else "Ended ${dateText(end)}"
    }

    fun billingSourceName(source: String): String = when (source.lowercase(Locale.US)) {
        "apple" -> "the App Store"
        "stripe" -> "the web"
        "google" -> "Google Play"
        "comped" -> "FitnessLink (complimentary)"
        else -> source
    }

    /** Which action block the Subscription screen renders (iOS `BillingView.actions`). */
    enum class Actions { COMPED, WEB, APPLE, PLAY }

    /**
     * The billing source drives the manage controls. The entitlement's source
     * wins; a lapsed / on-hold user (no entitlement source) falls back to the
     * subscription row's; nothing at all means a would-be Play subscriber.
     */
    fun actions(entitlement: Entitlement, subscription: SubscriptionDto? = null): Actions {
        val source = entitlement.source?.takeIf { it.isNotBlank() } ?: subscription?.source?.takeIf { it.isNotBlank() }
        return when (source?.lowercase(Locale.US)) {
            "comped" -> Actions.COMPED
            "stripe" -> Actions.WEB
            "apple" -> Actions.APPLE
            else -> Actions.PLAY
        }
    }

    /** What "Manage subscription" does for each source (534). */
    sealed class ManageAction {
        /** Open Play's subscription centre ([AppLinks.playSubscriptions]). */
        data object OpenPlay : ManageAction()
        /** No link — Play can't manage an App Store subscription. */
        data class Instructions(val text: String) : ManageAction()
        data class OpenUrl(val label: String, val url: String) : ManageAction()
        data class Info(val text: String) : ManageAction()
    }

    const val APPLE_MANAGE_TEXT =
        "Billed through the App Store — manage it on your iPhone (Settings → Apple ID → Subscriptions)."
    const val COMPED_TEXT = "Your access is on us — there's nothing to manage or pay."

    fun manageAction(entitlement: Entitlement, subscription: SubscriptionDto? = null): ManageAction =
        when (actions(entitlement, subscription)) {
            Actions.PLAY -> ManageAction.OpenPlay
            Actions.APPLE -> ManageAction.Instructions(APPLE_MANAGE_TEXT)
            Actions.WEB -> ManageAction.OpenUrl("Manage on the web", AppLinks.WEB_BILLING_PORTAL)
            Actions.COMPED -> ManageAction.Info(COMPED_TEXT)
        }

    // MARK: - Play offer copy

    /**
     * ISO-8601 billing period ("P1M", "P1Y", "P1W", "P7D") → "month", "year",
     * "week", "7 days" — the same words Play's purchase sheet uses, so our
     * copy and the system sheet never disagree. Unknown input → "period".
     */
    fun periodName(isoPeriod: String?): String {
        val match = PERIOD.matchEntire(isoPeriod?.trim().orEmpty()) ?: return "period"
        val value = match.groupValues[1].toIntOrNull() ?: return "period"
        val unit = when (match.groupValues[2]) {
            "D" -> "day"
            "W" -> "week"
            "M" -> "month"
            "Y" -> "year"
            else -> "period"
        }
        return if (value == 1) unit else "$value ${unit}s"
    }

    /** "1 week" / "2 weeks" — the trial length as a phrase (iOS `trialPeriodText`). */
    fun trialPeriodText(isoPeriod: String?): String? {
        if (isoPeriod == null) return null
        val name = periodName(isoPeriod)
        if (name == "period") return null
        return if (name.first().isDigit()) name else "1 $name"
    }

    /**
     * 533 CTA: "Start 7-day free trial" when the selected offer is the trial
     * offer, else "Subscribe". Weeks read as days ("P1W" → "7-day") the way
     * trial lengths are usually advertised.
     */
    fun ctaText(trialIsoPeriod: String?): String {
        if (trialIsoPeriod == null) return "Subscribe"
        val length = trialLengthAdjective(trialIsoPeriod) ?: return "Start free trial"
        return "Start $length free trial"
    }

    /** "P7D" / "P1W" → "7-day", "P1M" → "1-month"; null when unparseable. */
    fun trialLengthAdjective(isoPeriod: String): String? {
        val match = PERIOD.matchEntire(isoPeriod.trim()) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return when (match.groupValues[2]) {
            "D" -> "$value-day"
            "W" -> "${value * 7}-day"
            "M" -> "$value-month"
            "Y" -> "$value-year"
            else -> null
        }
    }

    /** "$9.99 / month". */
    fun priceLine(formattedPrice: String, isoPeriod: String?): String =
        if (isoPeriod == null) formattedPrice else "$formattedPrice / ${periodName(isoPeriod)}"

    /** Required disclosure: what is charged, when, and that it renews (iOS `disclosure(for:)`). */
    fun disclosure(formattedPrice: String, isoPeriod: String?, trialIsoPeriod: String?): String {
        val price = priceLine(formattedPrice, isoPeriod)
        if (trialIsoPeriod != null) {
            val trial = trialLengthAdjective(trialIsoPeriod)?.let { "$it free trial" } ?: "Free trial"
            return "$trial, then $price. Renews automatically until cancelled. " +
                "Cancel anytime in Google Play — cancel before the trial ends and you won't be charged."
        }
        return "$price. Renews automatically until cancelled. Cancel anytime in Google Play."
    }

    /**
     * Annual vs. 12× monthly from store prices (micros) so it can't drift from
     * what Play actually charges. Null when annual isn't cheaper.
     */
    fun savingsBadge(monthlyMicros: Long?, annualMicros: Long?): String? {
        if (monthlyMicros == null || annualMicros == null || monthlyMicros <= 0) return null
        val yearlyAtMonthly = monthlyMicros * 12
        if (yearlyAtMonthly <= annualMicros) return null
        val percent = ((yearlyAtMonthly - annualMicros) * 100 / yearlyAtMonthly).toInt()
        return if (percent > 0) "SAVE $percent%" else null
    }

    private val PERIOD = Regex("^P(\\d+)([DWMY])$")
}
