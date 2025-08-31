package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.repository.SubscriptionRepository
import com.stripe.model.Subscription
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SubscriptionSyncService(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    fun syncSubscriptionsWithStripe() {
        logger.info("Starting subscription sync with Stripe")
        
        val subscriptions = subscriptionRepository.findAll()
            .filter { it.stripeSubscriptionId != null }
        
        var syncedCount = 0
        var errorCount = 0
        
        subscriptions.forEach { userSubscription ->
            try {
                val stripeSubscriptionId = userSubscription.stripeSubscriptionId!!
                val stripeSubscription = Subscription.retrieve(stripeSubscriptionId)
                
                // Check if local data differs from Stripe
                val currentStatus = subscriptionService.mapStripeStatus(stripeSubscription.status)
                if (currentStatus != userSubscription.status) {
                    logger.info("Syncing subscription ${userSubscription.id}: ${userSubscription.status} -> $currentStatus")
                    subscriptionService.handleSubscriptionUpdated(stripeSubscription)
                    syncedCount++
                }
                
            } catch (e: Exception) {
                logger.error("Failed to sync subscription ${userSubscription.id}", e)
                errorCount++
            }
        }
        
        logger.info("Subscription sync completed. Synced: $syncedCount, Errors: $errorCount")
    }
    
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    fun cleanupOldWebhookEvents() {
        // TODO: Implement cleanup of old webhook events (older than 30 days)
        logger.info("Webhook event cleanup scheduled")
    }
}