package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComQueueJob
import com.jobsearchcv.backend.repository.XComQueueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Service for managing X.com job posting queue
 * Handles enqueueing jobs with random delays between posts
 */
@Service
class XComQueueService(
    private val xcomQueueRepository: XComQueueRepository,
    private val xcomMessageComposer: XComMessageComposer
) {
    private val logger = LoggerFactory.getLogger(XComQueueService::class.java)

    companion object {
        const val MIN_DELAY_MINUTES = 1
        const val MAX_DELAY_MINUTES = 5
    }

    /**
     * Enqueues jobs for X.com posting with random delays
     * First job is scheduled immediately, subsequent jobs are delayed by 1-5 minutes
     *
     * @param jobs List of scored jobs to post
     * @param username X.com username (from Destination.channelValue)
     * @param userId User ID who owns these jobs
     * @return Number of jobs enqueued
     */
    suspend fun enqueueJobs(
        jobs: List<ScoredJobData>,
        username: String,
        userId: String
    ): Int {
        if (jobs.isEmpty()) {
            logger.info("No jobs to enqueue for user $userId")
            return 0
        }

        logger.info("Enqueueing ${jobs.size} jobs for X.com posting (username: $username, userId: $userId)")

        val queueJobs = mutableListOf<XComQueueJob>()
        var scheduledAt = OffsetDateTime.now()

        for ((index, job) in jobs.withIndex()) {
            // Convert to XComJobData
            val xcomJobData = xcomMessageComposer.createXComJobData(job)

            // Format tweet text
            val tweetText = xcomMessageComposer.formatTweet(xcomJobData)

            // Create queue job
            val queueJob = XComQueueJob.create(
                userId = userId,
                username = username,
                jobData = xcomJobData,
                tweetText = tweetText,
                scheduledAt = scheduledAt
            )

            queueJobs.add(queueJob)

            // Calculate next scheduled time with random delay (except for last job)
            if (index < jobs.size - 1) {
                val delayMinutes = Random.nextInt(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES + 1)
                scheduledAt = scheduledAt.plusMinutes(delayMinutes.toLong())
                logger.debug("Job ${index + 1}/${jobs.size} scheduled at ${queueJob.scheduledAt}, next delay: $delayMinutes minutes")
            }
        }

        // Save all queue jobs at once
        xcomQueueRepository.saveAll(queueJobs)

        logger.info("Successfully enqueued ${queueJobs.size} jobs for X.com posting")
        return queueJobs.size
    }

    /**
     * Gets pending jobs count for a user
     */
    suspend fun getPendingJobsCount(userId: String): Int {
        val jobs = xcomQueueRepository.findByUserId(userId)
        return jobs.count { it.statusEnum.name == "PENDING" }
    }
}
