package com.jobsearchcv.backend.domain.model

import java.time.Instant

/**
 * Cached subscription data fetched from Stripe API.
 * This is NOT persisted in MongoDB - only held in memory cache for 5 minutes.
 *
 * Stripe API is the source of truth. This cache reduces API calls.
 */
data class StripeSubscriptionData(
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val currentPeriodEnd: Instant?,
    val trialEnd: Instant?,
    val isTrialCancelled: Boolean,
    val cachedAt: Instant = Instant.now()
) {
    /**
     * Check if user has premium access based on Stripe subscription data.
     *
     * Logic:
     * 1. Must be PREMIUM tier
     * 2. Status must be ACTIVE or TRIALING
     * 3. Current period or trial must not be expired
     */
    fun hasPremiumAccess(): Boolean {
        if (tier != SubscriptionTier.PREMIUM) return false
        if (status !in listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)) return false

        val now = Instant.now()

        // Check if current period or trial is still valid
        val periodValid = currentPeriodEnd?.let { it.isAfter(now) } ?: false
        val trialValid = trialEnd?.let { it.isAfter(now) } ?: false

        return periodValid || trialValid
    }
}

/**
 * Represents the absence of a subscription (free user).
 */
fun createFreeSubscriptionData(): StripeSubscriptionData {
    return StripeSubscriptionData(
        tier = SubscriptionTier.FREE,
        status = SubscriptionStatus.ACTIVE,
        currentPeriodEnd = null,
        trialEnd = null,
        isTrialCancelled = false
    )
}
