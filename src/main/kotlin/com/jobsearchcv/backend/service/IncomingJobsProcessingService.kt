package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.repository.SentJobRepository
import com.jobsearchcv.backend.repository.DestinationRepository
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class IncomingJobsProcessingService(
    private val sentJobRepository: SentJobRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val batchJobProcessingService: BatchJobProcessingService,
    private val destinationRepository: DestinationRepository,
    private val emailTemplateService: EmailTemplateService,
    private val asyncEmailService: AsyncEmailService,
    private val subscriptionService: SubscriptionService,
    private val xcomQueueService: XComQueueService,
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
                // Step 1: Fetch job search
                val savedJobSearch = jobSearchRepository.findById(jobSearchId)
                if (savedJobSearch == null) {
                    logger.error("Job search not found: {}", jobSearchId)
                    Sentry.captureMessage(
                        "Job search not found: $jobSearchId",
                        SentryLevel.ERROR
                    )
                    return@withContext
                }
                logger.info("Processing job data for alert searchId={}", jobSearchId)

                if (scrapedJobs.isEmpty()) {
                    logger.info("No job data received for jobSearchId={}", jobSearchId)
                    // Send no-results email for searches with 24h+ time periods
                    if (shouldSendNoResultsEmail(savedJobSearch)) {
                        sendNoResultsEmail(
                            userId,
                            savedJobSearch,
                            "No jobs were found matching your criteria"
                        )
                    }
                    return@withContext
                }

                val sentJobs = sentJobRepository.findByUserId(userId)


                val sentJobUrls = sentJobs.map { it.jobUrl }.toSet()
                val newJobs = scrapedJobs.filter { it.link !in sentJobUrls }

                logger.info(
                    "Received {} jobs from scraper, {} are new for user {}",
                    scrapedJobs.size, newJobs.size, userId
                )

                if (newJobs.isEmpty()) {
                    // Notify user about the reason for no results
                    val message =
                        "All ${scrapedJobs.size} jobs found have already been sent to you previously."

                    if (shouldSendNoResultsEmail(savedJobSearch)) {
                        sendNoResultsEmail(userId, savedJobSearch, message)
                    }
                    return@withContext
                }

                // Step 4: Process jobs using efficient batch processing (includes saving to DB)
                val scoredJobsData =
                    batchJobProcessingService.processAndSaveJobsDataBatch(newJobs, savedJobSearch)

                // Step 6: Filter by compatibility score > 59
                val filteredJobs = scoredJobsData.filter {
                    it.compatibilityScore > 59
                }

                logger.info(
                    "{} jobs passed compatibility filter (score > 59) for jobSearchId={}, userId={}",
                    filteredJobs.size, jobSearchId, userId
                )

                if (filteredJobs.isEmpty()) {
                    val message =
                        "I looked up jobs published recently and I didn't find good matches for you this time \uD83D\uDE14. Try again with different parameters or set up an alert to monitor freshly published jobs."

                    if (shouldSendNoResultsEmail(savedJobSearch)) {
                        sendNoResultsEmail(userId, savedJobSearch, message)
                    }
                    return@withContext
                }

                // Step 8: Route jobs based on destination
                val sendJobs =
                    sendJobsToUserDestinations(filteredJobs, savedJobSearch, userId, searchName)

                // Only mark jobs as sent if they were actually successfully processed

                logger.info(
                    "Successfully processed and sent {} jobs for jobSearchId={}, userId={}",
                    sendJobs.size, jobSearchId, userId
                )

                sendJobs.size

            } catch (e: Exception) {
                logger.error(
                    "Error processing incoming job data for jobSearchId={}, userId={}",
                    jobSearchId,
                    userId,
                    e
                )

            }
        }
    }

    private suspend fun sendJobsToUserDestinations(
        jobs: List<ScoredJobData>,
        jobSearch: JobSearchOut?,
        userId: String,
        specialSearchName: String? = null
    ): List<ScoredJobData> {
        val destinations = destinationRepository.findByUserId(userId)
        if (destinations.isEmpty()) {
            logger.warn("No destinations found for user $userId, skipping email sending")
            return emptyList()
        }

        // Find the latest destination by createdAt
        var latestDestination = destinations.maxByOrNull { it.createdAt }
        if (latestDestination == null) {
            latestDestination = destinations.firstOrNull()
        }

        if (latestDestination == null) {
            logger.error(
                "Destination is null, skipping email sending for user $userId, jobSearchId=${jobSearch?.id}"
            )
            return emptyList()
        }

        logger.info("Using destination: channel=${latestDestination.channel}, value=${latestDestination.channelValue}")

        val jobsToMarkAsSent = try {
            logger.info("Sending ${jobs.size} jobs to user destinations for userId=$userId")

            // Get user destinations


            // Route based on channel type
            when (latestDestination.channel) {
                "email" -> {
                    val recipientEmail = latestDestination.channelValue
                    val location = jobSearch?.location
                    val stringBuilder = StringBuilder()
                    val jobTitle = jobSearch?.jobTitle ?: "Your Job Search"
                    stringBuilder.append(jobTitle)
                    if (location != null) {
                        stringBuilder.append(" in $location")
                    }
                    val interval = specialSearchName ?: jobSearch?.timePeriod?.displayName
                    if (interval != null) {
                        stringBuilder.append(" in the last ${interval}")
                    }

                    val displaySearchName = stringBuilder.toString()
                    val alertId = jobSearch?.id ?: "unknown"

                    logger.info("Sending email to $recipientEmail for ${jobs.size} jobs, searchName=$specialSearchName")

                    // Create email content using EmailTemplateService
                    val hasPremiumAccess = subscriptionService.checkPremiumAccess(userId)
                    val emailContent = emailTemplateService.createJobNotificationEmail(
                        recipient = recipientEmail,
                        searchName = displaySearchName,
                        jobs = jobs,
                        alertId = alertId,
                        specialMessage = if (specialSearchName == "Monthly Overview") "This is an overview of jobs posted in the last month, you receive it only one time after job search is created \uD83D\uDE0A" else null,
                        userId = userId,
                        isFreeTier = !hasPremiumAccess
                    )

                    // Send email asynchronously using AsyncEmailService - fire and forget
                    asyncEmailService.sendEmailAsync(emailContent)
                    logger.info("Queued email to $recipientEmail with ${jobs.size} jobs")

                    // Return jobs as successfully sent since we're fire-and-forget
                    jobs
                }
                "xcom" -> {
                    val username = latestDestination.channelValue
                    logger.info("Enqueueing ${jobs.size} jobs for X.com posting (username: $username)")

                    // Enqueue jobs for X.com posting with random delays
                    val enqueuedCount = xcomQueueService.enqueueJobs(jobs, username, userId)
                    logger.info("Successfully enqueued $enqueuedCount jobs for X.com posting")

                    // Return jobs as successfully sent since they're now queued
                    jobs
                }
                else -> {
                    logger.info("Channel '${latestDestination.channel}' is not supported, skipping")
                    emptyList()
                }
            }

        } catch (e: Exception) {
            logger.error("Error sending jobs to user destinations for userId=$userId", e)
            emptyList()
        }

        if (jobsToMarkAsSent.isNotEmpty()) {
            markJobsAsSent(jobsToMarkAsSent, userId, destination = latestDestination.channelValue)
        }
        return jobsToMarkAsSent
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
                )
            }
            sentJobRepository.saveAll(sentJobEntities)
            logger.info("Marked ${sentJobEntities.size} jobs as sent for user $userId")

        } catch (e: Exception) {
            logger.error("Error marking jobs as sent for user $userId", e)
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
            val destinations = destinationRepository.findByUserId(userId)
            val emailDestination = destinations.find { it.channel == "email" }

            if (emailDestination == null) {
                logger.info("No email destination found for user: $userId, skipping no-results email")
                return
            }

            val searchName = jobSearch?.jobTitle ?: "Your Job Search"
            val timePeriod = jobSearch?.timePeriod?.displayName ?: "recent period"

            val emailContent = emailTemplateService.createNoResultsEmail(
                recipient = emailDestination.channelValue,
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