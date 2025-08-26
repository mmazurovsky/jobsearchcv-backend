package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.SentJobOut
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface SentJobRepository {
    fun save(sentJob: SentJobOut): SentJobOut
    fun saveAll(sentJobs: List<SentJobOut>): List<SentJobOut>
    fun findById(id: String): SentJobOut?
    fun findAll(): List<SentJobOut>
    fun findByUserId(userId: String): List<SentJobOut>
    fun existsByUserIdAndJobUrl(userId: String, jobUrl: String): Boolean
    fun deleteById(id: String)
    fun countByDestinationAndSentAtAfter(destination: String, sentAtAfter: OffsetDateTime): Long
}

@Repository
class SentJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : SentJobRepository {

    override fun save(sentJob: SentJobOut): SentJobOut {
        return mongoTemplate.save(sentJob)
    }

    override fun saveAll(sentJobs: List<SentJobOut>): List<SentJobOut> {
        return mongoTemplate.insertAll(sentJobs).toList()
    }

    override fun findById(id: String): SentJobOut? {
        return mongoTemplate.findById(id, SentJobOut::class.java)
    }

    override fun findAll(): List<SentJobOut> {
        return mongoTemplate.findAll(SentJobOut::class.java)
    }

    override fun findByUserId(userId: String): List<SentJobOut> {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.find(query, SentJobOut::class.java)
    }

    override fun existsByUserIdAndJobUrl(userId: String, jobUrl: String): Boolean {
        val query = Query(Criteria.where("user_id").`is`(userId).and("job_url").`is`(jobUrl))
        return mongoTemplate.exists(query, SentJobOut::class.java)
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("id").`is`(id))
        mongoTemplate.remove(query, SentJobOut::class.java)
    }

    override fun countByDestinationAndSentAtAfter(destination: String, sentAtAfter: OffsetDateTime): Long {
        val query = Query(Criteria.where("destination").`is`(destination).and("sent_at").gte(sentAtAfter))
        return mongoTemplate.count(query, SentJobOut::class.java)
    }
} 