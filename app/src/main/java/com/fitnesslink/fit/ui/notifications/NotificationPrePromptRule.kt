package com.fitnesslink.fit.ui.notifications

/**
 * Pre-prompt cadence (iOS `PushNotificationManager.shouldOfferPrePrompt`):
 * the in-app sheet is offered at most [MAX_OFFERS] times, at least
 * [MIN_INTERVAL_MS] apart, and only while the OS permission is still
 * undetermined (never once the system prompt has been shown or answered).
 * Pure so the rule is unit-testable.
 */
object NotificationPrePromptRule {
    const val MAX_OFFERS = 3
    const val MIN_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000

    fun shouldOffer(
        permissionUndetermined: Boolean,
        offerCount: Int,
        lastOfferedAtMs: Long,
        nowMs: Long
    ): Boolean {
        if (!permissionUndetermined) return false
        if (offerCount >= MAX_OFFERS) return false
        if (lastOfferedAtMs > 0 && nowMs - lastOfferedAtMs < MIN_INTERVAL_MS) return false
        return true
    }
}
