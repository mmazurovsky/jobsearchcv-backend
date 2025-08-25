package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.service.ScraperJobService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class JobSearchScheduler(
    private val scraperJobService: ScraperJobService,
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchScheduler::class.java)
    }
    
    private lateinit var scheduler: Scheduler
    private val activeSearches: MutableMap<String, JobSearchOut> = ConcurrentHashMap()
    
    @PostConstruct
    fun initializeScheduler() {
        scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()
        logger.info("Job search scheduler started")
    }

    @PreDestroy
    fun shutdown() {
        if (::scheduler.isInitialized && !scheduler.isShutdown) {
            scheduler.shutdown(true)
            activeSearches.clear()
            logger.info("Job search scheduler stopped")
        }
    }

    /**
     * Checks if a user has any destinations configured for receiving job notifications
     */
    private suspend fun hasDestinations(userId: String): Boolean {
        return try {
            val destinations = destinationRepository.findByUserId(userId)
            destinations.isNotEmpty()
        } catch (e: Exception) {
            logger.error("Error checking destinations for user: $userId", e)
            false
        }
    }

    suspend fun addInitialJobSearches(jobSearches: List<JobSearchOut>) {
        try {
            val approvedSearches = jobSearches.filter { it.isApproved }
            
            // Filter searches that have destinations
            val searchesWithDestinations = mutableListOf<JobSearchOut>()
            val searchesWithoutDestinations = mutableListOf<JobSearchOut>()
            
            approvedSearches.forEach { search ->
                if (hasDestinations(search.userId)) {
                    searchesWithDestinations.add(search)
                } else {
                    searchesWithoutDestinations.add(search)
                    logger.warn("Skipping job search {} for user {} - no destinations configured", search.id, search.userId)
                }
            }
            
            // Schedule only searches with destinations
            searchesWithDestinations.forEach { search ->
                addJobSearch(search)
            }
            
            logger.info("Added {} initial job searches (out of {} total, {} approved, {} had destinations, {} skipped due to no destinations)", 
                searchesWithDestinations.size, jobSearches.size, approvedSearches.size, 
                searchesWithDestinations.size, searchesWithoutDestinations.size)
        } catch (e: Exception) {
            logger.error("Error adding initial job searches", e)
        }
    }

    suspend fun addJobSearch(jobSearch: JobSearchOut) {
        try {
            // Only add approved job searches
            if (!jobSearch.isApproved) {
                logger.info("Skipping unapproved job search: {}", jobSearch.id)
                return
            }
            
            // Check if user has destinations configured
            if (!hasDestinations(jobSearch.userId)) {
                logger.warn("Skipping job search {} for user {} - no destinations configured", jobSearch.id, jobSearch.userId)
                return
            }
            
            // If job already exists, remove it first to ensure clean state
            if (jobSearch.id in activeSearches) {
                logger.info("Job search already exists: {}, removing old version before adding new one", jobSearch.id)
                removeJobSearch(jobSearch.id)
            }
            
            activeSearches[jobSearch.id] = jobSearch
            scheduleJobSearch(jobSearch)
            logger.info("Added job search: {}", jobSearch.id)
        } catch (e: Exception) {
            logger.error("Error adding job search", e)
        }
    }

    suspend fun removeJobSearch(searchId: String) {
        try {
            if (searchId in activeSearches) {
                // Remove from scheduler
                val jobKey = JobKey.jobKey("job-search-$searchId", "job-searches")
                scheduler.deleteJob(jobKey)
                
                // Remove from active searches
                activeSearches.remove(searchId)
                
                logger.info("Removed job search: {}", searchId)
            } else {
                logger.warn("Job search not found: {}", searchId)
            }
        } catch (e: Exception) {
            logger.error("Error removing job search: {}", searchId, e)
        }
    }
    
    suspend fun updateJobSearch(jobSearch: JobSearchOut) {
        try {
            // Check if job search is approved
            if (!jobSearch.isApproved) {
                // If it was previously scheduled, remove it
                if (jobSearch.id in activeSearches) {
                    val jobKey = JobKey.jobKey("job-search-${jobSearch.id}", "job-searches")
                    scheduler.deleteJob(jobKey)
                    activeSearches.remove(jobSearch.id)
                    logger.info("Removed unapproved job search from scheduler: {}", jobSearch.id)
                }
                return
            }
            
            // Check if user has destinations configured
            if (!hasDestinations(jobSearch.userId)) {
                // If it was previously scheduled, remove it since there are no destinations
                if (jobSearch.id in activeSearches) {
                    val jobKey = JobKey.jobKey("job-search-${jobSearch.id}", "job-searches")
                    scheduler.deleteJob(jobKey)
                    activeSearches.remove(jobSearch.id)
                    logger.warn("Removed job search {} from scheduler - no destinations configured for user {}", jobSearch.id, jobSearch.userId)
                } else {
                    logger.warn("Skipping job search update {} for user {} - no destinations configured", jobSearch.id, jobSearch.userId)
                }
                return
            }
            
            // Remove existing job if present
            if (jobSearch.id in activeSearches) {
                val jobKey = JobKey.jobKey("job-search-${jobSearch.id}", "job-searches")
                scheduler.deleteJob(jobKey)
                logger.info("Removed existing scheduled job for update: {}", jobSearch.id)
            }
            
            // Update active searches
            activeSearches[jobSearch.id] = jobSearch
            
            // Schedule with new parameters
            scheduleJobSearch(jobSearch)
            logger.info("Updated job search: {}", jobSearch.toLogString())
        } catch (e: Exception) {
            logger.error("Error updating job search: {}", jobSearch.id, e)
            throw e
        }
    }

    suspend fun scheduleJobSearch(jobSearch: JobSearchOut) {
        try {
            val jobDataMap = JobDataMap().apply {
                put("searchId", jobSearch.id)
                put("scheduler", this@JobSearchScheduler)
            }

            val jobDetail = JobBuilder.newJob(JobSearchJob::class.java)
                .withIdentity("job-search-${jobSearch.id}", "job-searches")
                .setJobData(jobDataMap)
                .storeDurably(false) // Job will be removed if no triggers reference it
                .build()

            val trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-${jobSearch.id}", "job-searches")
                .withSchedule(CronScheduleBuilder.cronSchedule(jobSearch.timePeriod.cronExpression))
                .build()

            // Use rescheduleJob if job already exists, otherwise schedule new
            val jobKey = JobKey.jobKey("job-search-${jobSearch.id}", "job-searches")
            if (scheduler.checkExists(jobKey)) {
                scheduler.rescheduleJob(TriggerKey.triggerKey("trigger-${jobSearch.id}", "job-searches"), trigger)
                logger.info("Rescheduled existing job search: {}", jobSearch.toLogString())
            } else {
                scheduler.scheduleJob(jobDetail, trigger)
                logger.info("Scheduled new job search: {}", jobSearch.toLogString())
            }

        } catch (e: Exception) {
            logger.error("Failed to schedule job search: {}", jobSearch.toLogString(), e)
            throw e
        }
    }

    suspend fun unscheduleJobSearch(searchId: String) {
        try {
            val jobKey = JobKey.jobKey("job-search-$searchId", "job-searches")
            scheduler.deleteJob(jobKey)
            logger.info("Unscheduled job search: {}", searchId)
        } catch (e: Exception) {
            logger.error("Failed to unschedule job search: {}", searchId, e)
            throw e
        }
    }

    

    fun getActiveSearchesCount(): Int = activeSearches.size
    
    fun getActiveSearches(): Map<String, JobSearchOut> = activeSearches.toMap()
    
    /**
     * Schedules all approved job searches for a user that have destinations configured.
     * This is called when a user adds their first destination.
     */
    suspend fun scheduleAllApprovedSearchesForUser(userId: String) {
        try {
            logger.info("Scheduling all approved job searches for user: $userId")
            
            // Check if user has destinations
            if (!hasDestinations(userId)) {
                logger.warn("User $userId has no destinations configured, skipping scheduling")
                return
            }
            
            // Get all approved job searches for the user
            val userJobSearches = jobSearchRepository.findByUserId(userId)
            val approvedSearches = userJobSearches.filter { it.isApproved }
            
            var scheduledCount = 0
            var skippedCount = 0
            
            approvedSearches.forEach { jobSearch ->
                try {
                    // Only schedule if not already scheduled
                    if (jobSearch.id !in activeSearches) {
                        addJobSearch(jobSearch)
                        scheduledCount++
                    } else {
                        logger.info("Job search ${jobSearch.id} is already scheduled, skipping")
                        skippedCount++
                    }
                } catch (e: Exception) {
                    logger.error("Failed to schedule job search ${jobSearch.id} for user $userId", e)
                    skippedCount++
                }
            }
            
            logger.info("Scheduled $scheduledCount approved job searches for user $userId (skipped: $skippedCount, total approved: ${approvedSearches.size})")
            
        } catch (e: Exception) {
            logger.error("Error scheduling approved job searches for user: $userId", e)
        }
    }

    class JobSearchJob : Job {
    
        companion object {
            private val logger: Logger = LoggerFactory.getLogger(JobSearchJob::class.java)
        }
        override fun execute(context: JobExecutionContext) {
            try {
                val searchId = context.jobDetail.jobDataMap.getString("searchId")
                val scheduler = context.jobDetail.jobDataMap.get("scheduler") as JobSearchScheduler

                logger.info("Executing scheduled job search: {}", searchId)

                runBlocking {
                    val jobSearch = scheduler.activeSearches[searchId]
                    if (jobSearch != null) {
                        scheduler.scraperJobService.triggerScraperJobAndLog(jobSearch)
                    } else {
                        logger.warn("Job search not found in active searches: {}", searchId)
                    }
                }

                logger.info("Initiated scheduled job search: {}", searchId)
            } catch (e: Exception) {
                logger.error("Error executing scheduled job search", e)
            }
        }
    }
} 