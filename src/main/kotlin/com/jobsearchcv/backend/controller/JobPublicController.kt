package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.PublicJobResponse
import com.jobsearchcv.backend.repository.ProcessedJobRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public controller for job viewing
 * No authentication required - allows users to view jobs from X.com links
 */
@RestController
@RequestMapping("/api/jobs")
class JobPublicController(
    private val processedJobRepository: ProcessedJobRepository
) {
    private val logger = LoggerFactory.getLogger(JobPublicController::class.java)

    /**
     * Get job details by internal ID
     * Public endpoint - no authentication required
     *
     * @param internalId Internal UUID of the job
     * @return Job details or 404 if not found
     */
    @GetMapping("/{internalId}")
    fun getJobByInternalId(@PathVariable internalId: String): ResponseEntity<PublicJobResponse> {
        logger.debug("Fetching job with internalId: $internalId")

        val job = processedJobRepository.findByInternalId(internalId)

        return if (job != null) {
            val response = PublicJobResponse(
                internalId = job.internalId,
                title = job.title,
                company = job.company,
                location = job.location,
                link = job.link,
                techstack = job.techstack,
                tags = job.tags,
                salary = job.salary,
                processedAt = job.processedAt
            )
            logger.info("Successfully fetched job: ${job.title} at ${job.company}")
            ResponseEntity.ok(response)
        } else {
            logger.warn("Job not found with internalId: $internalId")
            ResponseEntity.notFound().build()
        }
    }
}
