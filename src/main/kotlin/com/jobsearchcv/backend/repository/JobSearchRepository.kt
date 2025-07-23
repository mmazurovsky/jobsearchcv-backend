package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.JobSearchOut
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

interface JobSearchRepositoryCustom {
    fun countByUserId(userId: String): Long
}

@Repository
interface JobSearchRepository : MongoRepository<JobSearchOut, String>, JobSearchRepositoryCustom {
    fun findByUserId(userId: String): List<JobSearchOut>
    fun findByIdAndUserId(id: String, userId: String): JobSearchOut?
    fun deleteByIdAndUserId(id: String, userId: String): Long
}

@Repository
class JobSearchRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : JobSearchRepositoryCustom {
    
    override fun countByUserId(userId: String): Long {
        val query = Query(Criteria.where("userId").`is`(userId))
        return mongoTemplate.count(query, JobSearchOut::class.java)
    }
}