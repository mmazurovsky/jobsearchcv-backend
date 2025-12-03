package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.JobSearchPrompt
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

interface JobSearchPromptRepository {
    fun save(prompt: JobSearchPrompt): JobSearchPrompt
    fun findById(id: String): JobSearchPrompt?
    fun findByUserId(userId: String): List<JobSearchPrompt>
    fun deleteById(id: String)
    fun countByUserId(userId: String): Long
}

@Repository
class JobSearchPromptRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : JobSearchPromptRepository {

    override fun save(prompt: JobSearchPrompt): JobSearchPrompt {
        return mongoTemplate.save(prompt)
    }

    override fun findById(id: String): JobSearchPrompt? {
        return mongoTemplate.findById(id, JobSearchPrompt::class.java)
    }

    override fun findByUserId(userId: String): List<JobSearchPrompt> {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.find(query, JobSearchPrompt::class.java)
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("id").`is`(id))
        mongoTemplate.remove(query, JobSearchPrompt::class.java)
    }

    override fun countByUserId(userId: String): Long {
        val query = Query(Criteria.where("user_id").`is`(userId))
        return mongoTemplate.count(query, JobSearchPrompt::class.java)
    }
}
