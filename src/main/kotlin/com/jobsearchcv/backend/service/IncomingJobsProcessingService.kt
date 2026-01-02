package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.repository.SentJobRepository
import com.jobsearchcv.backend.repository.ScoredJobRepository
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.service.XComQueueService
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class IncomingJobsProcessingService(
    private val sentJobRepository: SentJobRepository,
    private val scoredJobRepository: ScoredJobRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val batchJobProcessingService: BatchJobProcessingService,
    private val destinationRepository: DestinationRepository,
    private val emailTemplateService: EmailTemplateService,
    private val asyncEmailService: AsyncEmailService,
    private val subscriptionService: SubscriptionService,
    private val xcomQueueService: XComQueueService,
    private val firebaseAuthService: FirebaseAuthService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun processIncomingJobData(
        jobSearchId: String,
        scrapedJobs: List<ScrapedJobData>,
        userId: String,
        searchName: String? = null
    ) {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch job search
                val savedJobSearch = jobSearchRepository.findById(jobSearchId)
                if (savedJobSearch == null) {
                    logger.error("Job search not found: {}", jobSearchId)
                    Sentry.captureMessage("Job search not found: $jobSearchId", SentryLevel.ERROR)
                    return@withContext
                }
                logger.info("Processing job data for searchId={}", jobSearchId)

                // Handle empty results
                if (scrapedJobs.isEmpty()) {
                    logger.info("No job data received for jobSearchId={}", jobSearchId)
                    if (shouldSendNoResultsEmail(savedJobSearch)) {
                        sendNoResultsEmail(userId, savedJobSearch, "No jobs were found matching your criteria")
                    }
                    return@withContext
                }

                // Deduplicate jobs
                val sentJobs = sentJobRepository.findByUserId(userId)
                val sentJobUrls = sentJobs.map { it.jobUrl }.toSet()
                val newJobs = scrapedJobs.filter { it.link !in sentJobUrls }

                logger.info("Received {} jobs, {} are new for user {}", scrapedJobs.size, newJobs.size, userId)

                // Handle all duplicates
                if (newJobs.isEmpty()) {
                    if (shouldSendNoResultsEmail(savedJobSearch)) {
                        sendNoResultsEmail(userId, savedJobSearch,
                            "All ${scrapedJobs.size} jobs found have already been sent to you.")
                    }
                    return@withContext
                }

                // Determine destination channel
                val destinations = destinationRepository.findByUserId(userId)
                val latestDestination = destinations.maxByOrNull { it.createdAt } ?: destinations.firstOrNull()

                if (latestDestination == null) {
                    logger.error("No destination found for user $userId, skipping processing")
                    return@withContext
                }

                val channel = latestDestination.channel
                logger.info("Delegating to channel-specific processor: $channel")

                // Delegate to channel-specific processors
                when (channel) {
                    "email" -> processJobsForEmailChannel(
                        newJobs, savedJobSearch, userId, latestDestination, searchName
                    )
                    "xcom" -> processJobsForXcomChannel(
                        newJobs, savedJobSearch, userId, latestDestination
                    )
                    "page" -> processJobsForPageChannel(
                        newJobs, savedJobSearch, userId, latestDestination
                    )
                    else -> {
                        logger.warn("Unknown channel: $channel, skipping")
                    }
                }

            } catch (e: Exception) {
                logger.error("Error processing incoming job data for jobSearchId={}, userId={}", jobSearchId, userId, e)
            }
        }
    }

    private suspend fun markJobsAsSent(
        jobs: List<ScoredJobData>,
        userId: String,
        destination: String?
    ) {
        try {
            val sentJobEntities = jobs.map { job ->
                SentJobOut(
                    userId = userId,
                    jobUrl = job.link,
                    destination = destination,
                    internalId = job.internalId
                )
            }
            sentJobRepository.saveAll(sentJobEntities)
            logger.info("Marked ${sentJobEntities.size} jobs as sent for user $userId")

        } catch (e: Exception) {
            logger.error("Error marking jobs as sent for user $userId", e)
        }
    }

    private suspend fun saveScoredJobs(
        jobs: List<ScoredJobData>,
        userId: String,
        jobSearchId: String,
        destination: String?,
        status: String?
    ) {
        try {
            val scoredJobEntities = jobs.map { job ->
                ScoredJobOut(
                    userId = userId,
                    jobSearchId = jobSearchId,
                    internalId = job.internalId,
                    title = job.title,
                    company = job.company,
                    location = job.location,
                    jobUrl = job.link,
                    description = job.description,
                    applicants = job.applicants,
                    techstack = job.techstack,
                    tags = job.tags,
                    salary = job.salary,
                    compatibilityScore = job.compatibilityScore,
                    status = status,
                    destination = destination,
                    savedAt = OffsetDateTime.now(),
                )
            }
            scoredJobRepository.saveAll(scoredJobEntities)
            logger.info("Saved ${scoredJobEntities.size} scored jobs for user $userId with status=$status")

        } catch (e: Exception) {
            logger.error("Error saving scored jobs for user $userId", e)
        }
    }

    private fun buildSearchDisplayName(jobSearch: JobSearchOut, specialSearchName: String?): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(jobSearch.jobTitle.ifBlank { "Your Job Search" })
        jobSearch.location?.let { if (it.isNotBlank()) stringBuilder.append(" in $it") }
        val interval = specialSearchName ?: jobSearch.timePeriod?.displayName
        interval?.let { stringBuilder.append(" in the last $it") }
        return stringBuilder.toString()
    }

    /**
     * EMAIL Channel: Full LLM pipeline with scoring and filtering.
     * Only sends jobs with compatibility score > 70.
     */
    private suspend fun processJobsForEmailChannel(
        jobs: List<ScrapedJobData>,
        jobSearch: JobSearchOut,
        userId: String,
        destination: Destination,
        specialSearchName: String?
    ) {
        try {
            logger.info("[EMAIL] Processing ${jobs.size} jobs for user $userId")

            // Full LLM pipeline: Translation → Enrichment → Save → Scoring
            val scoredJobs = batchJobProcessingService.processAndSaveJobsDataBatch(jobs, jobSearch)

            // Filter by compatibility score > 70
            val filteredJobs = scoredJobs.filter { it.compatibilityScore > 70 }
            logger.info("[EMAIL] {} of {} jobs passed compatibility filter", filteredJobs.size, scoredJobs.size)

            if (filteredJobs.isEmpty()) {
                if (shouldSendNoResultsEmail(jobSearch)) {
                    sendNoResultsEmail(userId, jobSearch,
                        "No high-quality matches found this time")
                }
                return
            }

            // Get email from Firebase instead of destination
            val recipientEmail = firebaseAuthService.getUserEmail(userId)
            if (recipientEmail == null) {
                logger.error("[EMAIL] No email found for user $userId in Firebase, skipping")
                return
            }
            val displaySearchName = buildSearchDisplayName(jobSearch, specialSearchName)
            val hasPremiumAccess = subscriptionService.checkPremiumAccess(userId)

            val emailContent = emailTemplateService.createJobNotificationEmail(
                recipient = recipientEmail,
                searchName = displaySearchName,
                jobs = filteredJobs,
                alertId = jobSearch.id,
                specialMessage = null,
                userId = userId,
                isFreeTier = !hasPremiumAccess && jobSearch.isAdmin != true
            )

            asyncEmailService.sendEmailAsync(emailContent)
            logger.info("[EMAIL] Queued email to $recipientEmail with ${filteredJobs.size} jobs")

            // Save scored jobs and mark as sent
            saveScoredJobs(filteredJobs, userId, jobSearch.id, recipientEmail, "unseen")
            markJobsAsSent(filteredJobs, userId, recipientEmail)
            logger.info("[EMAIL] Successfully processed and sent ${filteredJobs.size} jobs")

        } catch (e: Exception) {
            logger.error("[EMAIL] Error processing jobs for user $userId", e)
        }
    }

    /**
     * XCOM Channel: Translation + Enrichment only (no scoring).
     * Sends ALL jobs to X.com queue without filtering.
     */
    private suspend fun processJobsForXcomChannel(
        jobs: List<ScrapedJobData>,
        jobSearch: JobSearchOut,
        userId: String,
        destination: Destination
    ) {
        try {
            logger.info("[XCOM] Processing ${jobs.size} jobs for user $userId")

            // Partial pipeline: Translation → Enrichment → Save (NO scoring)
            val enrichedJobs = batchJobProcessingService.processJobsForXcomOrPageChannel(jobs, jobSearch)
            logger.info("[XCOM] Enriched and saved ${enrichedJobs.size} jobs")

            // Enqueue ALL jobs to X.com (no filtering)
            val username = destination.channelValue
            val enqueuedCount = xcomQueueService.enqueueJobs(enrichedJobs, username, userId)
            logger.info("[XCOM] Enqueued $enqueuedCount jobs for posting (username: $username)")
            markJobsAsSent(enrichedJobs, userId, username)
            logger.info("[XCOM] Successfully processed and enqueued ${enrichedJobs.size} jobs")

        } catch (e: Exception) {
            logger.error("[XCOM] Error processing jobs for user $userId", e)
        }
    }

    /**
     * PAGE Channel: Translation + Enrichment only (no scoring).
     * Saves ALL jobs to database for in-app viewing (no external delivery).
     * Creates ONE X.com post with batch overview after jobs are marked as sent.
     */
    private suspend fun processJobsForPageChannel(
        jobs: List<ScrapedJobData>,
        jobSearch: JobSearchOut,
        userId: String,
        destination: Destination
    ) {
        try {
            logger.info("[PAGE] Processing ${jobs.size} jobs for user $userId")

            // Partial pipeline: Translation → Enrichment → Save (NO scoring)
            val enrichedJobs = batchJobProcessingService.processJobsForXcomOrPageChannel(jobs, jobSearch)
            logger.info("[PAGE] Enriched and saved ${enrichedJobs.size} jobs")

            markJobsAsSent(enrichedJobs, userId, destination.channelValue)
            logger.info("[PAGE] Successfully processed and saved ${enrichedJobs.size} jobs")

            // Create X.com overview post after jobs are marked as sent (if enabled)
            if (destination.postOnX == true) {
                createPageOverviewPost(enrichedJobs, jobSearch, userId, destination)
            }

        } catch (e: Exception) {
            logger.error("[PAGE] Error processing jobs for user $userId", e)
        }
    }

    /**
     * Creates an X.com overview post for a batch of PAGE channel jobs
     * Post is only created if pagePath and channelValue are configured
     * Errors are logged but do not fail the job processing pipeline
     */
    private suspend fun createPageOverviewPost(
        enrichedJobs: List<ScoredJobData>,
        jobSearch: JobSearchOut,
        userId: String,
        destination: Destination
    ) {
        try {
            // Validation checks
            if (enrichedJobs.isEmpty()) {
                logger.info("[PAGE] No jobs to post overview for, skipping X.com post")
                return
            }

            if (destination.pagePath.isNullOrBlank()) {
                logger.info("[PAGE] No pagePath configured for destination ${destination.id}, skipping X.com post")
                return
            }

            if (destination.channelValue.isBlank()) {
                logger.warn("[PAGE] Empty channelValue for destination ${destination.id}, skipping X.com post")
                return
            }

            // Enqueue overview post with hashtags
            val success = xcomQueueService.enqueuePageOverviewPost(
                jobCount = enrichedJobs.size,
                jobSearch = jobSearch,
                username = destination.channelValue,
                pagePath = destination.pagePath,
                hashtags = destination.socialMediaTags,
                userId = userId
            )

            if (success) {
                logger.info("[PAGE] Successfully enqueued X.com overview post for ${enrichedJobs.size} jobs (pagePath: ${destination.pagePath})")
            } else {
                logger.error("[PAGE] Failed to enqueue X.com overview post for ${enrichedJobs.size} jobs")
            }

        } catch (e: Exception) {
            // Log error but don't propagate - X.com post creation should not fail job processing
            logger.error("[PAGE] Error creating X.com overview post for user $userId", e)
            io.sentry.Sentry.captureException(e)
        }
    }

    private fun shouldSendNoResultsEmail(jobSearch: JobSearchOut?): Boolean {
        // Only send emails for searches with 24h+ time periods
        return jobSearch?.timePeriod?.shouldSendNoResultsEmail() ?: false
    }

    private suspend fun sendNoResultsEmail(
        userId: String,
        jobSearch: JobSearchOut?,
        reason: String
    ) {
        try {
            // Get email from Firebase instead of destination
            val recipientEmail = firebaseAuthService.getUserEmail(userId)

            if (recipientEmail == null) {
                logger.info("No email found in Firebase for user: $userId, skipping no-results email")
                return
            }

            val searchName = jobSearch?.jobTitle ?: "Your Job Search"
            val timePeriod = jobSearch?.timePeriod?.displayName ?: "recent period"

            val emailContent = emailTemplateService.createNoResultsEmail(
                recipient = recipientEmail,
                searchName = searchName,
                timePeriod = timePeriod
            )

            // Send email asynchronously - fire and forget
            asyncEmailService.sendEmailAsync(emailContent)
            logger.info("Queued no-results email to user: $userId for search: ${jobSearch?.id}")

        } catch (e: Exception) {
            logger.error("Error sending no-results email to user: $userId", e)
        }
    }
}