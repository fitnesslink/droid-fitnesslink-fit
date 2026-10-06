package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.CheckoutContext
import com.fitnesslink.fit.network.dto.Entitlement
import com.fitnesslink.fit.network.dto.RestorePurchaseRequest
import com.fitnesslink.fit.network.dto.SubscriptionDto
import com.fitnesslink.fit.network.dto.SubscriptionPlan
import com.fitnesslink.fit.network.dto.VerifyGooglePurchaseRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * FTNSSLNK-281. Thin wrappers over `billing/api/v1` (base URL
 * `ApiService.BILLING`). Mirrors iOS `BillingService`: the app never decides
 * it is entitled, it asks. Play Billing produces the purchase token; the
 * server verifies and links it and answers the entitlement question.
 */
interface BillingApi {
    /** Plans + the per-store product ids a paywall purchases from. Display prices are reporting-only. */
    @GET("plans")
    suspend fun getPlans(): List<SubscriptionPlan>

    /** The single content-access gate. Cheap — call on login, foreground, after any purchase. */
    @GET("entitlement/me")
    suspend fun getEntitlement(): Entitlement

    /** Call before opening the purchase sheet; also ensures the backend Customer row exists. */
    @GET("customers/me/checkout-context")
    suspend fun getCheckoutContext(): CheckoutContext

    /** The raw subscription row behind the manage screen. 404 (`ApiError.NotFound`) when never subscribed. */
    @GET("subscriptions/me")
    suspend fun getMySubscription(): SubscriptionDto

    /**
     * Re-link an existing store subscription after reinstall / new device.
     * Google: `RestorePurchaseRequest.google(purchaseToken)`. 409 =
     * owned by another FitnessLink account.
     */
    @POST("subscriptions/restore")
    suspend fun restore(@Body request: RestorePurchaseRequest): Entitlement

    /**
     * TODO(backend): no Google verify route exists on the platform yet —
     * only `subscriptions/apple/verify` (JWS) and `subscriptions/restore`
     * (which accepts a Google `purchaseToken`). Path and body are provisional;
     * confirm with the backend agent before wiring Play Billing to it.
     */
    @POST("subscriptions/google/verify")
    suspend fun verifyGoogle(@Body request: VerifyGooglePurchaseRequest): Entitlement
}
