package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.config.StripeConfig
import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Event
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
        logger.info("Stripe SDK initialized with secret key ending in: ...${stripeConfig.secretKey.takeLast(4)}")
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