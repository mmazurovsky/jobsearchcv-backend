package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class UnsubscribeResult(
    val success: Boolean,
    val message: String,
    val affectedCount: Long = 0
)

@Service
class JobSearchService(
    private val jobSearchRepository: JobSearchRepository,
    private val subscriptionAwareSchedulingService: SubscriptionAwareSchedulingService,
    private val scraperJobService: ScraperJobService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchService::class.java)
    }
    

    
    fun getUserSearches(userId: String): List<JobSearchOut> {
        return jobSearchRepository.findByUserId(userId)
    }
    
    fun getSearchById(searchId: String): JobSearchOut? {
        return jobSearchRepository.findById(searchId)
    }
    
    suspend fun deleteJobSearch(userId: String, searchId: String): Boolean {
        val search = jobSearchRepository.findByIdAndUserId(searchId, userId)
        if (search != null) {
            jobSearchRepository.deleteById(searchId)
            subscriptionAwareSchedulingService.removeJobSearch(searchId)
            logger.info("Deleted job search: {} for user: {}", searchId, userId)
            return true
        }
        return false
    }
    
    suspend fun initialize() {
        // Load all existing job searches and add them to the scheduler
        val allSearches = jobSearchRepository.findAll()
        logger.info("Loading {} existing job searches", allSearches.size)
        
        // Use subscription-aware bulk scheduling
        subscriptionAwareSchedulingService.scheduleInitialJobSearches(allSearches)
    }
    
    suspend fun unsubscribeFromAllSearches(userId: String): UnsubscribeResult {
        return try {
            logger.info("Unsubscribing user {} from all job searches", userId)
            
            // Get all user's currently subscribed searches to remove from scheduler
            val subscribedSearches = jobSearchRepository.findByUserIdAndIsSubscribed(userId, true)
            
            // Update all user's searches to unsubscribed
            val affectedCount = jobSearchRepository.updateIsSubscribedByUserId(userId, false)
            
            // Remove all user's searches from scheduler
            subscribedSearches.forEach { jobSearch ->
                try {
                    subscriptionAwareSchedulingService.removeJobSearch(jobSearch.id)
                    logger.debug("Removed job search {} from scheduler", jobSearch.id)
                } catch (e: Exception) {
                    logger.warn("Failed to remove job search {} from scheduler: {}", jobSearch.id, e.message)
                }
            }
            
            logger.info("Successfully unsubscribed user {} from {} job searches", userId, affectedCount)
            UnsubscribeResult(
                success = true,
                message = "Successfully unsubscribed from all job searches",
                affectedCount = affectedCount
            )
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe user {} from all searches", userId, e)
            UnsubscribeResult(
                success = false,
                message = "Failed to unsubscribe from all searches: ${e.message}"
            )
        }
    }
    
    suspend fun unsubscribeFromSearch(userId: String, searchId: String): UnsubscribeResult {
        return try {
            logger.info("Unsubscribing user {} from job search {}", userId, searchId)
            
            // Verify the job search exists and belongs to the user
            val jobSearch = jobSearchRepository.findByIdAndUserId(searchId, userId)
            if (jobSearch == null) {
                return UnsubscribeResult(
                    success = false,
                    message = "Job search not found or does not belong to user"
                )
            }
            
            // Update the specific job search to unsubscribed
            val affectedCount = jobSearchRepository.updateIsSubscribedById(searchId, false)
            
            // Remove from scheduler if it was subscribed
            if (jobSearch.isSubscribed) {
                try {
                    subscriptionAwareSchedulingService.removeJobSearch(searchId)
                    logger.debug("Removed job search {} from scheduler", searchId)
                } catch (e: Exception) {
                    logger.warn("Failed to remove job search {} from scheduler: {}", searchId, e.message)
                }
            }
            
            logger.info("Successfully unsubscribed user {} from job search {}", userId, searchId)
            UnsubscribeResult(
                success = true,
                message = "Successfully unsubscribed from job search",
                affectedCount = affectedCount
            )
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe user {} from search {}", userId, searchId, e)
            UnsubscribeResult(
                success = false,
                message = "Failed to unsubscribe from search: ${e.message}"
            )
        }
    }
    
    suspend fun resubscribeToSearch(userId: String, searchId: String): UnsubscribeResult {
        return try {
            logger.info("Resubscribing user {} to job search {}", userId, searchId)
            
            // Verify the job search exists and belongs to the user
            val jobSearch = jobSearchRepository.findByIdAndUserId(searchId, userId)
            if (jobSearch == null) {
                return UnsubscribeResult(
                    success = false,
                    message = "Job search not found or does not belong to user"
                )
            }
            
            // Update the specific job search to subscribed
            val affectedCount = jobSearchRepository.updateIsSubscribedById(searchId, true)
            
            // Add back to scheduler if it's approved
            val updatedJobSearch = jobSearchRepository.findById(searchId)
            if (updatedJobSearch != null && updatedJobSearch.isApproved && updatedJobSearch.isSubscribed) {
                try {
                    subscriptionAwareSchedulingService.scheduleJobSearchWithSubscriptionLogic(updatedJobSearch)
                    logger.debug("Added job search {} back to scheduler", searchId)
                } catch (e: Exception) {
                    logger.warn("Failed to add job search {} back to scheduler: {}", searchId, e.message)
                }
            }
            
            logger.info("Successfully resubscribed user {} to job search {}", userId, searchId)
            UnsubscribeResult(
                success = true,
                message = "Successfully resubscribed to job search",
                affectedCount = affectedCount
            )
        } catch (e: Exception) {
            logger.error("Failed to resubscribe user {} to search {}", userId, searchId, e)
            UnsubscribeResult(
                success = false,
                message = "Failed to resubscribe to search: ${e.message}"
            )
        }
    }
} 