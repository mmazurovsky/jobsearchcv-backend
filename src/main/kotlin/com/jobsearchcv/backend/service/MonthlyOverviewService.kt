package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.TimePeriod
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.repository.UserPreferencesRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime

@Service
class MonthlyOverviewService(
    private val jobSearchRepository: JobSearchRepository,
    private val scraperJobService: ScraperJobService,
    private val subscriptionService: SubscriptionService,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MonthlyOverviewService::class.java)
    }

    suspend fun triggerMonthlyOverviewForUser(userId: String) {
        try {
            logger.debug("Triggering monthly overview for user: $userId")

            // Check if user has premium access - skip if premium
            val hasPremiumAccess = subscriptionService.checkPremiumAccess(userId)
            if (hasPremiumAccess) {
                logger.info("Skipping monthly overview for premium user: $userId")
                return
            }

            // Fetch or create user preferences
            var preferences = userPreferencesRepository.findByUserId(userId)
            if (preferences == null) {
                logger.debug("No preferences found for user $userId, creating default")
                preferences = com.jobsearchcv.backend.domain.model.UserPreferences.createDefault(userId)
                preferences = userPreferencesRepository.save(preferences)
            }

            // Check if free overview already sent
            if (preferences.freeMonthlyOverviewSentAt != null) {
                logger.info("Free monthly overview already sent to user $userId at ${preferences.freeMonthlyOverviewSentAt}")
                return
            }

            // Get all approved and subscribed job searches for the user
            val approvedSearches = jobSearchRepository.findByUserIdAndIsApprovedAndIsSubscribed(userId, true, true)
            
            if (approvedSearches.isEmpty()) {
                logger.warn("No approved and subscribed searches found for user: $userId")
                return
            }
            
            logger.debug("Found ${approvedSearches.size} approved searches for monthly overview for user: $userId")

            // Trigger each search with 1-month time period and "Monthly Overview" search name
            approvedSearches.forEach { jobSearch ->
                try {
                    logger.info("Triggering monthly overview for job search: ${jobSearch.id}")
                    scraperJobService.triggerScraperJobWithSearchName(
                        jobSearch = jobSearch,
                        timePeriod = TimePeriod.`1 month`.name,
                        searchName = "month"
                    )
                } catch (e: Exception) {
                    logger.error("Failed to trigger monthly overview for job search: ${jobSearch.id}", e)
                    // Continue with other searches even if one fails
                }
            }

            logger.debug("Successfully triggered monthly overview for user: $userId")

            // Update user preferences to mark overview as sent
            val updatedPreferences = preferences.copy(
                freeMonthlyOverviewSentAt = Instant.now(),
                updatedAt = OffsetDateTime.now()
            )
            userPreferencesRepository.save(updatedPreferences)
            logger.info("Marked free monthly overview as sent for user: $userId")
            
        } catch (e: Exception) {
            logger.error("Failed to trigger monthly overview for user: $userId", e)
        }
    }
}