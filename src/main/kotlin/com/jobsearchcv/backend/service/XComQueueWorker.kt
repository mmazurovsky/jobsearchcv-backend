package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.QueueStatus
import com.jobsearchcv.backend.domain.model.XComQueueJob
import com.jobsearchcv.backend.repository.XComQueueRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * Background worker that processes the X.com job posting queue
 * Runs every 30 seconds to post jobs that are due
 */
@Service
class XComQueueWorker(
    private val xcomQueueRepository: XComQueueRepository,
    private val xcomClient: XComClient
) {
    private val logger = LoggerFactory.getLogger(XComQueueWorker::class.java)

    companion object {
        const val MAX_RETRIES = 3
    }

    /**
     * Scheduled task that runs every 30 seconds
     * Fetches and processes pending jobs that are due to be posted
     */
    @Scheduled(fixedDelay = 90000) 
    fun processQueue() {
        runBlocking {
            try {
                val now = OffsetDateTime.now()
                val pendingJobs = xcomQueueRepository.findPendingJobsScheduledBefore(now)

                if (pendingJobs.isEmpty()) {
                    logger.debug("No pending X.com jobs to process")
                    return@runBlocking
                }

                logger.info("Processing ${pendingJobs.size} pending X.com jobs")

                for (job in pendingJobs) {
                    processJob(job)
                }

                logger.info("Finished processing X.com queue batch")
            } catch (e: Exception) {
                logger.error("Error processing X.com queue", e)
            }
        }
    }

    /**
     * Processes a single queue job
     * Posts to X.com and updates the job status
     */
    private suspend fun processJob(job: XComQueueJob) {
        try {
            logger.debug("Posting job ${job.id} to X.com (username: ${job.username})")

            // Post to X.com
            val result = xcomClient.postTweet(job.username, job.tweetText)

            result.fold(
                onSuccess = { response ->
                    // Success - update status to POSTED
                    xcomQueueRepository.updateStatus(
                        id = job.id,
                        status = QueueStatus.POSTED,
                        postedAt = OffsetDateTime.now(),
                        tweetId = response.tweetId,
                        error = null
                    )
                    logger.info("Successfully posted job ${job.id} to X.com, tweetId: ${response.tweetId}")
                },
                onFailure = { error ->
                    // Failure - check if we should retry or mark as failed
                    handleJobFailure(job, error)
                }
            )
        } catch (e: Exception) {
            logger.error("Exception processing job ${job.id}", e)
            handleJobFailure(job, e)
        }
    }

    /**
     * Handles job posting failure
     * Retries up to MAX_RETRIES times, then marks as FAILED
     */
    private fun handleJobFailure(job: XComQueueJob, error: Throwable) {
        val newRetryCount = job.retryCount + 1

        if (newRetryCount < MAX_RETRIES) {
            // Increment retry count and keep status as PENDING
            xcomQueueRepository.incrementRetryCount(job.id)
            logger.warn("Job ${job.id} failed, will retry (attempt $newRetryCount/$MAX_RETRIES): ${error.message}")
        } else {
            // Max retries reached - mark as FAILED
            xcomQueueRepository.updateStatus(
                id = job.id,
                status = QueueStatus.FAILED,
                postedAt = null,
                tweetId = null,
                error = error.message ?: "Unknown error"
            )
            logger.error("Job ${job.id} failed after $MAX_RETRIES retries: ${error.message}")
        }
    }
}
