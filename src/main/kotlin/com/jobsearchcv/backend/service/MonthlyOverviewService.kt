package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.TimePeriod
import com.jobsearchcv.backend.repository.JobSearchRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MonthlyOverviewService(
    private val jobSearchRepository: JobSearchRepository,
    private val scraperJobService: ScraperJobService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MonthlyOverviewService::class.java)
    }

    suspend fun triggerMonthlyOverviewForUser(userId: String) {
        try {
            logger.debug("Triggering monthly overview for user: $userId")
            
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
            
        } catch (e: Exception) {
            logger.error("Failed to trigger monthly overview for user: $userId", e)
        }
    }
}