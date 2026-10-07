package com.fitnesslink.fit.profile

import com.fitnesslink.fit.ui.notifications.NotificationPrePromptRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrePromptRuleTests {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `first offer while permission is undetermined`() {
        assertTrue(NotificationPrePromptRule.shouldOffer(permissionUndetermined = true, offerCount = 0, lastOfferedAtMs = 0L, nowMs = now))
    }

    @Test
    fun `never offered once the OS has an answer`() {
        assertFalse(NotificationPrePromptRule.shouldOffer(permissionUndetermined = false, offerCount = 0, lastOfferedAtMs = 0L, nowMs = now))
    }

    @Test
    fun `at most three offers`() {
        assertTrue(NotificationPrePromptRule.shouldOffer(true, offerCount = 2, lastOfferedAtMs = now - 10 * day, nowMs = now))
        assertFalse(NotificationPrePromptRule.shouldOffer(true, offerCount = 3, lastOfferedAtMs = now - 10 * day, nowMs = now))
        assertFalse(NotificationPrePromptRule.shouldOffer(true, offerCount = 7, lastOfferedAtMs = now - 10 * day, nowMs = now))
    }

    @Test
    fun `offers are at least three days apart`() {
        assertFalse(NotificationPrePromptRule.shouldOffer(true, offerCount = 1, lastOfferedAtMs = now - 2 * day, nowMs = now))
        assertFalse(NotificationPrePromptRule.shouldOffer(true, offerCount = 1, lastOfferedAtMs = now - 3 * day + 1, nowMs = now))
        assertTrue(NotificationPrePromptRule.shouldOffer(true, offerCount = 1, lastOfferedAtMs = now - 3 * day, nowMs = now))
    }
}
