package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.TimePeriod
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.JobSearchRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Result of a scheduling attempt
 */
sealed class SchedulingResult {
    data class Scheduled(val jobSearchId: String) : SchedulingResult()
    data class Skipped(val jobSearchId: String, val reason: String) : SchedulingResult()
    data class Error(val jobSearchId: String, val exception: Exception) : SchedulingResult()
}

/**
 * Internal JobSearchScheduler - only accessible within this file
 * All external code should use SubscriptionAwareSchedulingService instead
 */
internal class InternalJobSearchScheduler(
    private val scraperJobService: ScraperJobService,
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val timePeriodResolver: (String, TimePeriod) -> TimePeriod
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(InternalJobSearchScheduler::class.java)
    }

    private lateinit var scheduler: Scheduler

    fun initializeScheduler() {
        scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()
        logger.info("Job search scheduler started")
    }

    fun shutdown() {
        if (::scheduler.isInitialized && !scheduler.isShutdown) {
            scheduler.shutdown(true)
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

    suspend fun scheduleJobSearch(jobSearch: JobSearchOut): SchedulingResult {
        try {
            val userHasDestinations = hasDestinations(jobSearch.userId)

            if (!userHasDestinations) {
                  logger.warn("Skipping scheduling for job search ${jobSearch.id} - user ${jobSearch.userId} needs to add a destination first")
                return SchedulingResult.Skipped(jobSearch.id, "no destinations")
            }

            val jobDataMap = JobDataMap().apply {
                put("searchId", jobSearch.id)
                put("scheduler", this@InternalJobSearchScheduler)
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
                scheduler.rescheduleJob(
                    TriggerKey.triggerKey(
                        "trigger-${jobSearch.id}",
                        "job-searches"
                    ), trigger
                )
                logger.info("Rescheduled existing job search: {}", jobSearch.toLogString())
            } else {
                scheduler.scheduleJob(jobDetail, trigger)
                logger.info("Scheduled new job search: {}", jobSearch.toLogString())
            }

            return SchedulingResult.Scheduled(jobSearch.id)

        } catch (e: Exception) {
            logger.error("Failed to schedule job search: {}", jobSearch.toLogString(), e)
            return SchedulingResult.Error(jobSearch.id, e)
        }
    }

    suspend fun removeJobSearch(searchId: String) {
        try {
            val jobKey = JobKey.jobKey("job-search-$searchId", "job-searches")
            if (::scheduler.isInitialized && scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey)
                logger.info("Removed job search: {}", searchId)
            } else {
                logger.warn("Job search not found in scheduler: {}", searchId)
            }
        } catch (e: Exception) {
            logger.error("Error removing job search: {}", searchId, e)
        }
    }

    class JobSearchJob : Job {
        companion object {
            private val logger: Logger = LoggerFactory.getLogger(JobSearchJob::class.java)
        }

        override fun execute(context: JobExecutionContext) {
            try {
                val searchId = context.jobDetail.jobDataMap.getString("searchId")
                val scheduler =
                    context.jobDetail.jobDataMap.get("scheduler") as InternalJobSearchScheduler

                logger.info("Executing scheduled job search: {}", searchId)

                runBlocking {
                    // Validate job search is still active and subscribed from database
                    val currentJobSearch = scheduler.jobSearchRepository.findById(searchId)

                    if (currentJobSearch == null) {
                        logger.warn("Skipping execution - job search {} no longer exists", searchId)
                        return@runBlocking
                    }

                    if (!currentJobSearch.isApproved || !currentJobSearch.isSubscribed) {
                        logger.warn("Skipping execution - job search {} is no longer active or subscribed (approved: {}, subscribed: {})",
                            searchId, currentJobSearch.isApproved, currentJobSearch.isSubscribed)
                        return@runBlocking
                    }

                    // Get effective time period based on current subscription status
                    val effectiveTimePeriod = scheduler.timePeriodResolver(
                        currentJobSearch.userId,
                        currentJobSearch.timePeriod
                    )

                    // Log execution with subscription context
                    if (effectiveTimePeriod == currentJobSearch.timePeriod) {
                        logger.info(
                            "Executing search {} for premium user {} (period: {})",
                            searchId, currentJobSearch.userId, effectiveTimePeriod.displayName
                        )
                    } else {
                        logger.info(
                            "Executing search {} for free-tier user {} (effective period: {}, saved period: {})",
                            searchId, currentJobSearch.userId, effectiveTimePeriod.displayName, currentJobSearch.timePeriod.displayName
                        )
                    }

                    // Use effective time period for execution (important for free users)
                    val jobSearchToExecute = if (effectiveTimePeriod != currentJobSearch.timePeriod) {
                        currentJobSearch.copy(timePeriod = effectiveTimePeriod)
                    } else {
                        currentJobSearch
                    }

                    scheduler.scraperJobService.triggerScraperJobAndLog(jobSearchToExecute)
                }

                logger.info("Initiated scheduled job search: {}", searchId)
            } catch (e: Exception) {
                logger.error("Error executing scheduled job search", e)
            }
        }
    }
}

/**
 * Subscription-aware scheduling service that provides the ONLY public interface for job scheduling
 * Automatically handles subscription tiers:
 * - Free users: Monthly scheduling regardless of saved period
 * - Premium users: Original time periods as saved
 */
@Service
class SubscriptionAwareSchedulingService(
    private val subscriptionService: SubscriptionService,
    private val scraperJobService: ScraperJobService,
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository
) {

    // Internal scheduler instance - not injectable from outside
    private val internalJobSearchScheduler =
        InternalJobSearchScheduler(
            scraperJobService,
            destinationRepository,
            jobSearchRepository
        ) { userId, originalTimePeriod ->
            getEffectiveTimePeriodForUser(userId, originalTimePeriod)
        }

    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionAwareSchedulingService::class.java)
        private val FREE_TIER_TIME_PERIOD = TimePeriod.`1 month`
    }

    @PostConstruct
    fun initializeScheduler() {
        internalJobSearchScheduler.initializeScheduler()
    }

    @PreDestroy
    fun shutdown() {
        internalJobSearchScheduler.shutdown()
    }

    /**
     * Checks if a job search should be scheduled based on approval and subscription status
     */
    private fun shouldScheduleJobSearch(jobSearch: JobSearchOut): Boolean {
        return jobSearch.isApproved && jobSearch.isSubscribed
    }

    /**
     * Schedules a job search with subscription-aware time period logic
     * - Free users: Force monthly scheduling regardless of saved time period
     * - Premium users: Use their originally saved time period
     * - Only schedules if both isApproved and isSubscribed are true
     */
    suspend fun scheduleJobSearchWithSubscriptionLogic(jobSearch: JobSearchOut): SchedulingResult {
        try {
            // Check if job search should be scheduled
            if (!shouldScheduleJobSearch(jobSearch)) {
                val reason = when {
                    !jobSearch.isApproved && !jobSearch.isSubscribed -> "not approved and not subscribed"
                    !jobSearch.isApproved -> "not approved"
                    !jobSearch.isSubscribed -> "not subscribed"
                    else -> "unknown reason"
                }
                logger.info(
                    "Skipping scheduling for job search {} - isApproved: {}, isSubscribed: {}",
                    jobSearch.id,
                    jobSearch.isApproved,
                    jobSearch.isSubscribed
                )
                return SchedulingResult.Skipped(jobSearch.id, reason)
            }

            val effectiveTimePeriod = getEffectiveTimePeriod(jobSearch.userId, jobSearch.timePeriod)
            val adjustedJobSearch = jobSearch.copy(timePeriod = effectiveTimePeriod)

            // Log the scheduling decision
            if (effectiveTimePeriod != jobSearch.timePeriod) {
                logger.info(
                    "Adjusted scheduling for user {}: original period '{}', effective period '{}' (subscription-based)",
                    jobSearch.userId,
                    jobSearch.timePeriod.displayName,
                    effectiveTimePeriod.displayName
                )
            }

            return internalJobSearchScheduler.scheduleJobSearch(adjustedJobSearch)
        } catch (e: Exception) {
            logger.error("Error scheduling job search with subscription logic: ${jobSearch.id}", e)
            return SchedulingResult.Error(jobSearch.id, e)
        }
    }

    /**
     * Removes a job search from the scheduler
     */
    suspend fun removeJobSearch(searchId: String) {
        try {
            logger.info("Removing job search from scheduler: {}", searchId)
            internalJobSearchScheduler.removeJobSearch(searchId)
            logger.info("Successfully removed job search: {}", searchId)
        } catch (e: Exception) {
            logger.error("Error removing job search: {}", searchId, e)
            throw e
        }
    }

    /**
     * Updates a job search with subscription-aware scheduling logic
     * First removes any existing schedule, then reschedules with appropriate time period
     */
    suspend fun updateJobSearch(jobSearch: JobSearchOut) {
        try {
            logger.info("Updating job search with subscription logic: ${jobSearch.id}")

            // First remove the existing scheduled job
            internalJobSearchScheduler.removeJobSearch(jobSearch.id)

            // Then reschedule with subscription-aware logic
            val result = scheduleJobSearchWithSubscriptionLogic(jobSearch)

            when (result) {
                is SchedulingResult.Scheduled -> logger.info("Successfully updated job search: ${jobSearch.id}")
                is SchedulingResult.Skipped -> logger.info("Job search ${jobSearch.id} not scheduled: ${result.reason}")
                is SchedulingResult.Error -> throw result.exception
            }
        } catch (e: Exception) {
            logger.error("Error updating job search with subscription logic: ${jobSearch.id}", e)
            throw e
        }
    }

    /**
     * Logs a summary of scheduling results
     */
    private fun logSchedulingSummary(results: List<SchedulingResult>, operation: String) {
        val scheduled = results.filterIsInstance<SchedulingResult.Scheduled>()
        val skipped = results.filterIsInstance<SchedulingResult.Skipped>()
        val errors = results.filterIsInstance<SchedulingResult.Error>()

        logger.info("$operation summary: Scheduled: ${scheduled.size}, Skipped: ${skipped.size}")

        if (skipped.isNotEmpty()) {
            skipped.forEach { skip ->
                logger.info("- Job ${skip.jobSearchId} not scheduled: ${skip.reason}")
            }
        }

        if (errors.isNotEmpty()) {
            logger.warn("$operation had ${errors.size} errors - check logs for details")
        }
    }

    /**
     * Determines the effective time period based on user's subscription status
     */
    private fun getEffectiveTimePeriod(userId: String, originalTimePeriod: TimePeriod): TimePeriod {
        return if (subscriptionService.checkPremiumAccess(userId)) {
            // Premium users get their originally saved time period
            logger.debug(
                "User {} has premium access, using original period: {}",
                userId,
                originalTimePeriod.displayName
            )
            originalTimePeriod
        } else {
            // Free users are forced to monthly scheduling
            logger.debug(
                "User {} is free tier, forcing monthly period (original: {})",
                userId,
                originalTimePeriod.displayName
            )
            FREE_TIER_TIME_PERIOD
        }
    }

    /**
     * Reschedules all job searches for a specific user based on their current subscription status
     * This is called when subscription status changes (upgrade/downgrade)
     */
    suspend fun rescheduleAllSearchesForUser(userId: String) {
        try {
            logger.info("Rescheduling all job searches for user: {}", userId)

            // Get all user's searches from database that should be scheduled
            val userSearches = jobSearchRepository.findByUserId(userId)
            val schedulableSearches = userSearches.filter { shouldScheduleJobSearch(it) }

            if (schedulableSearches.isEmpty()) {
                logger.info("No schedulable searches to reschedule for user: {}", userId)
                return
            }

            val results = mutableListOf<SchedulingResult>()

            schedulableSearches.forEach { jobSearch ->
                // Remove existing scheduled job first to avoid conflicts
                internalJobSearchScheduler.removeJobSearch(jobSearch.id)

                // Schedule with subscription-aware logic
                val result = scheduleJobSearchWithSubscriptionLogic(jobSearch)
                results.add(result)
            }

            logSchedulingSummary(results, "Rescheduling for user $userId")

            val errorCount = results.filterIsInstance<SchedulingResult.Error>().size
            if (errorCount > 0) {
                throw RuntimeException("Failed to reschedule $errorCount out of ${schedulableSearches.size} searches for user: $userId")
            }

        } catch (e: Exception) {
            logger.error("Error rescheduling searches for user: {}", userId, e)
            throw e
        }
    }

    /**
     * Bulk schedule job searches with subscription-aware logic
     * Used during application startup
     */
    suspend fun scheduleMultipleJobSearchesWithSubscriptionLogic(jobSearches: List<JobSearchOut>) {
        logger.info("Bulk scheduling {} job searches with subscription logic", jobSearches.size)

        val results = jobSearches.map { jobSearch ->
            scheduleJobSearchWithSubscriptionLogic(jobSearch)
        }

        logSchedulingSummary(results, "Bulk scheduling")

        val errorCount = results.filterIsInstance<SchedulingResult.Error>().size
        if (errorCount > 0) {
            logger.warn("Some job searches failed to schedule during bulk operation. Check logs for details.")
        }
    }

    /**
     * Schedules initial job searches on application startup with subscription-aware logic
     * Replaces direct JobSearchScheduler.addInitialJobSearches calls
     */
    suspend fun scheduleInitialJobSearches(jobSearches: List<JobSearchOut>) {
        logger.info(
            "Scheduling {} initial job searches with subscription awareness",
            jobSearches.size
        )

        val results = jobSearches.map { jobSearch ->
            scheduleJobSearchWithSubscriptionLogic(jobSearch)
        }

        logSchedulingSummary(results, "Initial scheduling")

        val errorCount = results.filterIsInstance<SchedulingResult.Error>().size
        if (errorCount > 0) {
            logger.warn("Some initial job searches failed to schedule during startup. Check logs for details.")
        }
    }

    /**
     * Schedules all approved and subscribed job searches for a specific user when they add their first destination
     * Replaces direct JobSearchScheduler.scheduleAllApprovedSearchesForUser calls
     */
    suspend fun scheduleAllApprovedSubscribedSearchesForUser(userId: String) {
        logger.info("Scheduling all approved and subscribed job searches for user: {}", userId)

        val userSearches = jobSearchRepository.findByUserIdAndIsApprovedAndIsSubscribed(userId, true, true)
        logger.info("Found {} approved and subscribed job searches for user: {}", userSearches.size, userId)

        if (userSearches.isEmpty()) {
            logger.info("No approved and subscribed job searches to schedule for user: {}", userId)
            return
        }

        val results = userSearches.map { jobSearch ->
            scheduleJobSearchWithSubscriptionLogic(jobSearch)
        }

        logSchedulingSummary(results, "Approved searches scheduling for user $userId")

        val errorCount = results.filterIsInstance<SchedulingResult.Error>().size
        if (errorCount > 0) {
            logger.warn(
                "Some approved searches failed to schedule for user {}. Check logs for details.",
                userId
            )
        }
    }

    /**
     * Gets the effective time period that would be used for scheduling without actually scheduling
     * Useful for API responses or testing
     */
    fun getEffectiveTimePeriodForUser(userId: String, originalTimePeriod: TimePeriod): TimePeriod {
        return getEffectiveTimePeriod(userId, originalTimePeriod)
    }
}
