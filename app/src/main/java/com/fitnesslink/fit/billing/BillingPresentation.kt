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

    /** iOS `BillingView.statusLabel` + `statusColor`. */
    fun statusPill(entitlement: Entitlement, subscription: SubscriptionDto?): StatusPill {
        if (entitlement.isComped) return StatusPill("COMPLIMENTARY", PillTone.BRAND)
        return when (entitlement.status.lowercase(Locale.US)) {
            "trial" -> StatusPill("FREE TRIAL", PillTone.BRAND)
            "active" -> StatusPill(
                if (subscription?.cancelAtPeriodEnd == true) "CANCELS SOON" else "ACTIVE",
                PillTone.BRAND
            )
            "grace" -> StatusPill("PAYMENT ISSUE", PillTone.WARNING)
            "expired" -> StatusPill("EXPIRED", PillTone.NEUTRAL)
            else -> StatusPill("NONE", PillTone.NEUTRAL)
        }
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
        if (subscription?.cancelAtPeriodEnd == true) return "Access ends ${dateText(end)}"
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

    fun actions(entitlement: Entitlement): Actions = when {
        entitlement.isComped -> Actions.COMPED
        entitlement.isStripe -> Actions.WEB
        entitlement.isApple -> Actions.APPLE
        else -> Actions.PLAY
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

    fun trialButtonText(trialIsoPeriod: String?): String {
        val period = trialPeriodText(trialIsoPeriod) ?: return "Start Free Trial"
        return "Start ${period.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }} Free Trial"
    }

    /** "$9.99 / month". */
    fun priceLine(formattedPrice: String, isoPeriod: String?): String =
        if (isoPeriod == null) formattedPrice else "$formattedPrice / ${periodName(isoPeriod)}"

    /** Required disclosure: what is charged, when, and that it renews (iOS `disclosure(for:)`). */
    fun disclosure(formattedPrice: String, isoPeriod: String?, trialIsoPeriod: String?): String {
        val price = priceLine(formattedPrice, isoPeriod)
        if (trialIsoPeriod != null) {
            val trial = trialPeriodText(trialIsoPeriod)?.let { "$it free" } ?: "Free trial"
            return "$trial, then $price. Renews automatically until cancelled. Cancel anytime in Google Play."
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
