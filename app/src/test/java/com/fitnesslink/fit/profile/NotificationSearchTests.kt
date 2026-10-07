package com.fitnesslink.fit.profile

import com.fitnesslink.fit.ui.notifications.NotificationSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSearchTests {
    private val title = "Your workout plan is ready"
    private val body = "Meal plan for the week has been generated"

    @Test
    fun `empty query matches everything`() {
        assertTrue(NotificationSearch.matches("", title, body))
        assertTrue(NotificationSearch.matches("   ", title, body))
    }

    @Test
    fun `plain text matches anywhere in title or body, case-insensitively`() {
        assertTrue(NotificationSearch.matches("PLAN", title, body))
        assertTrue(NotificationSearch.matches("generated", title, body))
        assertFalse(NotificationSearch.matches("nutrition", title, body))
    }

    @Test
    fun `star is a wildcard for any run of characters`() {
        assertTrue(NotificationSearch.matches("work*plan", title, body))
        assertTrue(NotificationSearch.matches("*week", title, body))
        assertTrue(NotificationSearch.matches("meal*generated", title, body))
        assertFalse(NotificationSearch.matches("generated*meal", title, body))
    }

    @Test
    fun `wildcard parts must appear in order`() {
        assertFalse(NotificationSearch.matches("ready*workout", title, body))
        assertTrue(NotificationSearch.matches("workout*ready", title, body))
    }

    @Test
    fun `multiple terms must all match`() {
        assertTrue(NotificationSearch.matches("workout meal", title, body))
        assertFalse(NotificationSearch.matches("workout pizza", title, body))
        assertTrue(NotificationSearch.matches("work* *week", title, body))
    }

    @Test
    fun `a bare star matches everything`() {
        assertTrue(NotificationSearch.matches("*", title, body))
        assertTrue(NotificationSearch.matches("**", title, body))
    }
}
