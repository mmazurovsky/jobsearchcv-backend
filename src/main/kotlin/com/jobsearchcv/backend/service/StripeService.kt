package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.config.StripeConfig
import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

@Service
class StripeService(
    private val stripeConfig: StripeConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @PostConstruct
    fun init() {
        Stripe.apiKey = stripeConfig.secretKey
        logger.info("Stripe SDK initialized (v30.1.0) with secret key ending in: ...${stripeConfig.secretKey.takeLast(4)}")
        logger.info("NOTE: Webhook endpoint in Stripe Dashboard should be configured to use API version 2025-10-29.clover or earlier")
    }
    
    fun createCustomer(userId: String, email: String?, name: String? = null): Customer {
        val params = CustomerCreateParams.builder()
            .putMetadata("userId", userId)
            .apply {
                email?.let { setEmail(it) }
                name?.let { setName(it) }
            }
            .setDescription("ApplyFirst user: $userId")
            .build()

        val customer = Customer.create(params)
        logger.info("Created Stripe customer ${customer.id} for user: $userId")
        return customer
    }

    fun retrieveCustomer(customerId: String): Customer {
        logger.debug("Retrieving Stripe customer: $customerId")
        return Customer.retrieve(customerId)
    }

    fun retrieveSubscription(subscriptionId: String): Subscription {
        logger.debug("Retrieving Stripe subscription: $subscriptionId")
        return Subscription.retrieve(subscriptionId)
    }

    /**
     * List all subscriptions for a given customer.
     * Returns active, trialing, past_due, canceled, and incomplete subscriptions.
     */
    fun listSubscriptionsByCustomer(customerId: String): List<Subscription> {
        logger.debug("Listing subscriptions for customer: $customerId")
        val params = mapOf("customer" to customerId, "limit" to "10")
        val subscriptions = Subscription.list(params)
        return subscriptions.data
    }

    fun constructWebhookEvent(payload: String, signature: String): Event {
        return try {
            val event = Webhook.constructEvent(payload, signature, stripeConfig.webhookSecret)
            logger.debug("Successfully verified webhook signature for event: ${event.type}")
            event
        } catch (e: Exception) {
            logger.error("Invalid webhook signature", e)
            throw IllegalArgumentException("Invalid webhook signature", e)
        }
    }
}