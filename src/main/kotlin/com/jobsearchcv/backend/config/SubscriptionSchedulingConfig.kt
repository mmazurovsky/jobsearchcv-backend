package com.jobsearchcv.backend.config

import com.jobsearchcv.backend.service.SubscriptionAwareSchedulingService
import com.jobsearchcv.backend.service.SubscriptionService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

@Configuration
class SubscriptionSchedulingConfig(
    private val subscriptionAwareSchedulingService: SubscriptionAwareSchedulingService,
    private val subscriptionService: SubscriptionService
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionSchedulingConfig::class.java)
    }
    
    @PostConstruct
    fun configureCircularDependencies() {
        try {
            // Set up the circular dependencies after beans are created
            // The internal JobSearchScheduler is now handled within SubscriptionAwareSchedulingService
            subscriptionService.setSubscriptionAwareSchedulingService(subscriptionAwareSchedulingService)
            
            logger.info("Successfully configured subscription-aware scheduling integration with circular dependencies")
        } catch (e: Exception) {
            logger.error("Failed to configure subscription-aware scheduling integration", e)
            throw e
        }
    }
}