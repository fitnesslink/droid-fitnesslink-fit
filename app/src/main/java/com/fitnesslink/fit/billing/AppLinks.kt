package com.fitnesslink.fit.billing

/**
 * Outbound links the app has to point at (port of iOS `AppLinks.swift`).
 *
 * Google Play's subscription policy, like App Review 3.1.2, requires the
 * paywall to link to working Terms of Use and Privacy Policy pages. The www
 * host is deliberate: the apex `fitnesslink.fit` points at a project that
 * 404s everything; the site is served from www (verified live 2026-08-30).
 */
object AppLinks {
    const val TERMS_OF_USE = "https://www.fitnesslink.fit/terms"
    const val PRIVACY_POLICY = "https://www.fitnesslink.fit/privacy"

    /** Where a Stripe (web) subscriber manages their subscription — Play can't touch it. */
    const val WEB_BILLING_PORTAL = "https://www.fitnesslink.fit/account/billing"

    /** Play's subscription centre; with `sku` + `package` it opens the specific subscription. */
    fun playSubscriptions(productId: String? = null, packageName: String? = null): String {
        if (productId == null || packageName == null) return "https://play.google.com/store/account/subscriptions"
        return "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName"
    }
}
