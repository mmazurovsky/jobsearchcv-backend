package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

enum class SubscriptionTier {
    FREE,
    PREMIUM
}

enum class SubscriptionStatus {
    /**
     * Active paid subscription - user has full premium access
     * Updated via: customer.subscription.updated webhook when payment succeeds
     */
    ACTIVE,
    
    /**
     * Updated via: checkout.session.completed (initial) or customer.subscription.updated
     */
    TRIALING,
    
    /**
     * User canceled subscription - loses premium access at period end
     * Updated via: customer.subscription.deleted webhook
     */
    CANCELED,
    
    /**
     * Payment failed but subscription still exists - immediate access loss
     * Updated via: customer.subscription.updated webhook when payment fails
     * Note: Stripe will retry payments automatically via Smart Retries
     */
    PAST_DUE,
    
    /**
     * Payment incomplete/failed during signup - no premium access
     * Updated via: customer.subscription.updated webhook for incomplete payments
     */
    INCOMPLETE
}

/**
 * Simplified subscription record that only stores the mapping between user and Stripe customer.
 * All subscription details are fetched from Stripe API on-demand with caching.
 *
 * This eliminates sync issues - Stripe is the single source of truth.
 */
@Document(collection = "user_subscriptions")
@CompoundIndexes(
    CompoundIndex(name = "user_id_idx", def = "{'user_id': 1}", unique = true),
    CompoundIndex(name = "stripe_customer_id_idx", def = "{'stripe_customer_id': 1}")
)
data class UserSubscription(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @field:Field("user_id")
    val userId: String,

    /**
     * Stripe customer ID for this user.
     * Used to fetch subscription details from Stripe API.
     */
    @field:Field("stripe_customer_id")
    val stripeCustomerId: String,

    /**
     * Email address used for Stripe checkout and subscription.
     * Required for building checkout URLs with prefilled_email parameter.
     */
    @field:Field("email")
    val email: String,

    /**
     * Billing interval: "week" or "month"
     * Determines which plan the user is subscribed to (Weekly vs Monthly)
     */
    @field:Field("billing_interval")
    val billingInterval: String? = null,

    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @field:Field("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)

// Response DTOs

/**
 * Subscription status response with data fetched from Stripe API.
 * Trust Stripe's status completely - no need to return period/trial dates.
 */
data class SubscriptionStatusResponse(
    val userId: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val billingInterval: String? = null,  // "week" or "month" - for displaying plan name in UI
    val hasPremiumAccess: Boolean,
    val cachedAt: Instant,
    val email: String? = null  // Email for building checkout URL with prefilled_email parameter
)

