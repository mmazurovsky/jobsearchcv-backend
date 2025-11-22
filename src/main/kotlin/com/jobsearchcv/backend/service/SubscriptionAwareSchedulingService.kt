package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.TimePeriod
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.repository.JobSearchRepository
import kotlinx.coroutines.runBlocking
import org.quartz.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Result of a scheduling attempt
 */
sealed class SchedulingResult {
    data class Scheduled(val jobSearchId: String, val isPremium: Boolean) : SchedulingResult()
    data class Skipped(val jobSearchId: String, val reason: String) : SchedulingResult()
    data class Error(val jobSearchId: String, val exception: Exception) : SchedulingResult()
}

/**
 * Internal JobSearchScheduler - only accessible within this file
 * All external code should use SubscriptionAwareSchedulingService instead
 */
internal class InternalJobSearchScheduler(
    private val scheduler: Scheduler,
    private val scraperJobService: ScraperJobService,
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val timePeriodResolver: (JobSearchOut) -> TimePeriod
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(InternalJobSearchScheduler::class.java)
    }

    init {
        logger.info("InternalJobSearchScheduler initialized with Spring-managed scheduler")
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

    suspend fun scheduleJobSearch(jobSearch: JobSearchOut, isPremium: Boolean): SchedulingResult {
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

            return SchedulingResult.Scheduled(jobSearch.id, isPremium)

        } catch (e: Exception) {
            logger.error("Failed to schedule job search: {}", jobSearch.toLogString(), e)
            return SchedulingResult.Error(jobSearch.id, e)
        }
    }

    suspend fun removeJobSearch(searchId: String) {
        try {
            val jobKey = JobKey.jobKey("job-search-$searchId", "job-searches")
            if (scheduler.checkExists(jobKey)) {
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

                    // Get effective time period based on admin flag and current subscription status
                    val effectiveTimePeriod = scheduler.timePeriodResolver(currentJobSearch)

                    // Log execution with subscription context
                    if (currentJobSearch.isAdmin == true) {
                        logger.info(
                            "Executing admin search {} for user {} (period: {})",
                            searchId, currentJobSearch.userId, effectiveTimePeriod.displayName
                        )
                    } else {
                        logger.info(
                            "Executing search {} for subscribed user {} (period: {})",
                            searchId, currentJobSearch.userId, effectiveTimePeriod.displayName
                        )
                    }

                    // Use effective time period for execution
                    val jobSearchToExecute = currentJobSearch.copy(timePeriod = effectiveTimePeriod)

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
    private val scheduler: Scheduler,
    private val subscriptionService: SubscriptionService,
    private val scraperJobService: ScraperJobService,
    private val destinationRepository: DestinationRepository,
    private val jobSearchRepository: JobSearchRepository
) {

    // Internal scheduler instance - not injectable from outside
    private val internalJobSearchScheduler =
        InternalJobSearchScheduler(
            scheduler,
            scraperJobService,
            destinationRepository,
            jobSearchRepository
        ) { jobSearch ->
            getEffectiveTimePeriodForJobSearch(jobSearch)
        }

    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionAwareSchedulingService::class.java)
    }

    init {
        logger.info("SubscriptionAwareSchedulingService initialized with Spring-managed Quartz scheduler")
    }

    /**
     * Checks if a job search should be scheduled based on approval and subscription status
     */
    private fun shouldScheduleJobSearch(jobSearch: JobSearchOut): Boolean {
        return jobSearch.isApproved && jobSearch.isSubscribed
    }

    /**
     * CRITICAL: Only schedule jobs for users with active Stripe subscriptions
     * Users without Stripe subscription (FREE tier) should NOT get any job searches scheduled
     * This prevents free monthly job overviews
     */
    private fun shouldScheduleJobSearchForUser(userId: String): Boolean {
        // Admin job searches bypass this check
        val hasPremiumAccess = subscriptionService.checkPremiumAccess(userId)
        if (!hasPremiumAccess) {
            logger.info("Skipping job search scheduling for user {} - no active subscription", userId)
            return false
        }
        return true
    }

    /**
     * Schedules a job search with subscription-aware time period logic
     * - CRITICAL: Only schedules for users with active Stripe subscription (PREMIUM tier)
     * - Users without subscription (FREE tier) will NOT have job searches scheduled
     * - Premium users (weekly or monthly, including trial): Use their selected time period
     * - Only schedules if both isApproved and isSubscribed are true
     */
    suspend fun scheduleJobSearchWithSubscriptionLogic(jobSearch: JobSearchOut): SchedulingResult {
        try {
            // Check if job search should be scheduled (isApproved and isSubscribed)
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

            // CRITICAL: Check if user has active subscription (unless admin)
            val isAdmin = jobSearch.isAdmin == true
            if (!isAdmin && !shouldScheduleJobSearchForUser(jobSearch.userId)) {
                logger.info(
                    "Skipping scheduling for job search {} - user {} has no active subscription",
                    jobSearch.id,
                    jobSearch.userId
                )
                return SchedulingResult.Skipped(jobSearch.id, "no active subscription")
            }

            // Admin job searches bypass premium check and are treated as premium
            val isPremium = isAdmin || subscriptionService.checkPremiumAccess(jobSearch.userId)
            val effectiveTimePeriod = getEffectiveTimePeriodForJobSearch(jobSearch)
            val adjustedJobSearch = jobSearch.copy(timePeriod = effectiveTimePeriod)

            // Log the scheduling decision
            logger.info(
                "Scheduling job search {} for user {} with period '{}' (isPremium: {}, isAdmin: {})",
                jobSearch.id,
                jobSearch.userId,
                effectiveTimePeriod.displayName,
                isPremium,
                isAdmin
            )

            return internalJobSearchScheduler.scheduleJobSearch(adjustedJobSearch, isPremium)
        } catch (e: Exception) {
            logger.error("Error scheduling job search with subscription logic: ${jobSearch.id}", e)
            return SchedulingResult.Error(jobSearch.id, e)
        }
    }

    /**
     * Removes a job search from the scheduler
     */
    suspend fun removeJobSearch(searchId: String) {
        logger.info("Removing job search from scheduler: {}", searchId)
        internalJobSearchScheduler.removeJobSearch(searchId)
    }

    /**
     * Updates a job search with subscription-aware scheduling logic
     */
    suspend fun updateJobSearch(jobSearch: JobSearchOut) {
        try {
            logger.info("Updating job search with subscription logic: ${jobSearch.id}")

            internalJobSearchScheduler.removeJobSearch(jobSearch.id)
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

        val premiumCount = scheduled.count { it.isPremium }
        val freeCount = scheduled.count { !it.isPremium }

        logger.info(
            "$operation summary: Scheduled: ${scheduled.size} (Premium: $premiumCount, Free: $freeCount), Skipped: ${skipped.size}"
        )

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
     * Determines the effective time period based on job search's admin flag and user's subscription status
     */
    private fun getEffectiveTimePeriodForJobSearch(jobSearch: JobSearchOut): TimePeriod {
        // Admin job searches always get original time period (bypass premium check)
        if (jobSearch.isAdmin == true) {
            logger.info(
                "Admin job search {} using original period: {}",
                jobSearch.id,
                jobSearch.timePeriod.displayName
            )
            return jobSearch.timePeriod
        }

        return getEffectiveTimePeriod(jobSearch.userId, jobSearch.timePeriod)
    }

    /**
     * Determines the effective time period based on user's subscription status
     * All users with active subscription (PREMIUM tier - weekly or monthly) get their selected frequency
     */
    private fun getEffectiveTimePeriod(userId: String, originalTimePeriod: TimePeriod): TimePeriod {
        val hasPremium = subscriptionService.checkPremiumAccess(userId)
        if (!hasPremium) {
            throw IllegalStateException(
                "Cannot determine effective time period for user $userId without active subscription. " +
                "This should have been caught earlier by shouldScheduleJobSearchForUser check."
            )
        }

        // All PREMIUM users (weekly or monthly, including trial) get their selected frequency
        logger.debug(
            "User {} has premium access, using selected period: {}",
            userId,
            originalTimePeriod.displayName
        )
        return originalTimePeriod
    }

    /**
     * Reschedules all job searches for a specific user based on their current subscription status
     * Called when subscription status changes (upgrade/downgrade)
     */
    suspend fun rescheduleAllSearchesForUser(userId: String) {
        try {
            logger.info("Rescheduling all job searches for user: {}", userId)

            val userSearches = jobSearchRepository.findByUserId(userId)
            val schedulableSearches = userSearches.filter { shouldScheduleJobSearch(it) }

            if (schedulableSearches.isEmpty()) {
                logger.info("No schedulable searches to reschedule for user: {}", userId)
                return
            }

            val results = schedulableSearches.map { jobSearch ->
                internalJobSearchScheduler.removeJobSearch(jobSearch.id)
                scheduleJobSearchWithSubscriptionLogic(jobSearch)
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
     * Schedules initial job searches on application startup with subscription-aware logic
     */
    suspend fun scheduleInitialJobSearches(jobSearches: List<JobSearchOut>) {
        logger.info("Scheduling {} initial job searches with subscription awareness", jobSearches.size)

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
     * Schedules all approved and subscribed job searches for a specific user
     * Called when a user adds their first notification destination
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
