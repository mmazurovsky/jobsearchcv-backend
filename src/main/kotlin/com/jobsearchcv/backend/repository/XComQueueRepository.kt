package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.QueueStatus
import com.jobsearchcv.backend.domain.model.XComQueueJob
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface XComQueueRepository {
    fun save(queueJob: XComQueueJob): XComQueueJob
    fun saveAll(queueJobs: List<XComQueueJob>): List<XComQueueJob>
    fun findById(id: String): XComQueueJob?
    fun findPendingJobsScheduledBefore(scheduledBefore: OffsetDateTime): List<XComQueueJob>
    fun updateStatus(id: String, status: QueueStatus, postedAt: OffsetDateTime?, tweetId: String?, error: String?): Boolean
    fun incrementRetryCount(id: String): Boolean
    fun findByUserId(userId: String): List<XComQueueJob>
}

@Repository
class XComQueueRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : XComQueueRepository {

    override fun save(queueJob: XComQueueJob): XComQueueJob {
        return mongoTemplate.save(queueJob)
    }

    override fun saveAll(queueJobs: List<XComQueueJob>): List<XComQueueJob> {
        return mongoTemplate.insertAll(queueJobs).toList()
    }

    override fun findById(id: String): XComQueueJob? {
        return mongoTemplate.findById(id, XComQueueJob::class.java)
    }

    override fun findPendingJobsScheduledBefore(scheduledBefore: OffsetDateTime): List<XComQueueJob> {
        val query = Query(
            Criteria.where("status").`is`(QueueStatus.PENDING.name)
                .and("scheduled_at").lte(scheduledBefore)
        )
            .with(Sort.by(Sort.Direction.DESC, "scheduled_at"))
            .limit(10) // Fetch up to 10 jobs per batch
        return mongoTemplate.find(query, XComQueueJob::class.java)
    }

    override fun updateStatus(
        id: String,
        status: QueueStatus,
        postedAt: OffsetDateTime?,
        tweetId: String?,
        error: String?
    ): Boolean {
        val query = Query(Criteria.where("id").`is`(id))
        val update = Update()
            .set("status", status.name)
            .set("posted_at", postedAt)
            .set("tweet_id", tweetId)
            .set("error", error)

        val result = mongoTemplate.updateFirst(query, update, XComQueueJob::class.java)
        return result.modifiedCount > 0
    }

    override fun incrementRetryCount(id: String): Boolean {
        val query = Query(Criteria.where("id").`is`(id))
        val update = Update().inc("retry_count", 1)

        val result = mongoTemplate.updateFirst(query, update, XComQueueJob::class.java)
        return result.modifiedCount > 0
    }

    override fun findByUserId(userId: String): List<XComQueueJob> {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.find(query, XComQueueJob::class.java)
    }
}
