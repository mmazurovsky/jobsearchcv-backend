package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.ProcessedJobData
import org.springframework.stereotype.Repository
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.UUID

/**
 * Custom repository interface for bulk operations.
 */
interface ProcessedJobRepository {
    fun bulkSaveOrUpdate(jobs: List<ProcessedJobData>): Int
    fun findExistingJobsByLinks(links: List<String>): List<ProcessedJobData>
    fun bulkDeleteByIds(ids: List<String>): Int
    fun countAll(): Long
    fun findByLinkPattern(pattern: String): List<ProcessedJobData>
    fun findById(id: String): ProcessedJobData?
    fun findByInternalId(internalId: String): ProcessedJobData?
    fun findInternalIdsForJobIds(jobIds: Set<String>): Map<String, String>
}


/**
 * MongoDB template-based repository implementation for ProcessedJobData.
 * Provides bulk operations for efficient database operations.
 */
@Repository
class ProcessedJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : ProcessedJobRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Bulk saves or updates a list of ProcessedJobData.
     * Uses upsert operation - inserts if not exists, updates if exists.
     * Returns a map of job ID to internal ID for all input jobs.
     */
    override fun bulkSaveOrUpdate(jobs: List<ProcessedJobData>): Int {
        if (jobs.isEmpty()) {
            logger.debug("No jobs to save/update")
            return 0
        }

        try {
            val bulkOps = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                ProcessedJobData::class.java
            )

            jobs.forEach { job ->
                val query = Query(Criteria.where("_id").`is`(job.id))
                val update = Update()
                    // Use setOnInsert for internalId to preserve existing values
                    .setOnInsert("internal_id", UUID.randomUUID().toString())
                    // These fields can be updated
                    .set("title", job.title)
                    .set("company", job.company)
                    .set("location", job.location)
                    .set("link", job.link)
                    .set("description", job.description)
                    .set("applicants", job.applicants)
                    .set("techstack", job.techstack)
                    .set("tags", job.tags)
                    .set("salary", job.salary)
                    .set("processed_at", job.processedAt)

                bulkOps.upsert(query, update)
            }

            val result = bulkOps.execute()
            val modifiedCount = result.modifiedCount + result.insertedCount

            logger.debug("Bulk save/update completed: {} jobs modified/inserted", modifiedCount)

            return modifiedCount

        } catch (e: Exception) {
            logger.error("Error during bulk save/update operation for {} jobs", jobs.size, e)
            throw e
        }
    }

    override fun findInternalIdsForJobIds(jobIds: Set<String>): Map<String, String> {
        val query = Query(Criteria.where("_id").`in`(jobIds))
        val savedJobs = mongoTemplate.find(query, ProcessedJobData::class.java)

        // Create the mapping from saved jobs
        val jobIdToInternalIdMap = savedJobs.associate { savedJob ->
            savedJob.id to savedJob.internalId
        }

        return jobIdToInternalIdMap
    }

    /**
     * Finds jobs that already exist by their links.
     * Useful for checking duplicates before bulk operations.
     */
    override fun findExistingJobsByLinks(links: List<String>): List<ProcessedJobData> {
        if (links.isEmpty()) {
            return emptyList()
        }

        val query = Query(Criteria.where("link").`in`(links))
        return mongoTemplate.find(query, ProcessedJobData::class.java)
    }

    /**
     * Deletes jobs by their IDs in bulk.
     */
    override fun bulkDeleteByIds(ids: List<String>): Int {
        if (ids.isEmpty()) {
            logger.debug("No jobs to delete")
            return 0
        }

        try {
            val query = Query(Criteria.where("_id").`in`(ids))
            val result = mongoTemplate.remove(query, ProcessedJobData::class.java)

            logger.info("Bulk delete completed: {} jobs deleted", result.deletedCount)
            return result.deletedCount.toInt()

        } catch (e: Exception) {
            logger.error("Error during bulk delete operation for {} job IDs", ids.size, e)
            throw e
        }
    }

    /**
     * Counts total number of processed jobs.
     */
    override fun countAll(): Long {
        return mongoTemplate.count(Query(), ProcessedJobData::class.java)
    }

    /**
     * Finds jobs by link patterns (useful for finding jobs from specific sites).
     */
    override fun findByLinkPattern(pattern: String): List<ProcessedJobData> {
        val query = Query(Criteria.where("link").regex(pattern, "i"))
        return mongoTemplate.find(query, ProcessedJobData::class.java)
    }

    /**
     * Finds a single job by its ID.
     */
    override fun findById(id: String): ProcessedJobData? {
        val query = Query(Criteria.where("_id").`is`(id))
        return mongoTemplate.findOne(query, ProcessedJobData::class.java)
    }

    /**
     * Finds a single job by its internal ID.
     */
    override fun findByInternalId(internalId: String): ProcessedJobData? {
        val query = Query(Criteria.where("internal_id").`is`(internalId))
        return mongoTemplate.findOne(query, ProcessedJobData::class.java)
    }
}
