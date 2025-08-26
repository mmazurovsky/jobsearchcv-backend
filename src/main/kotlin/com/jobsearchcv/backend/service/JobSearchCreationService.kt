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
    private val jobSearchScheduler: JobSearchScheduler
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
                if (!existingSearchesMap.containsKey(jobSearchIn.id)) {
                    // New search
                    searchesToCreate.add(
                        JobSearchOut.fromJobSearchIn(jobSearchIn, userId, isApproved = isApproved)
                    )
                } else {
                    // Update existing search
                    val existing = existingSearchesMap[jobSearchIn.id]!!
                    searchesToUpdate.add(
                        existing.copy(
                            jobTitle = jobSearchIn.jobTitle,
                            location = jobSearchIn.location,
                            jobTypes = jobSearchIn.jobTypes,
                            remoteTypes = jobSearchIn.remoteTypes,
                            timePeriod = jobSearchIn.timePeriod,
                            filterText = jobSearchIn.filterText,
                            isApproved = isApproved
                        )
                    )
                }
            }
            
            // Perform database operations
            val createdSearches = if (searchesToCreate.isNotEmpty()) {
                jobSearchRepository.saveAll(searchesToCreate)
            } else {
                emptyList()
            }
            
            val updatedSearches = searchesToUpdate.map { jobSearchRepository.save(it) }
            
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
                        jobSearchScheduler.addJobSearch(jobSearch)
                    } else {
                        jobSearchScheduler.updateJobSearch(jobSearch)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to update scheduler for job search: ${jobSearch.id}", e)
                    // Don't fail the entire operation if scheduler update fails
                }
            }
            
            // Remove deleted job searches from scheduler
            idsToDelete.forEach { id ->
                try {
                    jobSearchScheduler.removeJobSearch(id)
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
                destinationId = null, // No destination needed when not executing immediately
                immediateSearchTriggerResults = emptyList() // No immediate execution
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create job searches for user $userId: ${e.message}", e)
            throw JobSearchCreationException("Failed to create job searches: ${e.message}", e)
        }
    }

    /**
     * Creates job searches with immediate execution for an authenticated user
     */
    suspend fun createJobSearchesWithImmediateExecution(
        jobSearches: List<JobSearchIn>,
        userId: String,
        email: String
    ): JobSearchCreationResult = coroutineScope {
        
        logger.info("Creating job searches for user: $userId, count=${jobSearches.size}, email=$email")
        
        // Validate input
        if (jobSearches.isEmpty()) {
            throw IllegalArgumentException("No job searches provided")
        }
        
        if (email.isBlank()) {
            throw IllegalArgumentException("Email is required")
        }
        
        try {
            // Create or find existing destination
            val destination = findOrCreateEmailDestination(userId, email)
            
            // Get existing searches for this user
            val existingSearches = jobSearchRepository.findByUserId(userId)
            val existingSearchesMap = existingSearches.associateBy { it.id }
            
            // Separate incoming searches into new, updates, and deletions
            val incomingIds = jobSearches.mapNotNull { it.id }.toSet()
            val searchesToCreate = mutableListOf<JobSearchOut>()
            val searchesToUpdate = mutableListOf<JobSearchOut>()
            val idsToDelete = existingSearches.map { it.id }.filter { it !in incomingIds }
            
            jobSearches.forEach { jobSearchIn ->
                val jobSearchWithWeekPeriod = jobSearchIn.copy(timePeriod = TimePeriod.`1 week`)
                
                if (!existingSearchesMap.containsKey(jobSearchIn.id)) {
                    // New search
                    searchesToCreate.add(
                        JobSearchOut.fromJobSearchIn(jobSearchWithWeekPeriod, userId, destination.id, isApproved = true)
                    )
                } else {
                    // Update existing search
                    val existing = existingSearchesMap[jobSearchIn.id]!!
                    searchesToUpdate.add(
                        existing.copy(
                            jobTitle = jobSearchIn.jobTitle,
                            location = jobSearchIn.location,
                            jobTypes = jobSearchIn.jobTypes,
                            remoteTypes = jobSearchIn.remoteTypes,
                            timePeriod = jobSearchWithWeekPeriod.timePeriod,
                            filterText = jobSearchIn.filterText,
                            isApproved = true,
                            destination = destination.id
                        )
                    )
                }
            }
            
            // Perform database operations
            val createdSearches = if (searchesToCreate.isNotEmpty()) {
                jobSearchRepository.saveAll(searchesToCreate)
            } else {
                emptyList()
            }
            
            val updatedSearches = searchesToUpdate.map { jobSearchRepository.save(it) }
            
            // Delete searches that are no longer in the request, but preserve approved ones
            idsToDelete.forEach { id ->
                val searchToDelete = existingSearchesMap[id]
                if (searchToDelete != null && !searchToDelete.isApproved) {
                    jobSearchRepository.deleteByIdAndUserId(id, userId)
                }
            }
            
            val savedJobSearches = createdSearches + updatedSearches
            
            // Trigger immediate job searches for each saved job search
            val immediateSearchResults = triggerImmediateJobSearches(savedJobSearches)
            
            logger.info("Successfully processed job searches - created: ${createdSearches.size}, updated: ${updatedSearches.size}, deleted: ${idsToDelete.size}")
            
            return@coroutineScope JobSearchCreationResult(
                success = true,
                message = "Successfully processed job searches - created: ${createdSearches.size}, updated: ${updatedSearches.size}, deleted: ${idsToDelete.size}",
                jobSearchIds = savedJobSearches.map { it.id },
                destinationId = destination.id,
                immediateSearchTriggerResults = immediateSearchResults
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create job searches for user $userId: ${e.message}", e)
            throw JobSearchCreationException("Failed to create job searches: ${e.message}", e)
        }
    }
    
    /**
     * Finds existing destination or creates a new one for email channel
     */
    private suspend fun findOrCreateEmailDestination(userId: String, email: String): Destination {
        return destinationRepository.findByUserIdAndChannelAndChannelValue(
            userId, Channel.EMAIL.value, email
        ) ?: run {
            logger.info("Creating new email destination for user: $userId, email: $email")
            destinationRepository.save(Destination.createNew(userId, Channel.EMAIL, email))
        }
    }
    
    /**
     * Triggers immediate job searches for all saved job searches
     */
    private suspend fun triggerImmediateJobSearches(savedJobSearches: List<JobSearchOut>): List<ImmediateSearchResult> {
        return savedJobSearches.map { jobSearchOut ->
            val immediateJobSearch = jobSearchOut.copy(id = "immediate-${jobSearchOut.id}")
            try {
                scraperJobService.triggerScraperJobAndLog(immediateJobSearch)
                logger.info("Successfully triggered immediate job search for: ${jobSearchOut.id}")
                ImmediateSearchResult(
                    originalJobSearchId = jobSearchOut.id,
                    immediateSearchId = immediateJobSearch.id,
                    success = true,
                    errorMessage = null
                )
            } catch (e: Exception) {
                logger.error("Failed to trigger immediate job search for ${jobSearchOut.id}: ${e.message}", e)
                ImmediateSearchResult(
                    originalJobSearchId = jobSearchOut.id,
                    immediateSearchId = immediateJobSearch.id,
                    success = false,
                    errorMessage = e.message
                )
            }
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
    val destinationId: String?,
    val immediateSearchTriggerResults: List<ImmediateSearchResult>
)

/**
 * Result of triggering an immediate job search
 */
data class ImmediateSearchResult(
    val originalJobSearchId: String,
    val immediateSearchId: String,
    val success: Boolean,
    val errorMessage: String?
)

/**
 * Exception thrown when job search creation fails
 */
class JobSearchCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)