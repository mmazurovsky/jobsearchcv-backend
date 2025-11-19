package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComQueueJob
import com.jobsearchcv.backend.repository.XComQueueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * Service for managing X.com job posting queue
 * Jobs are added immediately - worker handles posting delays
 */
@Service
class XComQueueService(
    private val xcomQueueRepository: XComQueueRepository,
    private val xcomMessageComposer: XComMessageComposer
) {
    private val logger = LoggerFactory.getLogger(XComQueueService::class.java)

    /**
     * Enqueues jobs for X.com posting
     * Jobs are saved immediately - the worker posts them with 3-8 minute delays
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

        val now = OffsetDateTime.now()
        val queueJobs = jobs.map { job ->
            val xcomJobData = xcomMessageComposer.createXComJobData(job)
            val tweetText = xcomMessageComposer.formatTweet(xcomJobData)

            XComQueueJob.create(
                userId = userId,
                username = username,
                jobData = xcomJobData,
                tweetText = tweetText,
                scheduledAt = now
            )
        }

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
