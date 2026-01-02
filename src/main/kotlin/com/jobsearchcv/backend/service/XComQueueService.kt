package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComJobData
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
        val queueJobs = jobs.filter {
            (it.techstack.size > 3 && it.salary != null)
        }
        .take(5) // Limit to 5 jobs for X.com posting
        .map { job ->
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

    /**
     * Enqueues a single page overview post to X.com queue
     * @param jobCount Number of jobs in the batch
     * @param jobSearch Job search configuration
     * @param username X.com username (from Destination.channelValue)
     * @param pagePath Page path for the overview URL (from Destination.pagePath)
     * @param hashtags Optional list of hashtags to include (from Destination.socialMediaTags)
     * @param userId User ID who owns this post
     * @return true if enqueued successfully, false otherwise
     */
    suspend fun enqueuePageOverviewPost(
        jobCount: Int,
        jobSearch: JobSearchOut,
        username: String,
        pagePath: String,
        hashtags: List<String>?,
        userId: String
    ): Boolean {
        try {
            logger.info("Enqueueing page overview post for $jobCount jobs (username: $username, userId: $userId)")

            // Format tweet text with hashtags
            val tweetText = xcomMessageComposer.formatPageOverviewTweet(
                jobCount = jobCount,
                jobSearch = jobSearch,
                pagePath = pagePath,
                hashtags = hashtags
            )

            // Create placeholder XComJobData (required by XComQueueJob schema)
            val summaryJobData = XComJobData(
                internalId = "page-overview-${jobSearch.id}",
                title = "Page Overview: ${jobSearch.jobTitle}",
                company = "Multiple Companies",
                location = jobSearch.location,
                techstack = emptyList(),
                salary = null,
                internalJobLink = ""
            )

            // Create and save queue job
            val queueJob = XComQueueJob.create(
                userId = userId,
                username = username,
                jobData = summaryJobData,
                tweetText = tweetText,
                scheduledAt = OffsetDateTime.now()
            )

            xcomQueueRepository.save(queueJob)

            logger.info("Successfully enqueued page overview post (tweetLength: ${tweetText.length} chars)")
            return true

        } catch (e: Exception) {
            logger.error("Failed to enqueue page overview post for user $userId", e)
            return false
        }
    }
}
