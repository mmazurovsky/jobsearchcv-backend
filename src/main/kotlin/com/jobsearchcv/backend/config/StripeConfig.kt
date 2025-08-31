package com.jobsearchcv.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "stripe")
class StripeConfig {
    lateinit var secretKey: String
    lateinit var webhookSecret: String
    
    // Payment Links and Customer Portal URLs are configured in Stripe Dashboard
    // Frontend uses these environment variables directly:
    // - STRIPE_PREMIUM_CHECKOUT_URL (payment link from Stripe)
    // - STRIPE_CUSTOMER_PORTAL_URL (customer portal from Stripe)
}