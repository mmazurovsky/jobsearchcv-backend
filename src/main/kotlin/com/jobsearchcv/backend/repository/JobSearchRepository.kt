package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.JobSearchOut
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

interface JobSearchRepository {
    fun save(jobSearch: JobSearchOut): JobSearchOut
    fun saveAll(jobSearches: List<JobSearchOut>): List<JobSearchOut>
    fun findById(id: String): JobSearchOut?
    fun findAll(): List<JobSearchOut>
    fun findByUserId(userId: String): List<JobSearchOut>
    fun findByUserIdAndIsApproved(userId: String, isApproved: Boolean): List<JobSearchOut>
    fun findByUserIdAndIsSubscribed(userId: String, isSubscribed: Boolean): List<JobSearchOut>
    fun findByUserIdAndIsApprovedAndIsSubscribed(userId: String, isApproved: Boolean, isSubscribed: Boolean): List<JobSearchOut>
    fun findByIdAndUserId(id: String, userId: String): JobSearchOut?
    fun deleteById(id: String)
    fun deleteByIdAndUserId(id: String, userId: String): Long
    fun countByUserId(userId: String): Long
    fun existsById(id: String): Boolean
    fun updateIsSubscribedByUserId(userId: String, isSubscribed: Boolean): Long
    fun updateIsSubscribedById(id: String, isSubscribed: Boolean): Long
}

@Repository
class JobSearchRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : JobSearchRepository {

    override fun save(jobSearch: JobSearchOut): JobSearchOut {
        return mongoTemplate.save(jobSearch)
    }

    override fun saveAll(jobSearches: List<JobSearchOut>): List<JobSearchOut> {
        return mongoTemplate.insertAll(jobSearches).toList()
    }

    override fun findById(id: String): JobSearchOut? {
        return mongoTemplate.findById(id, JobSearchOut::class.java)
    }

    override fun findAll(): List<JobSearchOut> {
        return mongoTemplate.findAll(JobSearchOut::class.java)
    }

    override fun findByUserId(userId: String): List<JobSearchOut> {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.find(query, JobSearchOut::class.java)
    }

    override fun findByUserIdAndIsApproved(userId: String, isApproved: Boolean): List<JobSearchOut> {
        val query = Query(Criteria.where("user_id").`is`(userId).and("is_approved").`is`(isApproved))
        return mongoTemplate.find(query, JobSearchOut::class.java)
    }

    override fun findByIdAndUserId(id: String, userId: String): JobSearchOut? {
        val query = Query(Criteria.where("id").`is`(id).and("user_id").`is`(userId))
        return mongoTemplate.findOne(query, JobSearchOut::class.java)
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("id").`is`(id))
        mongoTemplate.remove(query, JobSearchOut::class.java)
    }

    override fun deleteByIdAndUserId(id: String, userId: String): Long {
        val query = Query(Criteria.where("id").`is`(id).and("user_id").`is`(userId))
        val result = mongoTemplate.remove(query, JobSearchOut::class.java)
        return result.deletedCount
    }

    override fun countByUserId(userId: String): Long {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.count(query, JobSearchOut::class.java)
    }

    override fun existsById(id: String): Boolean {
        val query = Query(Criteria.where("id").`is`(id))
        return mongoTemplate.exists(query, JobSearchOut::class.java)
    }

    override fun findByUserIdAndIsSubscribed(userId: String, isSubscribed: Boolean): List<JobSearchOut> {
        val query = Query(Criteria.where("user_id").`is`(userId).and("is_subscribed").`is`(isSubscribed))
        return mongoTemplate.find(query, JobSearchOut::class.java)
    }

    override fun findByUserIdAndIsApprovedAndIsSubscribed(userId: String, isApproved: Boolean, isSubscribed: Boolean): List<JobSearchOut> {
        val query = Query(Criteria.where("user_id").`is`(userId).and("is_approved").`is`(isApproved).and("is_subscribed").`is`(isSubscribed))
        return mongoTemplate.find(query, JobSearchOut::class.java)
    }

    override fun updateIsSubscribedByUserId(userId: String, isSubscribed: Boolean): Long {
        val query = Query(Criteria.where("user_id").`is`(userId))
        val update = org.springframework.data.mongodb.core.query.Update().set("is_subscribed", isSubscribed)
        val result = mongoTemplate.updateMulti(query, update, JobSearchOut::class.java)
        return result.modifiedCount
    }

    override fun updateIsSubscribedById(id: String, isSubscribed: Boolean): Long {
        val query = Query(Criteria.where("id").`is`(id))
        val update = org.springframework.data.mongodb.core.query.Update().set("is_subscribed", isSubscribed)
        val result = mongoTemplate.updateFirst(query, update, JobSearchOut::class.java)
        return result.modifiedCount
    }
}
