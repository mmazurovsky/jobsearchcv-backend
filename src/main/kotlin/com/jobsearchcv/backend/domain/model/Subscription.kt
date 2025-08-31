package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
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
     * In 3-day free trial - user has full premium access
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

@Document(collection = "user_subscriptions")
@CompoundIndexes(
    CompoundIndex(name = "user_id_idx", def = "{'user_id': 1}", unique = true),
    CompoundIndex(name = "stripe_customer_id_idx", def = "{'stripe_customer_id': 1}"),
    CompoundIndex(name = "stripe_subscription_id_idx", def = "{'stripe_subscription_id': 1}")
)
data class UserSubscription(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @field:Field("user_id")
    val userId: String,
    
    @field:Field("tier")
    val tier: SubscriptionTier,
    
    @field:Field("status")
    val status: SubscriptionStatus,
    
    @field:Field("current_period_end")
    val currentPeriodEnd: OffsetDateTime?,
    
    @field:Field("trial_end")
    val trialEnd: OffsetDateTime?,
    
    @field:Field("stripe_customer_id")
    val stripeCustomerId: String?,
    
    @field:Field("stripe_subscription_id")
    val stripeSubscriptionId: String?,
    
    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    
    @field:Field("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)

// Response DTOs

data class SubscriptionStatusResponse(
    val userId: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val currentPeriodEnd: OffsetDateTime?,
    val trialEnd: OffsetDateTime?,
    val hasPremiumAccess: Boolean
)

