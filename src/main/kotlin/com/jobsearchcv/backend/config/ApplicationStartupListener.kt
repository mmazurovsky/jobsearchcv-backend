package com.jobsearchcv.backend.config

import com.jobsearchcv.backend.service.JobSearchScheduler
import com.jobsearchcv.backend.service.JobSearchService
import com.jobsearchcv.backend.service.client.ScraperClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlinx.coroutines.runBlocking

@Component
class ApplicationStartupListener(
    private val jobSearchScheduler: JobSearchScheduler,
    private val jobSearchService: JobSearchService,
    private val scraperClient: ScraperClient
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ApplicationStartupListener::class.java)
    }
    
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() = runBlocking {
        try {
            logger.info("Application startup - initializing services")

            // Check scraper service connection
            try {
                val health = scraperClient.checkHealth()
                logger.info("Scraper service health check: {}", health)
            } catch (e: Exception) {
                logger.warn("Scraper service health check failed - service may not be available", e)
            }
            
            // Check proxy connection through scraper service
            try {
                val proxyCheck = scraperClient.checkProxyConnection()
                logger.info("Proxy connection check: {}", proxyCheck)
                
                val isProxyWorking = proxyCheck["success"] as? Boolean ?: false
                if (isProxyWorking) {
                    logger.info("✅ Proxy connection is working properly")
                } else {
                    logger.warn("⚠️ Proxy connection failed: {}", proxyCheck["message"])
                }
            } catch (e: Exception) {
                logger.error("❌ Error checking proxy connection - scraper service may not be available", e)
            }
            
            // Initialize job search service
            jobSearchService.initialize()
            
            logger.info("Application startup completed successfully")
            
        } catch (e: Exception) {
            logger.error("Error during application startup", e)
        }
    }
} 