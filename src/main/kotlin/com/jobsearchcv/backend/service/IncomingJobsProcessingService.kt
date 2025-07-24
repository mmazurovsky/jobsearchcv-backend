package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.TelegramMessages
import com.jobsearchcv.backend.TelegramMessages.CREATE_ALERT_DESC
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.repository.SentJobRepository
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.jvm.optionals.getOrNull

@Service
class IncomingJobsProcessingService(
    private val sentJobRepository: SentJobRepository,
    private val jobSearchRepository: JobSearchRepository,
    private val batchJobProcessingService: BatchJobProcessingService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    companion object {
        private const val XCOM_DAILY_JOB_LIMIT = 50
        private const val XCOM_RATE_LIMIT_HOURS = 24
    }

    suspend fun processIncomingJobData(
        jobSearchId: String,
        scrapedJobs: List<ScrapedJobData>,
        userId: String
    ) {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Determine if this is an immediate search and fetch job search
                var isImmediateSearch = false
                var savedJobSearch: JobSearchOut? = null

                if (jobSearchId.startsWith("temp-")) {
                    isImmediateSearch = true
                    logger.info("Processing job data for temp job for user $userId, searchId=$jobSearchId")
                } else {
                    savedJobSearch = jobSearchRepository.findById(jobSearchId)
                    if (savedJobSearch == null) {
                        logger.error("Job search not found: {}", jobSearchId)
                        Sentry.captureMessage(
                            "Job search not found: $jobSearchId",
                            SentryLevel.ERROR
                        )
                        return@withContext
                    } else {
                        logger.info("Processing job data for alert searchId={}", jobSearchId)
                    }
                }

                if (scrapedJobs.isEmpty()) {
                    logger.info("No job data received for jobSearchId={}", jobSearchId)
                    // Notify user only for immediate searches when no jobs found
                    if (isImmediateSearch) {
//                        TODO: sendNoResults
                    }
                    return@withContext
                }

                val destination = savedJobSearch?.destination

                val sentJobs = if (destination != null) {
                    sentJobRepository.findByDestination(destination)
                } else {
                    sentJobRepository.findByUserId(userId)
                }

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

                    if (isImmediateSearch) {
//                        TODO: sendnoresults
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

                    if (isImmediateSearch) {
//                        TODO: sendnoresults
                    }
                    return@withContext
                }

                // Step 8: Route jobs based on destination
                val jobsToMarkAsSent = if (destination == "xcom_us_tech") {
                    // Post to X.com without compatibility score filtering
                    logger.info("Routing jobs to X.com for jobSearchId={}", jobSearchId)
                    sendJobsToXCom(scoredJobsData, savedJobSearch)
                } else {
                    // Default: Send to Telegram with compatibility score filtering
                    logger.info("Routing jobs to Telegram for jobSearchId={}", jobSearchId)
//                    TODO: send email or tg
                    filteredJobs // All filtered jobs are considered successfully sent to Telegram
                }

                // Only mark jobs as sent if they were actually successfully processed
                if (jobsToMarkAsSent.isNotEmpty()) {
                    markJobsAsSent(jobsToMarkAsSent, userId, destination = destination)
                }

                logger.info(
                    "Successfully processed and sent {} jobs for jobSearchId={}, userId={}",
                    jobsToMarkAsSent.size, jobSearchId, userId
                )

                jobsToMarkAsSent.size

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

    private suspend fun sendJobsToXCom(
        jobs: List<ScoredJobData>,
        jobSearch: JobSearchOut
    ): List<ScoredJobData> {
        return try {
            logger.info("[JobSearch: ${jobSearch.id}] Sending ${jobs.size} jobs to X.com (no compatibility filtering)")

            // Check rate limit: 50 jobs per 24 hours
            val twentyFourHoursAgo = OffsetDateTime.now().minusHours(XCOM_RATE_LIMIT_HOURS.toLong())
            val jobsSentLast24Hours = sentJobRepository.countByDestinationAndSentAtAfter(
                destination = "xcom_us_tech",
                sentAtAfter = twentyFourHoursAgo
            )
            
            logger.info("[JobSearch: ${jobSearch.id}] Jobs sent to X.com in last 24 hours: $jobsSentLast24Hours")
            
            val remainingQuota = XCOM_DAILY_JOB_LIMIT - jobsSentLast24Hours
            if (remainingQuota <= 0) {
                logger.warn("[JobSearch: ${jobSearch.id}] Daily limit reached for X.com. Skipping all ${jobs.size} jobs.")
                return emptyList()
            }
            
            // Take only as many jobs as we can send within the limit
            val jobsToSend = if (jobs.size > remainingQuota) {
                logger.info("[JobSearch: ${jobSearch.id}] Limiting jobs to send from ${jobs.size} to $remainingQuota due to daily quota")
                jobs.take(remainingQuota.toInt())
            } else {
                jobs
            }

            // Post jobs to X.com without compatibility score filtering
//            TODO: use x scraper to post


            emptyList()

        } catch (e: Exception) {
            logger.error(
                "[JobSearch: ${jobSearch.id}] Error sending jobs to X.com: ${e.message}",
                e
            )
            emptyList()
        }
    }

    private suspend fun markJobsAsSent(jobs: List<ScoredJobData>, userId: String, destination: String?) {
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
}