package com.fitnesslink.fit.profile

import com.fitnesslink.fit.billing.BillingPresentation
import com.fitnesslink.fit.billing.BillingPresentation.PillTone
import com.fitnesslink.fit.network.dto.Entitlement
import com.fitnesslink.fit.network.dto.SubscriptionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class BillingPresentationTests {
    private fun entitlement(status: String, source: String? = "Google", entitled: Boolean = true, end: Date? = Date(0)) =
        Entitlement(isEntitled = entitled, status = status, source = source, currentPeriodEnd = end)

    private fun subscription(cancelAtPeriodEnd: Boolean, source: String = "Google") = SubscriptionDto(
        id = "s", customerId = "c", planId = "p", status = "Active", source = source, cancelAtPeriodEnd = cancelAtPeriodEnd
    )

    // MARK: - status pill

    @Test
    fun `active subscription is ACTIVE`() {
        val pill = BillingPresentation.statusPill(entitlement("active"), subscription(cancelAtPeriodEnd = false))
        assertEquals("ACTIVE", pill.label)
        assertEquals(PillTone.BRAND, pill.tone)
    }

    @Test
    fun `active subscription cancelling at period end is CANCELS SOON`() {
        assertEquals("CANCELS SOON", BillingPresentation.statusPill(entitlement("active"), subscription(cancelAtPeriodEnd = true)).label)
    }

    @Test
    fun `trial is FREE TRIAL`() {
        assertEquals("FREE TRIAL", BillingPresentation.statusPill(entitlement("trial"), null).label)
    }

    @Test
    fun `grace is a warning PAYMENT ISSUE`() {
        val pill = BillingPresentation.statusPill(entitlement("grace"), null)
        assertEquals("PAYMENT ISSUE", pill.label)
        assertEquals(PillTone.WARNING, pill.tone)
    }

    @Test
    fun `comped wins over status`() {
        val pill = BillingPresentation.statusPill(entitlement("active", source = "Comped"), null)
        assertEquals("COMPLIMENTARY", pill.label)
    }

    @Test
    fun `expired and none are neutral`() {
        assertEquals(PillTone.NEUTRAL, BillingPresentation.statusPill(entitlement("expired", entitled = false), null).tone)
        assertEquals("NONE", BillingPresentation.statusPill(Entitlement.NONE, null).label)
    }

    // MARK: - renewal copy

    @Test
    fun `renewal line reads renews, ends, or ended`() {
        val text: (Date) -> String = { "D" }
        assertEquals("Renews D", BillingPresentation.renewalLine(entitlement("active"), subscription(false), text))
        assertEquals("Cancels on D", BillingPresentation.renewalLine(entitlement("active"), subscription(true), text))
        assertEquals("Ended D", BillingPresentation.renewalLine(entitlement("expired", entitled = false), null, text))
        assertEquals("Doesn't expire", BillingPresentation.renewalLine(entitlement("active", source = "Comped"), null, text))
        assertNull(BillingPresentation.renewalLine(entitlement("active", end = null), null, text))
    }

    @Test
    fun `actions follow the billing source`() {
        assertEquals(BillingPresentation.Actions.COMPED, BillingPresentation.actions(entitlement("active", source = "Comped")))
        assertEquals(BillingPresentation.Actions.WEB, BillingPresentation.actions(entitlement("active", source = "Stripe")))
        assertEquals(BillingPresentation.Actions.APPLE, BillingPresentation.actions(entitlement("active", source = "Apple")))
        assertEquals(BillingPresentation.Actions.PLAY, BillingPresentation.actions(entitlement("active", source = "Google")))
        assertEquals(BillingPresentation.Actions.PLAY, BillingPresentation.actions(Entitlement.NONE))
    }

    // MARK: - Play offer copy

    @Test
    fun `ISO periods read like the Play sheet`() {
        assertEquals("month", BillingPresentation.periodName("P1M"))
        assertEquals("year", BillingPresentation.periodName("P1Y"))
        assertEquals("week", BillingPresentation.periodName("P1W"))
        assertEquals("7 days", BillingPresentation.periodName("P7D"))
        assertEquals("period", BillingPresentation.periodName("bogus"))
    }

    @Test
    fun `trial copy comes from the offer period`() {
        assertEquals("Start 7-day free trial", BillingPresentation.ctaText("P7D"))
        assertEquals("Start 7-day free trial", BillingPresentation.ctaText("P1W"))
        assertEquals("Start 1-month free trial", BillingPresentation.ctaText("P1M"))
        assertEquals("Start free trial", BillingPresentation.ctaText("bogus"))
        assertEquals("Subscribe", BillingPresentation.ctaText(null))
        assertEquals("$9.99 / month", BillingPresentation.priceLine("$9.99", "P1M"))
        assertEquals(
            "7-day free trial, then $9.99 / month. Renews automatically until cancelled. " +
                "Cancel anytime in Google Play — cancel before the trial ends and you won't be charged.",
            BillingPresentation.disclosure("$9.99", "P1M", "P1W")
        )
        assertEquals(
            "$79.99 / year. Renews automatically until cancelled. Cancel anytime in Google Play.",
            BillingPresentation.disclosure("$79.99", "P1Y", null)
        )
    }

    @Test
    fun `savings badge compares annual to twelve months`() {
        assertEquals("SAVE 33%", BillingPresentation.savingsBadge(9_990_000L, 79_990_000L))
        assertNull(BillingPresentation.savingsBadge(9_990_000L, 119_880_000L))
        assertNull(BillingPresentation.savingsBadge(null, 79_990_000L))
    }
}
