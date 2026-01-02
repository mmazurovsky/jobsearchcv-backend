package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.JobSearchRepository
import kotlinx.coroutines.coroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JobSearchCreationService(
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val scraperJobService: ScraperJobService,
    private val subscriptionAwareSchedulingService: SubscriptionAwareSchedulingService
) {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchCreationService::class.java)
    }
    
    /**
     * Creates job searches for an authenticated user with specified approval status
     */
    suspend fun createJobSearches(
        jobSearches: List<JobSearchIn>,
        userId: String,
        isApproved: Boolean
    ): JobSearchCreationResult = coroutineScope {
        
        logger.info("Creating job searches for user: $userId, count=${jobSearches.size}, isApproved=$isApproved")
        
        // Validate input
        if (jobSearches.isEmpty()) {
            throw IllegalArgumentException("No job searches provided")
        }
        
        try {
            // Get existing searches for this user
            val existingSearches = jobSearchRepository.findByUserId(userId)
            val existingSearchesMap = existingSearches.associateBy { it.id }
            
            // Separate incoming searches into new, updates, and deletions
            val incomingIds = jobSearches.mapNotNull { it.id }.toSet()
            val searchesToCreate = mutableListOf<JobSearchOut>()
            val searchesToUpdate = mutableListOf<JobSearchOut>()
            val idsToDelete = existingSearches.map { it.id }.filter { it !in incomingIds }
            
            jobSearches.forEach { jobSearchIn ->
                logger.debug("Processing jobSearchIn: id=${jobSearchIn.id}, timePeriod=${jobSearchIn.timePeriod}, jobTypes=${jobSearchIn.jobTypes}, remoteTypes=${jobSearchIn.remoteTypes}")
                
                if (!existingSearchesMap.containsKey(jobSearchIn.id)) {
                    // New search
                    val newSearch = JobSearchOut.fromJobSearchIn(jobSearchIn, userId, isApproved = isApproved)
                    logger.debug("Creating new search: id=${newSearch.id}, timePeriod=${newSearch.timePeriod}, jobTypes=${newSearch.jobTypes}, remoteTypes=${newSearch.remoteTypes}")
                    searchesToCreate.add(newSearch)
                } else {
                    // Update existing search
                    val existing = existingSearchesMap[jobSearchIn.id]!!
                    logger.debug("Existing search before update: id=${existing.id}, timePeriod=${existing.timePeriod}, jobTypes=${existing.jobTypes}, remoteTypes=${existing.remoteTypes}")
                    
                    val updated = existing.copy(
                        jobTitle = jobSearchIn.jobTitle,
                        location = jobSearchIn.location,
                        jobTypes = jobSearchIn.jobTypes,
                        remoteTypes = jobSearchIn.remoteTypes,
                        timePeriod = jobSearchIn.timePeriod,
                        filterText = jobSearchIn.filterText,
                        isApproved = isApproved
                    )
                    
                    logger.debug("Updated search: id=${updated.id}, timePeriod=${updated.timePeriod}, jobTypes=${updated.jobTypes}, remoteTypes=${updated.remoteTypes}")
                    searchesToUpdate.add(updated)
                }
            }
            
            // Perform database operations
            val createdSearches = if (searchesToCreate.isNotEmpty()) {
                jobSearchRepository.saveAll(searchesToCreate)
            } else {
                emptyList()
            }
            
            val updatedSearches = searchesToUpdate.map { searchToUpdate ->
                val saved = jobSearchRepository.save(searchToUpdate)
                logger.debug("Saved updated search to DB: id=${saved.id}, timePeriod=${saved.timePeriod}, jobTypes=${saved.jobTypes}, remoteTypes=${saved.remoteTypes}")
                saved
            }
            
            // Delete searches that are no longer in the request, but preserve approved ones
            idsToDelete.forEach { id ->
                val searchToDelete = existingSearchesMap[id]
                if (searchToDelete != null && !searchToDelete.isApproved) {
                    jobSearchRepository.deleteByIdAndUserId(id, userId)
                }
            }
            
            val savedJobSearches = createdSearches + updatedSearches
            
            // Update scheduler for all created and updated searches
            savedJobSearches.forEach { jobSearch ->
                try {
                    if (createdSearches.contains(jobSearch)) {
                        subscriptionAwareSchedulingService.scheduleJobSearchWithSubscriptionLogic(jobSearch)
                    } else {
                        subscriptionAwareSchedulingService.updateJobSearch(jobSearch)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to update scheduler for job search: ${jobSearch.id}", e)
                    // Don't fail the entire operation if scheduler update fails
                }
            }
            
            // Remove deleted job searches from scheduler
            idsToDelete.forEach { id ->
                try {
                    subscriptionAwareSchedulingService.removeJobSearch(id)
                } catch (e: Exception) {
                    logger.error("Failed to remove job search from scheduler: $id", e)
                    // Don't fail the entire operation if scheduler update fails
                }
            }

            logger.info("Successfully processed job searches - created: ${createdSearches.size}, updated: ${updatedSearches.size}, deleted: ${idsToDelete.size}")

            return@coroutineScope JobSearchCreationResult(
                success = true,
                message = "Successfully processed job searches - created: ${createdSearches.size}, updated: ${updatedSearches.size}, deleted: ${idsToDelete.size}",
                jobSearchIds = savedJobSearches.map { it.id },
                destinationId = null
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create job searches for user $userId: ${e.message}", e)
            throw JobSearchCreationException("Failed to create job searches: ${e.message}", e)
        }
    }

    
}

/**
 * Result of job search creation operation
 */
data class JobSearchCreationResult(
    val success: Boolean,
    val message: String,
    val jobSearchIds: List<String>,
    val destinationId: String?
)

/**
 * Exception thrown when job search creation fails
 */
class JobSearchCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)