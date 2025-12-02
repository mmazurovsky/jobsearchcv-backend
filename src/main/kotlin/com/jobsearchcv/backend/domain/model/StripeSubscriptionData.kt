package com.jobsearchcv.backend.domain.model

import java.time.Instant

/**
 * Cached subscription data fetched from Stripe API.
 * This is NOT persisted in MongoDB - only held in memory cache for 5 minutes.
 *
 * Stripe API is the source of truth. This cache reduces API calls.
 * We trust Stripe's status completely - no need to cache period/trial dates.
 */
data class StripeSubscriptionData(
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val billingInterval: String? = null,  // "week" or "month" - for UI display
    val cachedAt: Instant = Instant.now()
) {
    /**
     * Check if user has premium access based on Stripe subscription data.
     *
     * Logic:
     * - Trust Stripe's status completely (source of truth)
     * - PREMIUM tier + ACTIVE or TRIALING status = access granted
     *
     * Stripe validates payment, billing cycles, and expiration on their end.
     * If Stripe reports status as ACTIVE or TRIALING, the subscription is valid.
     */
    fun hasPremiumAccess(): Boolean {
        return tier == SubscriptionTier.PREMIUM &&
               status in listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)
    }
}

/**
 * Represents the absence of a subscription (free user).
 */
fun createFreeSubscriptionData(): StripeSubscriptionData {
    return StripeSubscriptionData(
        tier = SubscriptionTier.FREE,
        status = SubscriptionStatus.ACTIVE
    )
}
