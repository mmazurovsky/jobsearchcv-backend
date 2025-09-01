package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.SubscriptionRepository
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService,
    private val resendEmailService: ResendEmailService,
    private val destinationRepository: DestinationRepository,
    private val emailTemplateService: EmailTemplateService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun getSubscription(userId: String): UserSubscription? {
        return subscriptionRepository.findByUserId(userId)
    }
    
    fun getSubscriptionStatus(userId: String): SubscriptionStatusResponse {
        val subscription = getSubscription(userId)
        
        return if (subscription != null) {
            SubscriptionStatusResponse(
                userId = userId,
                tier = subscription.tier,
                status = subscription.status,
                currentPeriodEnd = subscription.currentPeriodEnd,
                trialEnd = subscription.trialEnd,
                hasPremiumAccess = checkPremiumAccess(userId)
            )
        } else {
            // Default free subscription
            SubscriptionStatusResponse(
                userId = userId,
                tier = SubscriptionTier.FREE,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = null,
                trialEnd = null,
                hasPremiumAccess = false
            )
        }
    }
    
    fun createOrUpdateSubscription(subscription: UserSubscription): UserSubscription {
        val existing = subscriptionRepository.findByUserId(subscription.userId)
        return if (existing != null) {
            logger.info("Updating existing subscription for user: ${subscription.userId}")
            subscriptionRepository.save(subscription.copy(
                id = existing.id,
                createdAt = existing.createdAt
            ))
        } else {
            logger.info("Creating new subscription for user: ${subscription.userId}")
            subscriptionRepository.save(subscription)
        }
    }
    
    fun handleCheckoutCompleted(userId: String, session: Session) {
        logger.info("Handling checkout completed for user: $userId, session: ${session.id}")
        
        val subscription = UserSubscription(
            userId = userId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.TRIALING,
            stripeCustomerId = session.customer,
            stripeSubscriptionId = session.subscription,
            trialEnd = OffsetDateTime.now().plusDays(3),
            currentPeriodEnd = OffsetDateTime.now().plusDays(3)
        )
        
        createOrUpdateSubscription(subscription)
    }
    
    fun handleSubscriptionCreated(stripeSubscription: Subscription) {
        logger.info("Handling subscription created: ${stripeSubscription.id}")
        
        val customerId = stripeSubscription.customer
        val existing = subscriptionRepository.findByStripeCustomerId(customerId)
        
        if (existing != null) {
            val updated = existing.copy(
                status = mapStripeStatus(stripeSubscription.status),
                stripeSubscriptionId = stripeSubscription.id,
                currentPeriodEnd = epochToOffsetDateTime(stripeSubscription.currentPeriodEnd),
                trialEnd = stripeSubscription.trialEnd?.let { epochToOffsetDateTime(it) },
                updatedAt = OffsetDateTime.now()
            )
            subscriptionRepository.save(updated)
            logger.info("Updated subscription for customer: $customerId")
        } else {
            logger.warn("No subscription found for customer: $customerId")
        }
    }
    
    fun handleSubscriptionUpdated(stripeSubscription: Subscription) {
        logger.info("Handling subscription updated: ${stripeSubscription.id}")
        
        val existing = subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.id)
        
        if (existing != null) {
            val updated = existing.copy(
                status = mapStripeStatus(stripeSubscription.status),
                currentPeriodEnd = epochToOffsetDateTime(stripeSubscription.currentPeriodEnd),
                trialEnd = stripeSubscription.trialEnd?.let { epochToOffsetDateTime(it) },
                updatedAt = OffsetDateTime.now()
            )
            subscriptionRepository.save(updated)
            logger.info("Updated subscription: ${stripeSubscription.id}")
        } else {
            logger.warn("No subscription found with ID: ${stripeSubscription.id}")
        }
    }
    
    fun handleSubscriptionDeleted(stripeSubscription: Subscription) {
        logger.info("Handling subscription deleted: ${stripeSubscription.id}")
        
        val existing = subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.id)
        
        if (existing != null) {
            val updated = existing.copy(
                status = SubscriptionStatus.CANCELED,
                updatedAt = OffsetDateTime.now()
            )
            subscriptionRepository.save(updated)
            logger.info("Marked subscription as canceled: ${stripeSubscription.id}")
        } else {
            logger.warn("No subscription found with ID: ${stripeSubscription.id}")
        }
    }
    
    fun checkPremiumAccess(userId: String): Boolean {
        val subscription = getSubscription(userId) ?: return false
        
        if (subscription.tier != SubscriptionTier.PREMIUM) {
            return false
        }
        
        return when (subscription.status) {
            SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING -> {
                // Active subscriptions have access until period end
                subscription.currentPeriodEnd?.isAfter(OffsetDateTime.now()) ?: true
            }
            SubscriptionStatus.PAST_DUE -> {
                // No grace period - immediate access loss when payment fails
                // Users must update payment method and Stripe will retry
                false
            }
            SubscriptionStatus.CANCELED, SubscriptionStatus.INCOMPLETE -> {
                false // No access
            }
        }
    }
    
    fun ensureStripeCustomer(userId: String, email: String, name: String? = null): String {
        // Check if user already has a Stripe customer
        val existingSubscription = getSubscription(userId)
        if (existingSubscription?.stripeCustomerId != null) {
            logger.info("User $userId already has Stripe customer: ${existingSubscription.stripeCustomerId}")
            return existingSubscription.stripeCustomerId
        }
        
        // Create new Stripe customer
        val customer = stripeService.createCustomer(userId, email, name)
        
        // Create FREE subscription record with Stripe customer ID
        val freeSubscription = UserSubscription(
            userId = userId,
            tier = SubscriptionTier.FREE,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodEnd = null,
            trialEnd = null,
            stripeCustomerId = customer.id,
            stripeSubscriptionId = null
        )
        
        createOrUpdateSubscription(freeSubscription)
        logger.info("Created Stripe customer ${customer.id} and FREE subscription for user: $userId")
        
        return customer.id
    }
    
    fun handleTrialWillEnd(stripeSubscription: Subscription) {
        logger.info("Handling trial will end for subscription: ${stripeSubscription.id}")
        
        val userSubscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.id)
        if (userSubscription != null) {
            sendTrialEndingEmail(userSubscription.userId)
        } else {
            logger.warn("No user subscription found for Stripe subscription: ${stripeSubscription.id}")
        }
    }
    
    fun handlePaymentFailed(invoice: com.stripe.model.Invoice) {
        logger.warn("Handling payment failed for invoice: ${invoice.id}")
        
        val subscriptionId = invoice.subscription
        if (subscriptionId != null) {
            val userSubscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
            if (userSubscription != null) {
                sendPaymentFailedEmail(userSubscription.userId)
            }
        }
    }
    
    private fun sendTrialEndingEmail(userId: String) {
        try {
            // Get user subscription to find email
            val subscription = getSubscription(userId)
            if (subscription?.stripeCustomerId == null) {
                logger.warn("No Stripe customer found for user: $userId")
                return
            }
            
            // Get email from destination (since we create customers from destinations)
            val destinations = runBlocking {
                destinationRepository.findByUserId(userId)
            }
            
            val emailDestination = destinations.find { it.channel == "email" }
            if (emailDestination == null) {
                logger.warn("No email destination found for user: $userId")
                return
            }
            
            val email = emailDestination.channelValue
            
            runBlocking {
                val emailContent = emailTemplateService.createTrialEndingEmail(email)
                val result = resendEmailService.sendEmail(emailContent)
                
                result.fold(
                    onSuccess = { logger.info("Trial ending email sent to user: $userId") },
                    onFailure = { logger.error("Failed to send trial ending email to user: $userId", it) }
                )
            }
        } catch (e: Exception) {
            logger.error("Error sending trial ending email to user: $userId", e)
        }
    }
    
    private fun sendPaymentFailedEmail(userId: String) {
        try {
            // Get user subscription to find email
            val subscription = getSubscription(userId)
            if (subscription?.stripeCustomerId == null) {
                logger.warn("No Stripe customer found for user: $userId")
                return
            }
            
            // Get email from destination (since we create customers from destinations)
            val destinations = runBlocking {
                destinationRepository.findByUserId(userId)
            }
            
            val emailDestination = destinations.find { it.channel == "email" }
            if (emailDestination == null) {
                logger.warn("No email destination found for user: $userId")
                return
            }
            
            val email = emailDestination.channelValue
            
            runBlocking {
                val emailContent = emailTemplateService.createPaymentFailedEmail(email)
                val result = resendEmailService.sendEmail(emailContent)
                
                result.fold(
                    onSuccess = { logger.info("Payment failed email sent to user: $userId") },
                    onFailure = { logger.error("Failed to send payment failed email to user: $userId", it) }
                )
            }
        } catch (e: Exception) {
            logger.error("Error sending payment failed email to user: $userId", e)
        }
    }
    
    fun mapStripeStatus(stripeStatus: String): SubscriptionStatus {
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
    
    private fun epochToOffsetDateTime(epochSeconds: Long): OffsetDateTime {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)
    }
}