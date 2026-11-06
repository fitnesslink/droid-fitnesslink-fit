package com.fitnesslink.fit.profile

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.fitnesslink.fit.billing.AppLinks
import com.fitnesslink.fit.billing.BillingException
import com.fitnesslink.fit.billing.BillingPresentation
import com.fitnesslink.fit.billing.BillingPresentation.ManageAction
import com.fitnesslink.fit.billing.PlayBillingRules
import com.fitnesslink.fit.billing.PlayBillingRules.OfferCandidate
import com.fitnesslink.fit.billing.PlayBillingRules.PricingPhase
import com.fitnesslink.fit.billing.PlayBillingRules.VerifyOutcome
import com.fitnesslink.fit.billing.RestoreResult
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.CheckoutContext
import com.fitnesslink.fit.network.dto.Entitlement
import com.fitnesslink.fit.network.dto.SubscriptionDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.Date

class PlayBillingRulesTests {

    // MARK: - Offer selection (533)

    private val paid = PricingPhase(9_990_000L, "$9.99", "P1M")
    private val free = PricingPhase(0L, "Free", "P7D")
    private val base = OfferCandidate(offerId = null, offerToken = "base-token", phases = listOf(paid))
    private val trial = OfferCandidate(offerId = "free-trial-7d", offerToken = "trial-token", phases = listOf(free, paid))
    private val other = OfferCandidate(offerId = "winback", offerToken = "winback-token", phases = listOf(free, paid))

    @Test
    fun `eligible account with the trial offer from Play gets the trial`() {
        val choice = PlayBillingRules.selectOffer(listOf(base, trial), "free-trial-7d", trialEligible = true)!!
        assertEquals("trial-token", choice.candidate.offerToken)
        assertTrue(choice.isTrial)
        assertEquals("P7D", choice.trialPeriod)
        assertEquals("$9.99", choice.formattedPrice)
        assertEquals("P1M", choice.billingPeriod)
    }

    @Test
    fun `server says ineligible - base plan even though Play offers the trial`() {
        val choice = PlayBillingRules.selectOffer(listOf(base, trial), "free-trial-7d", trialEligible = false)!!
        assertEquals("base-token", choice.candidate.offerToken)
        assertFalse(choice.isTrial)
        assertNull(choice.trialPeriod)
    }

    @Test
    fun `eligible but Play did not return the trial offer - base plan`() {
        val choice = PlayBillingRules.selectOffer(listOf(base), "free-trial-7d", trialEligible = true)!!
        assertEquals("base-token", choice.candidate.offerToken)
        assertFalse(choice.isTrial)
    }

    @Test
    fun `no configured trial offer id - base plan, never some other free offer`() {
        val choice = PlayBillingRules.selectOffer(listOf(other, base, trial), trialOfferId = null, trialEligible = true)!!
        assertEquals("base-token", choice.candidate.offerToken)
    }

    @Test
    fun `older server without trialEligible defers to Play`() {
        assertTrue(PlayBillingRules.selectOffer(listOf(base, trial), "free-trial-7d", trialEligible = null)!!.isTrial)
    }

    @Test
    fun `no offers - nothing to sell`() {
        assertNull(PlayBillingRules.selectOffer(emptyList(), "free-trial-7d", true))
    }

    @Test
    fun `checkout context decodes with and without the Google fields`() {
        val gson = Gson()
        val old = gson.fromJson("""{"stripeCustomerId":"cus_1"}""", CheckoutContext::class.java)
        assertNull(old.trialEligible)
        assertNull(old.googleObfuscatedAccountId)
        val new = gson.fromJson("""{"trialEligible":false,"googleObfuscatedAccountId":"abc"}""", CheckoutContext::class.java)
        assertEquals(false, new.trialEligible)
        assertEquals("abc", new.googleObfuscatedAccountId)
    }

    // MARK: - Response codes

    @Test
    fun `cancel and OK are silent`() {
        assertNull(PlayBillingRules.responseCodeMessage(BillingResponseCode.USER_CANCELED))
        assertNull(PlayBillingRules.responseCodeMessage(BillingResponseCode.OK))
    }

    @Test
    fun `every other response code has copy`() {
        val codes = listOf(
            -3, // SERVICE_TIMEOUT (deprecated constant)
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.ITEM_UNAVAILABLE,
            BillingResponseCode.DEVELOPER_ERROR,
            BillingResponseCode.ERROR,
            BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingResponseCode.ITEM_NOT_OWNED,
            BillingResponseCode.NETWORK_ERROR,
            999
        )
        val messages = codes.map { PlayBillingRules.responseCodeMessage(it) }
        messages.forEach { assertNotNull(it); assertTrue(it!!.isNotBlank()) }
        // Specific copy for each known code — only the unknown one uses the generic line.
        assertEquals(codes.size - 1, messages.dropLast(1).toSet().size)
        assertEquals(PlayBillingRules.GENERIC_STORE_MESSAGE, messages.last())
    }

    @Test
    fun `connection trouble is retryable, product problems are not`() {
        assertTrue(PlayBillingRules.isRetryable(BillingResponseCode.BILLING_UNAVAILABLE))
        assertTrue(PlayBillingRules.isRetryable(BillingResponseCode.SERVICE_DISCONNECTED))
        assertTrue(PlayBillingRules.isRetryable(BillingResponseCode.NETWORK_ERROR))
        assertFalse(PlayBillingRules.isRetryable(BillingResponseCode.ITEM_UNAVAILABLE))
        assertFalse(PlayBillingRules.isRetryable(BillingResponseCode.DEVELOPER_ERROR))
        val err = BillingException.StoreError(BillingResponseCode.BILLING_UNAVAILABLE)
        assertEquals(PlayBillingRules.responseCodeMessage(BillingResponseCode.BILLING_UNAVAILABLE), err.message)
        assertTrue(err.isRetryable)
    }

    // MARK: - Verify / restore HTTP outcomes

    private fun server(code: Int) = ApiError.ServerError(code, "{}")

    @Test
    fun `403 is the sandbox gate`() {
        assertSame(BillingException.TestPurchaseNotAccepted, PlayBillingRules.failureFor(ApiError.Forbidden, false))
        assertSame(BillingException.TestPurchaseNotAccepted, PlayBillingRules.failureFor(server(403), false))
    }

    @Test
    fun `409 is linked to another account`() {
        val f = PlayBillingRules.failureFor(server(409), duringRestore = true)
        assertSame(BillingException.AlreadyLinkedToAnotherAccount, f)
        assertTrue(f.message!!.contains("another FitnessLink account"))
    }

    @Test
    fun `422 is an invalid token`() {
        assertSame(BillingException.PurchaseNotValid, PlayBillingRules.failureFor(server(422), false))
    }

    @Test
    fun `503, other 5xx, network and missing route are retry-later`() {
        listOf<Throwable>(server(503), server(500), ApiError.Offline, ApiError.Timeout, ApiError.NotFound, IOException("x"))
            .forEach { e ->
                val f = PlayBillingRules.failureFor(e, duringRestore = false)
                assertEquals(BillingException.VerificationDelayed(duringRestore = false), f)
                assertFalse(PlayBillingRules.isTerminal(f))
            }
        assertTrue(PlayBillingRules.failureFor(server(503), duringRestore = true).message!!.contains("try again"))
    }

    @Test
    fun `401 is session expired`() {
        assertSame(BillingException.SessionExpired, PlayBillingRules.failureFor(ApiError.Unauthorized, false))
    }

    @Test
    fun `403 409 422 are terminal for the token`() {
        assertTrue(PlayBillingRules.isTerminal(BillingException.TestPurchaseNotAccepted))
        assertTrue(PlayBillingRules.isTerminal(BillingException.AlreadyLinkedToAnotherAccount))
        assertTrue(PlayBillingRules.isTerminal(BillingException.PurchaseNotValid))
    }

    @Test
    fun `pending is never acknowledged, verified is acknowledged only once`() {
        val pending = Entitlement(isEntitled = false, status = "pending", source = "Google")
        assertEquals(VerifyOutcome.PENDING, PlayBillingRules.outcomeOf(pending))
        assertFalse(PlayBillingRules.shouldAcknowledge(VerifyOutcome.PENDING, alreadyAcknowledged = false))
        val active = Entitlement(isEntitled = true, status = "active", source = "Google")
        assertEquals(VerifyOutcome.VERIFIED, PlayBillingRules.outcomeOf(active))
        assertTrue(PlayBillingRules.shouldAcknowledge(VerifyOutcome.VERIFIED, alreadyAcknowledged = false))
        assertFalse(PlayBillingRules.shouldAcknowledge(VerifyOutcome.VERIFIED, alreadyAcknowledged = true))
    }

    // MARK: - Restore (531)

    private val googleActive = Entitlement(isEntitled = true, status = "active", source = "Google")

    @Test
    fun `entitled through another source skips Play`() {
        assertEquals("the App Store", PlayBillingRules.alreadyPremiumSource(Entitlement(true, "active", "Apple")))
        assertEquals("the web", PlayBillingRules.alreadyPremiumSource(Entitlement(true, "active", "Stripe")))
        assertEquals("FitnessLink", PlayBillingRules.alreadyPremiumSource(Entitlement(true, "active", "Comped")))
        assertNull(PlayBillingRules.alreadyPremiumSource(googleActive))
        assertNull(PlayBillingRules.alreadyPremiumSource(Entitlement(false, "expired", "Apple")))
        assertEquals(
            "You already have Premium via the App Store.",
            PlayBillingRules.restoreMessage(RestoreResult.AlreadyPremium(Entitlement(true, "active", "Apple"), "the App Store"))
        )
    }

    @Test
    fun `no purchases reports no active subscription`() {
        val r = PlayBillingRules.summarizeRestore(emptyList(), playHasPendingOnly = false)
        assertEquals(RestoreResult.NothingToRestore, r)
        assertEquals("No active subscription was found for this Google account.", PlayBillingRules.restoreMessage(r))
    }

    @Test
    fun `only a pending purchase reports payment pending`() {
        assertEquals(RestoreResult.PaymentPending, PlayBillingRules.summarizeRestore(emptyList(), playHasPendingOnly = true))
        val pending = Entitlement(false, "pending", "Google")
        assertEquals(RestoreResult.PaymentPending, PlayBillingRules.summarizeRestore(listOf(Result.success(pending)), false))
    }

    @Test
    fun `an entitled restore wins over a collision`() {
        val r = PlayBillingRules.summarizeRestore(
            listOf(Result.failure(BillingException.AlreadyLinkedToAnotherAccount), Result.success(googleActive)),
            false
        )
        assertEquals(RestoreResult.Restored(googleActive), r)
    }

    @Test
    fun `collision is surfaced ahead of a transient failure`() {
        try {
            PlayBillingRules.summarizeRestore(
                listOf(Result.failure(BillingException.VerificationDelayed(true)), Result.failure(BillingException.AlreadyLinkedToAnotherAccount)),
                false
            )
            fail("expected a throw")
        } catch (e: BillingException) {
            assertSame(BillingException.AlreadyLinkedToAnotherAccount, e)
        }
    }

    @Test
    fun `503 on restore becomes a retry message`() {
        try {
            PlayBillingRules.summarizeRestore(listOf(Result.failure(PlayBillingRules.failureFor(server(503), true))), false)
            fail("expected a throw")
        } catch (e: BillingException) {
            assertEquals(BillingException.VerificationDelayed(duringRestore = true), e)
        }
    }

    // MARK: - Billing screen per source (534)

    private fun sub(status: String = "Active", source: String = "Google", cancel: Boolean = false, grace: Date? = null) = SubscriptionDto(
        id = "s", customerId = "c", planId = "p", status = status, source = source, cancelAtPeriodEnd = cancel, gracePeriodEndsAt = grace
    )

    @Test
    fun `manage action per source`() {
        assertEquals(ManageAction.OpenPlay, BillingPresentation.manageAction(googleActive))
        assertEquals(ManageAction.OpenPlay, BillingPresentation.manageAction(Entitlement.NONE))
        val apple = BillingPresentation.manageAction(Entitlement(true, "active", "Apple"))
        assertTrue(apple is ManageAction.Instructions)
        assertTrue((apple as ManageAction.Instructions).text.contains("Settings → Apple ID → Subscriptions"))
        assertEquals(
            ManageAction.OpenUrl("Manage on the web", AppLinks.WEB_BILLING_PORTAL),
            BillingPresentation.manageAction(Entitlement(true, "active", "Stripe"))
        )
        assertEquals(ManageAction.Info(BillingPresentation.COMPED_TEXT), BillingPresentation.manageAction(Entitlement(true, "active", "Comped")))
    }

    @Test
    fun `lapsed user falls back to the subscription row's source`() {
        assertEquals(BillingPresentation.Actions.APPLE, BillingPresentation.actions(Entitlement.NONE, sub(status = "PastDue", source = "Apple")))
    }

    @Test
    fun `play manage link is product-specific when known, generic otherwise`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=com.fitnesslink.monthly&package=fit.fitnesslink",
            AppLinks.playSubscriptions("com.fitnesslink.monthly", "fit.fitnesslink")
        )
        assertEquals("https://play.google.com/store/account/subscriptions", AppLinks.playSubscriptions(null, "com.fitnesslink.fit"))
    }

    @Test
    fun `past due with future grace is grace, keeps access`() {
        val now = 1_000_000L
        val e = Entitlement(true, "grace", "Google")
        val s = sub(status = "PastDue", grace = Date(now + 86_400_000))
        assertTrue(BillingPresentation.isGrace(e, s, now))
        assertFalse(BillingPresentation.isAccountHold(e, s, now))
        assertEquals("PAYMENT ISSUE", BillingPresentation.statusPill(e, s, now).label)
        val banner = BillingPresentation.paymentBanner(e, s, now) { "Nov 1" }!!
        assertEquals(BillingPresentation.BannerKind.GRACE, banner.kind)
        assertTrue(banner.text.contains("in Google Play by Nov 1"))
        assertTrue(banner.opensPlay)
    }

    @Test
    fun `past due with no or past grace is account hold`() {
        val now = 1_000_000L
        val e = Entitlement(false, "expired", "Google")
        listOf(sub(status = "PastDue"), sub(status = "PastDue", grace = Date(now - 1))).forEach { s ->
            assertTrue(BillingPresentation.isAccountHold(e, s, now))
            assertEquals("ON HOLD", BillingPresentation.statusPill(e, s, now).label)
            val banner = BillingPresentation.paymentBanner(e, s, now) { "" }!!
            assertEquals(BillingPresentation.BannerKind.HOLD, banner.kind)
            assertTrue(banner.text.contains("Update your payment method in Google Play"))
        }
    }

    @Test
    fun `apple account hold points at the iPhone, not Play`() {
        val banner = BillingPresentation.paymentBanner(Entitlement(false, "expired", "Apple"), sub(status = "PastDue", source = "Apple"), 0L) { "" }!!
        assertFalse(banner.opensPlay)
        assertTrue(banner.text.contains("iPhone"))
    }

    @Test
    fun `healthy and comped subscriptions have no banner`() {
        assertNull(BillingPresentation.paymentBanner(googleActive, sub(), 0L) { "" })
        assertNull(BillingPresentation.paymentBanner(Entitlement(true, "active", "Comped"), null, 0L) { "" })
    }

    @Test
    fun `pending payment pill`() {
        assertEquals("PAYMENT PENDING", BillingPresentation.statusPill(Entitlement(false, "pending", "Google"), null).label)
    }

    @Test
    fun `cancelling at period end reads Cancels on`() {
        val e = Entitlement(true, "active", "Google", currentPeriodEnd = Date(0))
        assertEquals("Cancels on D", BillingPresentation.renewalLine(e, sub(cancel = true)) { "D" })
    }
}
