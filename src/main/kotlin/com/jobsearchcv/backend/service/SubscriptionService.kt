package com.jobsearchcv.backend.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.jobsearchcv.backend.domain.model.SubscriptionStatus
import com.jobsearchcv.backend.domain.model.SubscriptionStatusResponse
import com.jobsearchcv.backend.domain.model.SubscriptionTier
import com.jobsearchcv.backend.domain.model.StripeSubscriptionData
import com.jobsearchcv.backend.domain.model.UserSubscription
import com.jobsearchcv.backend.domain.model.createFreeSubscriptionData
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.SubscriptionRepository
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

/**
 * Simplified subscription service that fetches subscription data from Stripe API on-demand.
 * MongoDB only stores user_id -> stripe_customer_id mapping.
 * All subscription details are cached in memory for 5 minutes to reduce API calls.
 *
 * This eliminates sync issues - Stripe is the single source of truth.
 */
@Service
class SubscriptionService(
        private val subscriptionRepository: SubscriptionRepository,
        private val stripeService: StripeService,
        private val destinationRepository: DestinationRepository,
        private val emailTemplateService: EmailTemplateService,
        private val asyncEmailService: AsyncEmailService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // In-memory cache: userId -> StripeSubscriptionData (5 min TTL)
    private lateinit var subscriptionCache: Cache<String, StripeSubscriptionData>

    @PostConstruct
    fun initCache() {
        subscriptionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .recordStats()
                .build()
        logger.info("Subscription cache initialized with 5 minute TTL")
    }

    /**
     * Get user's subscription record (only contains user_id and stripe_customer_id mapping).
     */
    fun getSubscription(userId: String): UserSubscription? {
        return subscriptionRepository.findByUserId(userId)
    }

    /**
     * Get full subscription status by fetching from Stripe API (with caching).
     */
    fun getSubscriptionStatus(userId: String): SubscriptionStatusResponse {
        val subscriptionData = fetchSubscriptionDataWithCache(userId)

        return SubscriptionStatusResponse(
                userId = userId,
                tier = subscriptionData.tier,
                status = subscriptionData.status,
                currentPeriodEnd = subscriptionData.currentPeriodEnd,
                trialEnd = subscriptionData.trialEnd,
                hasPremiumAccess = subscriptionData.hasPremiumAccess(),
                isTrialCancelled = subscriptionData.isTrialCancelled,
                cachedAt = subscriptionData.cachedAt
        )
    }

    /**
     * Check if user has premium access.
     * Fetches from Stripe API with 5-minute cache.
     */
    fun checkPremiumAccess(userId: String): Boolean {
        val subscriptionData = fetchSubscriptionDataWithCache(userId)
        return subscriptionData.hasPremiumAccess()
    }

    /**
     * Fetch subscription data from Stripe API with caching.
     * Cache TTL: 5 minutes
     */
    private fun fetchSubscriptionDataWithCache(userId: String): StripeSubscriptionData {
        // Check cache first
        val cached = subscriptionCache.getIfPresent(userId)
        if (cached != null) {
            logger.debug("Cache hit for user: $userId")
            return cached
        }

        // Cache miss - fetch from Stripe
        logger.debug("Cache miss for user: $userId, fetching from Stripe")
        val subscriptionData = fetchSubscriptionDataFromStripe(userId)

        // Cache the result
        subscriptionCache.put(userId, subscriptionData)

        return subscriptionData
    }

    /**
     * Fetch subscription data directly from Stripe API.
     * This is the source of truth for subscription status.
     */
    private fun fetchSubscriptionDataFromStripe(userId: String): StripeSubscriptionData {
        try {
            // Get user's Stripe customer ID from MongoDB
            val userSubscription = getSubscription(userId)
            if (userSubscription == null) {
                logger.debug("No subscription record for user: $userId, returning free tier")
                return createFreeSubscriptionData()
            }

            val customerId = userSubscription.stripeCustomerId

            // Fetch all subscriptions for this customer from Stripe
            val subscriptions = stripeService.listSubscriptionsByCustomer(customerId)

            if (subscriptions.isEmpty()) {
                logger.debug("No Stripe subscriptions found for customer: $customerId, returning free tier")
                return createFreeSubscriptionData()
            }

            // Get the most recent active/trialing subscription (prioritize active subscriptions)
            val activeSubscription = subscriptions
                    .filter { it.status in listOf("active", "trialing", "past_due") }
                    .maxByOrNull { it.created }

            if (activeSubscription == null) {
                logger.debug("No active subscription for customer: $customerId, returning free tier")
                return createFreeSubscriptionData()
            }

            // Extract data from Stripe subscription
            return extractSubscriptionData(activeSubscription)

        } catch (e: Exception) {
            logger.error("Error fetching subscription from Stripe for user: $userId", e)
            // On error, return free tier (fail-safe) but don't cache it (cache only on success)
            return createFreeSubscriptionData()
        }
    }

    /**
     * Extract subscription data from Stripe subscription object.
     */
    private fun extractSubscriptionData(stripeSubscription: Subscription): StripeSubscriptionData {
        val status = mapStripeStatus(stripeSubscription.status)
        val tier = if (status in listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)) {
            SubscriptionTier.PREMIUM
        } else {
            SubscriptionTier.FREE
        }

        // Extract period end (try top-level first, then items)
        val currentPeriodEnd = extractInstant(stripeSubscription, "current_period_end")
                ?: extractNestedInstant(stripeSubscription)

        val trialEnd = extractInstant(stripeSubscription, "trial_end")

        // Check if subscription was cancelled (any cancellation field set)
        val isCancelled = stripeSubscription.cancelAt != null ||
                stripeSubscription.cancelAtPeriodEnd == true ||
                stripeSubscription.canceledAt != null

        return StripeSubscriptionData(
                tier = tier,
                status = status,
                currentPeriodEnd = currentPeriodEnd,
                trialEnd = trialEnd,
                isTrialCancelled = isCancelled && status == SubscriptionStatus.TRIALING
        )
    }

    /**
     * Extract timestamp field from Stripe subscription JSON.
     */
    private fun extractInstant(stripeSubscription: Subscription, fieldName: String): Instant? {
        return try {
            stripeSubscription.rawJsonObject
                    ?.get(fieldName)
                    ?.takeIf { !it.isJsonNull }
                    ?.asLong
                    ?.let { Instant.ofEpochSecond(it) }
        } catch (e: Exception) {
            logger.debug("Failed to extract $fieldName from subscription ${stripeSubscription.id}: ${e.message}")
            null
        }
    }

    /**
     * Extract current_period_end from subscription items (fallback for trialing subscriptions).
     */
    private fun extractNestedInstant(stripeSubscription: Subscription): Instant? {
        return try {
            val items = stripeSubscription.rawJsonObject?.getAsJsonObject("items")
            val data = items?.getAsJsonArray("data")
            val firstItem = data?.get(0)?.asJsonObject

            firstItem?.get("current_period_end")
                    ?.takeIf { !it.isJsonNull }
                    ?.asLong
                    ?.let { Instant.ofEpochSecond(it) }
        } catch (e: Exception) {
            logger.debug("Failed to extract nested current_period_end: ${e.message}")
            null
        }
    }

    /**
     * Invalidate cache for a user (called by webhooks to force refresh).
     */
    fun invalidateCache(userId: String) {
        subscriptionCache.invalidate(userId)
        logger.debug("Invalidated cache for user: $userId")
    }

    /**
     * Create or update subscription record (only stores userId -> stripeCustomerId mapping).
     */
    fun createOrUpdateSubscription(subscription: UserSubscription): UserSubscription {
        val existing = subscriptionRepository.findByUserId(subscription.userId)
        return if (existing != null) {
            logger.info("Updating subscription mapping for user: ${subscription.userId}")
            subscriptionRepository.save(
                    subscription.copy(id = existing.id, createdAt = existing.createdAt)
            )
        } else {
            logger.info("Creating subscription mapping for user: ${subscription.userId}")
            subscriptionRepository.save(subscription)
        }
    }

    /**
     * Handle checkout completed webhook - only used for sending welcome email.
     * Subscription data will be fetched from Stripe API when needed.
     */
    suspend fun handleCheckoutCompleted(userId: String, session: Session) {
        logger.info("Handling checkout completed for user: $userId, session: ${session.id}")

        // Validate session has required fields
        session.subscription
                ?: throw IllegalArgumentException("No subscription ID in checkout session ${session.id}")
        val customerId = session.customer
                ?: throw IllegalArgumentException("No customer ID in checkout session ${session.id}")

        // Create/update the mapping (userId -> stripeCustomerId)
        val subscription = UserSubscription(
                userId = userId,
                stripeCustomerId = customerId
        )
        createOrUpdateSubscription(subscription)

        // Invalidate cache to force fresh fetch
        invalidateCache(userId)

        // Send welcome email (async - doesn't block webhook response)
        sendWelcomeEmail(userId)
    }

    /**
     * Handle subscription updated webhook - invalidate cache and send notifications.
     */
    fun handleSubscriptionUpdated(stripeSubscription: Subscription) {
        logger.info("Handling subscription updated: ${stripeSubscription.id}")

        // Find user by customer ID
        val customerId = stripeSubscription.customer ?: return
        val userSubscription = subscriptionRepository.findByStripeCustomerId(customerId) ?: run {
            logger.warn("No user subscription found for customer: $customerId")
            return
        }

        // Invalidate cache to force fresh fetch on next access
        invalidateCache(userSubscription.userId)

        logger.info("Cache invalidated for subscription update: ${stripeSubscription.id}")
    }

    /**
     * Handle subscription deleted webhook - invalidate cache.
     */
    fun handleSubscriptionDeleted(stripeSubscription: Subscription) {
        logger.info("Handling subscription deleted: ${stripeSubscription.id}")

        val customerId = stripeSubscription.customer ?: return
        val userSubscription = subscriptionRepository.findByStripeCustomerId(customerId) ?: run {
            logger.warn("No user subscription found for customer: $customerId")
            return
        }

        // Invalidate cache
        invalidateCache(userSubscription.userId)

        logger.info("Cache invalidated for subscription deletion: ${stripeSubscription.id}")
    }

    /**
     * Ensure user has a Stripe customer ID.
     * Creates Stripe customer if needed and stores mapping in MongoDB.
     */
    fun ensureStripeCustomer(userId: String, email: String, name: String? = null): String {
        // Check if user already has a subscription record
        val existingSubscription = getSubscription(userId)

        // If user has a Stripe customer ID, return it
        if (existingSubscription != null) {
            logger.info("User $userId already has Stripe customer: ${existingSubscription.stripeCustomerId}")
            return existingSubscription.stripeCustomerId
        }

        // Create new Stripe customer
        val customer = stripeService.createCustomer(userId, email, name)
        logger.info("Created Stripe customer ${customer.id} for user: $userId")

        // Create subscription mapping
        val subscription = UserSubscription(
                userId = userId,
                stripeCustomerId = customer.id
        )
        createOrUpdateSubscription(subscription)

        return customer.id
    }

    /**
     * Handle trial will end webhook - send notification email only.
     */
    suspend fun handleTrialWillEnd(stripeSubscription: Subscription) {
        logger.info("Handling trial will end for subscription: ${stripeSubscription.id}")

        val customerId = stripeSubscription.customer ?: return
        val userSubscription = subscriptionRepository.findByStripeCustomerId(customerId)

        if (userSubscription != null) {
            sendTrialEndingEmail(userSubscription.userId)
        } else {
            logger.warn("No user subscription found for Stripe subscription: ${stripeSubscription.id}")
        }
    }

    /**
     * Handle payment failed webhook - send notification email only.
     */
    suspend fun handlePaymentFailed(invoice: com.stripe.model.Invoice) {
        logger.warn("Handling payment failed for invoice: ${invoice.id}")

        val subscriptionId = invoice.rawJsonObject?.get("subscription")?.asString
        if (subscriptionId != null) {
            val customerId = invoice.customer
            if (customerId != null) {
                val userSubscription = subscriptionRepository.findByStripeCustomerId(customerId)
                if (userSubscription != null) {
                    // Invalidate cache immediately on payment failure
                    invalidateCache(userSubscription.userId)
                    sendPaymentFailedEmail(userSubscription.userId)
                }
            }
        } else {
            logger.warn("No subscription ID found in invoice ${invoice.id}, cannot send payment failed email")
        }
    }

    private suspend fun sendTrialEndingEmail(userId: String) {
        try {
            val destinations = destinationRepository.findByUserId(userId)
            val emailDestination = destinations.find { it.channel == "email" }

            if (emailDestination == null) {
                logger.warn("No email destination found for trial ending email to user: $userId")
                return
            }

            val email = emailDestination.channelValue
            val emailContent = emailTemplateService.createTrialEndingEmail(email)

            asyncEmailService.sendEmailWithRetry(emailContent, maxRetries = 2)
            logger.info("Queued trial ending email for user: $userId")
        } catch (e: Exception) {
            logger.error("Error queuing trial ending email for user: $userId", e)
        }
    }

    private suspend fun sendPaymentFailedEmail(userId: String) {
        try {
            val destinations = destinationRepository.findByUserId(userId)
            val emailDestination = destinations.find { it.channel == "email" }

            if (emailDestination == null) {
                logger.warn("No email destination found for payment failed email to user: $userId")
                return
            }

            val email = emailDestination.channelValue
            val emailContent = emailTemplateService.createPaymentFailedEmail(email)

            asyncEmailService.sendEmailWithRetry(emailContent, maxRetries = 3)
            logger.info("Queued payment failed email for user: $userId")
        } catch (e: Exception) {
            logger.error("Error queuing payment failed email for user: $userId", e)
        }
    }

    private suspend fun sendWelcomeEmail(userId: String) {
        try {
            val destinations = destinationRepository.findByUserId(userId)
            val emailDestination = destinations.find { it.channel == "email" }

            if (emailDestination == null) {
                logger.warn("No email destination found for welcome email to user: $userId")
                return
            }

            val email = emailDestination.channelValue
            val emailContent = emailTemplateService.createWelcomeEmail(email)

            asyncEmailService.sendEmailAsync(emailContent)
            logger.info("Queued welcome email for user: $userId")
        } catch (e: Exception) {
            logger.error("Error queuing welcome email for user: $userId", e)
        }
    }

    private fun mapStripeStatus(stripeStatus: String): SubscriptionStatus {
        return when (stripeStatus) {
            "active" -> SubscriptionStatus.ACTIVE
            "trialing" -> SubscriptionStatus.TRIALING
            "canceled" -> SubscriptionStatus.CANCELED
            "past_due" -> SubscriptionStatus.PAST_DUE
            "incomplete", "incomplete_expired" -> SubscriptionStatus.INCOMPLETE
            else -> {
                logger.warn("Unknown Stripe status: $stripeStatus, defaulting to INCOMPLETE")
                SubscriptionStatus.INCOMPLETE
            }
        }
    }
}
