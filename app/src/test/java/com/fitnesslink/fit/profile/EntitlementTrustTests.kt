package com.fitnesslink.fit.profile

import com.fitnesslink.fit.billing.EntitlementState
import com.fitnesslink.fit.billing.EntitlementTrust
import com.fitnesslink.fit.network.dto.Entitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EntitlementTrustTests {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun entitled(endOffsetMs: Long?, status: String = "active") = Entitlement(
        isEntitled = true, status = status, source = "Google",
        currentPeriodEnd = endOffsetMs?.let { Date(now + it) }
    )

    // MARK: - trust window

    @Test
    fun `cached entitlement is trusted while its period is still running`() {
        assertTrue(EntitlementTrust.isTrusted(entitled(5 * hour), now))
    }

    @Test
    fun `cached entitlement is not trusted once its period has ended`() {
        assertFalse(EntitlementTrust.isTrusted(entitled(-1), now))
    }

    @Test
    fun `cached entitlement with no period end is trusted`() {
        assertTrue(EntitlementTrust.isTrusted(entitled(null), now))
    }

    @Test
    fun `a cached not-entitled answer is never trusted`() {
        assertFalse(EntitlementTrust.isTrusted(Entitlement.NONE, now))
        assertFalse(EntitlementTrust.isTrusted(null, now))
    }

    // MARK: - fail closed

    @Test
    fun `refresh failure keeps a live cache`() {
        val cached = entitled(5 * hour)
        val state = EntitlementTrust.onRefreshFailed(cached, now, "offline")
        assertTrue(state is EntitlementState.Entitled)
        assertEquals(cached, (state as EntitlementState.Entitled).entitlement)
    }

    @Test
    fun `refresh failure with a lapsed cache fails closed`() {
        val state = EntitlementTrust.onRefreshFailed(entitled(-hour), now, "offline")
        assertTrue(state is EntitlementState.Error)
        assertFalse(state.isEntitled)
    }

    @Test
    fun `refresh failure with no cache fails closed`() {
        val state = EntitlementTrust.onRefreshFailed(null, now, "offline")
        assertTrue(state is EntitlementState.Error)
        assertEquals("offline", (state as EntitlementState.Error).message)
    }

    @Test
    fun `server answers map to Entitled or NotEntitled`() {
        assertTrue(EntitlementTrust.fromServer(entitled(5 * hour)) is EntitlementState.Entitled)
        val no = EntitlementTrust.fromServer(Entitlement(isEntitled = false, status = "expired"))
        assertTrue(no is EntitlementState.NotEntitled)
        assertEquals("expired", no.entitlement.status)
    }

    // MARK: - near expiry

    @Test
    fun `near expiry within 24 hours`() {
        assertTrue(EntitlementTrust.isNearExpiry(entitled(23 * hour), now))
        assertTrue(EntitlementTrust.isNearExpiry(entitled(-hour), now))
        assertFalse(EntitlementTrust.isNearExpiry(entitled(25 * hour), now))
        assertFalse(EntitlementTrust.isNearExpiry(entitled(null), now))
        assertFalse(EntitlementTrust.isNearExpiry(Entitlement.NONE, now))
    }
}
