package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.IncomingJobsProcessingService
import jakarta.validation.Valid
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.Executor

@RestController
@RequestMapping("/api")
class JobDataApiController(
    private val incomingJobsProcessingService: IncomingJobsProcessingService,
    @Qualifier("jobProcessingExecutor") private val jobProcessingExecutor: Executor
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobDataApiController::class.java)
    }

    @PostMapping("/job-data-callback", consumes = ["application/json"], produces = ["application/json"])
    fun receiveJobData(@Valid @RequestBody request: JobDataCallbackRequest): ResponseEntity<JobDataCallbackResponse> {
        logger.info(
            "Received job data for jobSearchId={}, userId={}, jobCount={}",
            request.jobSearchId,
            request.userId,
            request.jobs.size
        )

        // Process jobs asynchronously using proper coroutine scope with executor
        val executorScope = CoroutineScope(jobProcessingExecutor.asCoroutineDispatcher() + SupervisorJob())
        executorScope.launch {
            try {
                logger.info("Starting async job data processing for jobSearchId={}", request.jobSearchId)
                incomingJobsProcessingService.processIncomingJobData(
                    jobSearchId = request.jobSearchId,
                    scrapedJobs = request.jobs,
                    userId = request.userId
                )
                logger.info("Completed async job data processing for jobSearchId={}", request.jobSearchId)
            } catch (e: Exception) {
                logger.error(
                    "Error in async job data processing for jobSearchId={}, userId={}",
                    request.jobSearchId,
                    request.userId,
                    e
                )
            }
        }

        return ResponseEntity.ok(
            JobDataCallbackResponse(
                status = "received",
                message = "Job data processing started",
                receivedCount = request.jobs.size
            )
        )
    }
} 