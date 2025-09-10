package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.service.JobSearchCreationException
import com.jobsearchcv.backend.service.JobSearchCreationService
import com.jobsearchcv.backend.service.JobSearchService
import com.jobsearchcv.backend.service.SubscriptionAwareSchedulingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/job-searches")
@Tag(name = "Job Searches", description = "Manage job searches and alerts")
@SecurityRequirement(name = "bearerAuth")
class JobSearchController(
    private val jobSearchCreationService: JobSearchCreationService,
    private val jobSearchRepository: JobSearchRepository,
    private val subscriptionAwareSchedulingService: SubscriptionAwareSchedulingService,
    private val jobSearchService: JobSearchService
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchController::class.java)
    }

    @PostMapping
    @Operation(
        summary = "Create job searches",
        description = "Creates new job searches for the authenticated user with specified approval status"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Job searches created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request or job search creation failed"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun createJobSearches(
        @RequestBody request: CreateJobSearchesRequest,
        @RequestParam("isApproved") @Parameter(description = "Whether the job searches should be immediately approved") isApproved: Boolean,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<CreateJobSearchesResponse> = runBlocking {
        try {
            val userId = authentication.principal as String

            logger.info("Creating job searches request for user: $userId, count=${request.jobSearches.size}, isApproved=$isApproved")
            
            // Log incoming job searches for debugging
            request.jobSearches.forEach { jobSearch ->
                logger.debug("Incoming job search: id=${jobSearch.id}, timePeriod=${jobSearch.timePeriod}, jobTypes=${jobSearch.jobTypes}, remoteTypes=${jobSearch.remoteTypes}")
            }

            // Delegate to service with isApproved parameter
            val result = jobSearchCreationService.createJobSearches(
                jobSearches = request.jobSearches,
                userId = userId,
                isApproved = isApproved
            )

            return@runBlocking ResponseEntity.ok(
                CreateJobSearchesResponse(
                    message = result.message,
                    jobSearchIds = result.jobSearchIds,
                    destinationId = result.destinationId
                )
            )

        } catch (e: JobSearchCreationException) {
            logger.error("Job search creation failed: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                    CreateJobSearchesResponse(
                        e.message ?: "Job search creation failed",
                        emptyList(),
                        null
                    )
                )
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid request: ${e.message}")
            return@runBlocking ResponseEntity.badRequest()
                .body(CreateJobSearchesResponse(e.message ?: "Invalid request", emptyList(), null))
        } catch (e: Exception) {
            logger.error("Unexpected error creating job searches: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CreateJobSearchesResponse("Internal server error", emptyList(), null))
        }
    }

    @GetMapping
    @Operation(
        summary = "Get user job searches",
        description = "Retrieves job searches for the authenticated user filtered by approval status"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Job searches retrieved successfully"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun getUserSearches(
        @RequestParam("isApproved") @Parameter(description = "Filter by approval status") isApproved: Boolean,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<List<JobSearchOut>> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Getting job searches for user: $userId, isApproved=$isApproved")
            
            val searches = jobSearchRepository.findByUserIdAndIsApproved(userId, isApproved)
            
            logger.info("Found ${searches.size} searches for user: $userId with isApproved=$isApproved")
            return@runBlocking ResponseEntity.ok(searches)
            
        } catch (e: Exception) {
            logger.error("Failed to get searches for user: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{searchId}")
    @Operation(
        summary = "Delete a job search",
        description = "Deletes a job search by ID. Only the owner can delete their job searches."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Job search deleted successfully"),
        ApiResponse(responseCode = "404", description = "Job search not found"),
        ApiResponse(responseCode = "403", description = "Forbidden - not the owner"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun deleteJobSearch(
        @Parameter(description = "Job search ID") @PathVariable searchId: String,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<Void> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Deleting job search: id=$searchId, userId=$userId")
            
            // Check if job search exists and belongs to user
            val jobSearch = jobSearchRepository.findById(searchId)
            
            if (jobSearch == null) {
                logger.warn("Job search not found: id=$searchId")
                return@runBlocking ResponseEntity.notFound().build()
            }
            
            if (jobSearch.userId != userId) {
                logger.warn("User $userId attempted to delete job search belonging to another user")
                return@runBlocking ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            
            // Delete job search
            jobSearchRepository.deleteById(searchId)
            
            logger.info("Successfully deleted job search: id=$searchId")
            return@runBlocking ResponseEntity.noContent().build()
            
        } catch (e: Exception) {
            logger.error("Failed to delete job search: id=$searchId", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/debug/enum-test")
    @Operation(
        summary = "Test enum serialization",
        description = "Debug endpoint to test enum serialization and deserialization"
    )
    @ApiResponse(responseCode = "200", description = "Enum test successful")
    fun testEnumSerialization(
        @RequestBody request: JobSearchIn,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Debug enum test - received request")
        
        val response = mutableMapOf<String, Any>()
        
        // Log and add to response what was received
        response["receivedData"] = mapOf(
            "id" to request.id,
            "jobTitle" to request.jobTitle,
            "location" to request.location,
            "timePeriod" to mapOf(
                "value" to request.timePeriod.name,
                "displayName" to request.timePeriod.displayName,
                "seconds" to request.timePeriod.seconds,
                "cronExpression" to request.timePeriod.cronExpression
            ),
            "jobTypes" to request.jobTypes.map { mapOf("value" to it.name, "label" to it.label) },
            "remoteTypes" to request.remoteTypes.map { mapOf("value" to it.name, "label" to it.label) },
            "filterText" to (request.filterText ?: "null")
        )
        
        // Create a JobSearchOut and verify conversion
        val jobSearchOut = JobSearchOut.fromJobSearchIn(request, authentication.principal as String, isApproved = true)
        
        response["convertedData"] = mapOf(
            "id" to jobSearchOut.id,
            "timePeriod" to mapOf(
                "value" to jobSearchOut.timePeriod.name,
                "displayName" to jobSearchOut.timePeriod.displayName
            ),
            "jobTypes" to jobSearchOut.jobTypes.map { it.name },
            "remoteTypes" to jobSearchOut.remoteTypes.map { it.name }
        )
        
        logger.info("Debug enum test response: $response")
        
        return ResponseEntity.ok(response)
    }

    @PutMapping("/{searchId}")
    @Operation(
        summary = "Update a job search",
        description = "Updates an existing job search. Only the owner can update their job searches."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Job search updated successfully"),
        ApiResponse(responseCode = "404", description = "Job search not found"),
        ApiResponse(responseCode = "403", description = "Forbidden - not the owner"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun updateJobSearch(
        @Parameter(description = "Job search ID") @PathVariable searchId: String,
        @RequestBody request: UpdateJobSearchRequest,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<JobSearchOut> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Updating job search: id=$searchId, userId=$userId")
            
            // Check if job search exists and belongs to user
            val existingJobSearch = jobSearchRepository.findById(searchId)
            
            if (existingJobSearch == null) {
                logger.warn("Job search not found: id=$searchId")
                return@runBlocking ResponseEntity.notFound().build()
            }
            
            if (existingJobSearch.userId != userId) {
                logger.warn("User $userId attempted to update job search belonging to another user")
                return@runBlocking ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            
            // Update job search
            val updatedJobSearch = existingJobSearch.copy(
                jobTitle = request.jobTitle ?: existingJobSearch.jobTitle,
                location = request.location ?: existingJobSearch.location,
                jobTypes = request.jobTypes ?: existingJobSearch.jobTypes,
                remoteTypes = request.remoteTypes ?: existingJobSearch.remoteTypes,
                timePeriod = request.timePeriod ?: existingJobSearch.timePeriod,
                filterText = request.filterText,
                isApproved = request.isApproved ?: existingJobSearch.isApproved,
                isSubscribed = request.isSubscribed ?: existingJobSearch.isSubscribed
            )
            
            val savedJobSearch = jobSearchRepository.save(updatedJobSearch)
            
            // Update scheduler if job search approval status or other parameters changed
            try {
                subscriptionAwareSchedulingService.updateJobSearch(savedJobSearch)
                logger.info("Successfully updated job search and scheduler: id=$searchId")
            } catch (e: Exception) {
                logger.error("Failed to update scheduler for job search: id=$searchId", e)
                // Don't fail the request if scheduler update fails, just log the error
            }
            
            logger.info("Successfully updated job search: id=$searchId")
            return@runBlocking ResponseEntity.ok(savedJobSearch)
            
        } catch (e: Exception) {
            logger.error("Failed to update job search: id=$searchId", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PutMapping("/unsubscribeFromEmails/all")
    @Operation(
        summary = "Unsubscribe from all job searches",
        description = "Sets isSubscribed=false for all user's job searches and removes them from scheduler"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully unsubscribed from all job searches"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun unsubscribeFromAllSearches(
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        try {
            val userId = authentication.principal as String
            val result = jobSearchService.unsubscribeFromAllSearches(userId)
            
            val response = mapOf(
                "success" to result.success,
                "message" to result.message,
                "affectedCount" to result.affectedCount
            )
            
            return@runBlocking ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe user from all searches", e)
            val response = mapOf(
                "success" to false,
                "message" to "Internal server error"
            )
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PutMapping("/changeEmailSubscriptions")
    @Operation(
        summary = "Change email notifications for multiple job searches",
        description = "Updates isSubscribed status for multiple job searches and manages scheduler accordingly"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Email notifications updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request or some job searches don't belong to user"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun changeEmailNotifications(
        @RequestBody request: ChangeEmailNotificationsRequest,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<ChangeEmailNotificationsResponse> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Changing email notifications for user: $userId, count=${request.jobSearches.size}")
            
            // Convert request to pairs
            val changes = request.jobSearches.map { it.id to it.isSubscribed }
            
            // Delegate to service
            val result = jobSearchService.changeEmailNotifications(userId, changes)
            
            val response = ChangeEmailNotificationsResponse(
                message = result.message,
                success = result.success,
                successCount = result.successCount,
                failureCount = result.failureCount,
                failures = result.failures
            )
            
            val status = if (result.success) {
                HttpStatus.OK
            } else if (result.message.contains("do not belong to the user")) {
                HttpStatus.BAD_REQUEST
            } else {
                HttpStatus.BAD_REQUEST
            }
            
            return@runBlocking ResponseEntity.status(status).body(response)
        } catch (e: Exception) {
            logger.error("Failed to change email notifications for user", e)
            val response = ChangeEmailNotificationsResponse(
                message = "Internal server error",
                success = false,
                successCount = 0,
                failureCount = 0
            )
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }
}

// Request DTOs
@Schema(description = "Request to change email notifications for multiple job searches")
data class ChangeEmailNotificationsRequest(
    @Schema(description = "List of job search notification changes", required = true)
    val jobSearches: List<JobSearchNotificationChange>
)

@Schema(description = "Job search notification change")
data class JobSearchNotificationChange(
    @Schema(description = "Job search ID", example = "search-123", required = true)
    val id: String,
    @Schema(description = "Whether to subscribe to email notifications", example = "true", required = true)
    val isSubscribed: Boolean
)

@Schema(description = "Response for email notification changes")
data class ChangeEmailNotificationsResponse(
    @Schema(description = "Operation result message", required = true)
    val message: String,
    @Schema(description = "Whether all operations were successful", required = true)
    val success: Boolean,
    @Schema(description = "Number of job searches successfully updated", required = true)
    val successCount: Int,
    @Schema(description = "Number of job searches that failed to update", required = true)
    val failureCount: Int,
    @Schema(description = "Details of any failures", required = false)
    val failures: List<String>? = null
)

@Schema(description = "Request to update a job search")
data class UpdateJobSearchRequest(
    @Schema(description = "Job title to search for", example = "Software Engineer", required = false)
    val jobTitle: String? = null,
    @Schema(description = "Location for job search", example = "New York, NY", required = false)
    val location: String? = null,
    @Schema(description = "Types of job positions", required = false)
    val jobTypes: List<JobType>? = null,
    @Schema(description = "Remote work preferences", required = false)
    val remoteTypes: List<RemoteType>? = null,
    @Schema(description = "Search frequency/time period", required = false)
    val timePeriod: TimePeriod? = null,
    @Schema(description = "Additional filter text for job descriptions", example = "Spring Boot", required = false)
    val filterText: String? = null,
    @Schema(description = "Whether the job search is approved for scheduling", required = false)
    val isApproved: Boolean? = null,
    @Schema(description = "Whether user is subscribed to receive notifications for this job search", required = false)
    val isSubscribed: Boolean? = null
)