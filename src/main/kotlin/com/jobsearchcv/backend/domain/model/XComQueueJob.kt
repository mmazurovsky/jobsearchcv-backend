package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Represents a job posting queued for X.com (Twitter) distribution
 * Posts are scheduled with random delays (1-5 minutes) between them
 *
 * Indexes:
 * - Compound index on (status, scheduled_at) for efficient queue worker queries
 * - Simple index on user_id for user-specific queries
 */
@Document(collection = "xcom_queue_jobs")
@CompoundIndexes(
    CompoundIndex(
        name = "status_scheduled_idx",
        def = "{'status': 1, 'scheduled_at': 1}"
    )
)
data class XComQueueJob(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Indexed(unique = false)
    @field:Field("user_id")
    val userId: String,

    @field:Field("username")
    val username: String, // X.com username from Destination.channelValue

    @field:Field("job_data")
    val jobData: XComJobData,

    @field:Field("tweet_text")
    val tweetText: String, // Pre-formatted message within 280 character limit

    @field:Field("status")
    val status: String, // QueueStatus enum value stored as string

    @field:Field("scheduled_at")
    val scheduledAt: OffsetDateTime, // When this job should be posted

    @field:Field("posted_at")
    val postedAt: OffsetDateTime? = null, // When this job was actually posted

    @field:Field("tweet_id")
    val tweetId: String? = null, // X.com tweet ID from successful post

    @field:Field("error")
    val error: String? = null, // Error message if posting failed

    @field:Field("retry_count")
    val retryCount: Int = 0, // Number of retry attempts

    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    val statusEnum: QueueStatus
        get() = QueueStatus.valueOf(status)

    companion object {
        fun create(
            userId: String,
            username: String,
            jobData: XComJobData,
            tweetText: String,
            scheduledAt: OffsetDateTime
        ): XComQueueJob {
            return XComQueueJob(
                id = UUID.randomUUID().toString(),
                userId = userId,
                username = username,
                jobData = jobData,
                tweetText = tweetText,
                status = QueueStatus.PENDING.name,
                scheduledAt = scheduledAt,
                createdAt = OffsetDateTime.now()
            )
        }
    }
}

/**
 * Status of a queued X.com job
 */
enum class QueueStatus {
    PENDING,  // Waiting to be posted
    POSTED,   // Successfully posted to X.com
    FAILED    // Failed to post after retries
}
