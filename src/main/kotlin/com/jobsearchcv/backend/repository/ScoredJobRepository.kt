package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.ScoredJobOut
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface ScoredJobRepository {
    fun saveAll(scoredJobs: List<ScoredJobOut>): List<ScoredJobOut>
    fun findByUserIdAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut>
    fun findByUserIdAndStatusAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        status: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut>
    fun findByUserIdAndJobSearchIdAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        jobSearchId: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut>
    fun findByUserIdAndJobSearchIdAndStatusAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        jobSearchId: String,
        status: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut>
}

@Repository
class ScoredJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : ScoredJobRepository {

    override fun saveAll(scoredJobs: List<ScoredJobOut>): List<ScoredJobOut> {
        return mongoTemplate.insertAll(scoredJobs).toList()
    }

    override fun findByUserIdAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut> {
        val query = Query(
            Criteria.where("user_id").`is`(userId)
                .and("sent_at").gte(sentAtAfter)
        ).with(Sort.by(Sort.Direction.DESC, "sent_at"))

        return mongoTemplate.find(query, ScoredJobOut::class.java)
    }

    override fun findByUserIdAndStatusAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        status: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut> {
        val query = Query(
            Criteria.where("user_id").`is`(userId)
                .and("status").`is`(status)
                .and("sent_at").gte(sentAtAfter)
        ).with(Sort.by(Sort.Direction.DESC, "sent_at"))

        return mongoTemplate.find(query, ScoredJobOut::class.java)
    }

    override fun findByUserIdAndJobSearchIdAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        jobSearchId: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut> {
        val query = Query(
            Criteria.where("user_id").`is`(userId)
                .and("job_search_id").`is`(jobSearchId)
                .and("sent_at").gte(sentAtAfter)
        ).with(Sort.by(Sort.Direction.DESC, "sent_at"))

        return mongoTemplate.find(query, ScoredJobOut::class.java)
    }

    override fun findByUserIdAndJobSearchIdAndStatusAndSentAtAfterOrderBySentAtDesc(
        userId: String,
        jobSearchId: String,
        status: String,
        sentAtAfter: OffsetDateTime
    ): List<ScoredJobOut> {
        val query = Query(
            Criteria.where("user_id").`is`(userId)
                .and("job_search_id").`is`(jobSearchId)
                .and("status").`is`(status)
                .and("sent_at").gte(sentAtAfter)
        ).with(Sort.by(Sort.Direction.DESC, "sent_at"))

        return mongoTemplate.find(query, ScoredJobOut::class.java)
    }
}
