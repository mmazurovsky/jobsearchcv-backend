package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.service.SimpleCVService
import com.jobsearchcv.backend.service.CVProcessingService
import com.jobsearchcv.backend.service.UserAuthService
import com.jobsearchcv.backend.service.JobSearchCreationService
import com.jobsearchcv.backend.service.JobSearchCreationException
import com.jobsearchcv.backend.service.SupabaseUser
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.security.core.Authentication
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/cv")
class CVController(
    private val cvService: SimpleCVService,
    private val cvProcessingService: CVProcessingService,
    private val userAuthService: UserAuthService,
    private val jobSearchCreationService: JobSearchCreationService,
    private val jobSearchRepository: JobSearchRepository
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CVController::class.java)
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB
        private val ALLOWED_CONTENT_TYPES = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        private val ALLOWED_EXTENSIONS = setOf("pdf", "doc", "docx")
    }

    // 1. Upload CV, save to S3, and create job searches using AI analysis
    @PostMapping("/uploadAndCreateSearches")
    fun uploadAndCreateSearches(
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication
    ): ResponseEntity<UploadAndCreateSearchesResponse> = runBlocking {
        try {
            // Extract user ID from authentication
            val userId = authentication.principal as String

            logger.info(
                "CV upload and search creation request from user: $userId, file: ${file.originalFilename}"
            )

            // Validate file
            val fileExtension = validateUploadedFile(file)

            // Start both coroutines in parallel
            val s3UploadDeferred = async {
                performS3Upload(file, userId, fileExtension)
            }

            val cvProcessingDeferred = async {
                performCVProcessing(file, userId)
            }

            // Wait for both coroutines to complete
            val s3Result = s3UploadDeferred.await()
            val processingResult = cvProcessingDeferred.await()

            // Check if both operations succeeded
            if (!s3Result.success) {
                logger.error("S3 upload failed: ${s3Result.errorMessage}")
                return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to upload CV: ${s3Result.errorMessage}"))
            }

            if (!processingResult.success) {
                logger.error("CV processing failed: ${processingResult.errorMessage}")
                return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to process CV: ${processingResult.errorMessage}"))
            }

            // Save the recommended searches with isApproved=false
            val recommendedSearches = processingResult.analysisResult!!.recommendedSearches
            val savedSearches = if (recommendedSearches.isNotEmpty()) {
                val jobSearchOuts = recommendedSearches.map { searchIn ->
                    JobSearchOut.fromJobSearchIn(searchIn, userId, isApproved = false)
                }
                jobSearchRepository.saveAll(jobSearchOuts)
            } else {
                emptyList()
            }

            logger.info("Successfully uploaded CV and created searches: userId=$userId, cvId=${s3Result.cvId}, savedSearches=${savedSearches.size}")

            // Convert saved searches back to JobSearchIn format for the response
            val recommendedSearchesWithIds = savedSearches.map { saved ->
                JobSearchIn(
                    id = saved.id,
                    jobTitle = saved.jobTitle,
                    location = saved.location,
                    jobTypes = saved.jobTypes,
                    remoteTypes = saved.remoteTypes,
                    timePeriod = saved.timePeriod,
                    filterText = saved.filterText
                )
            }

            return@runBlocking ResponseEntity.ok(
                UploadAndCreateSearchesResponse(
                    cvId = s3Result.cvId,
                    linkToCv = s3Result.linkToCv,
                    recommendedSearches = recommendedSearchesWithIds,
                    analysisResult = CVAnalysisResult(
                        currentOrDesiredPosition = processingResult.analysisResult.currentOrDesiredPosition,
                        previousPositions = processingResult.analysisResult.previousPositions,
                        skillsWithWeights = processingResult.analysisResult.skillsWithWeights,
                        education = processingResult.analysisResult.education,
                        location = processingResult.analysisResult.location,
                        recommendedSearches = recommendedSearchesWithIds
                    )
                )
            )

        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid CV upload request: ${e.message}")
            return@runBlocking ResponseEntity.badRequest()
                .body(createErrorResponse("Invalid request: ${e.message}"))
        } catch (e: Exception) {
            logger.error("Failed to upload CV and create searches: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error: ${e.message}"))
        }
    }

    // 2. Get all CVs for a user (CV IDs and links)
    @GetMapping("/user")
    fun getUserCVs(
        authentication: Authentication
    ): ResponseEntity<List<CVResponse>> = runBlocking {
        try {
            // Extract user ID from authentication
            val userId = authentication.principal as String

            logger.info("Getting CVs for user: $userId")

            val cvs = cvService.getCVsByUserId(userId)

            val cvResponses = cvs.map { cv ->
                CVResponse(
                    cvId = cv.id,
                    linkToCv = cv.linkToCv
                )
            }

            return@runBlocking ResponseEntity.ok(cvResponses)

        } catch (e: Exception) {
            logger.error("Failed to get CVs for user", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    // 3. Delete CV by CV ID
    @DeleteMapping("/{cvId}")
    fun deleteCV(
        @PathVariable cvId: String,
        authentication: Authentication
    ): ResponseEntity<Void> = runBlocking {
        try {
            // Extract user ID from authentication
            val userId = authentication.principal as String

            logger.info(
                "Delete CV request: cvId=$cvId, userId=$userId"
            )

            val deleted = cvService.deleteCV(userId, cvId)

            return@runBlocking if (deleted) {
                logger.info("Successfully deleted CV: cvId=$cvId, userId=$userId")
                ResponseEntity.noContent().build()
            } else {
                logger.warn("CV not found for deletion: cvId=$cvId, userId=$userId")
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("Failed to delete CV: cvId=$cvId", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    // 4. Create job searches with authentication
    @PostMapping("/createJobSearches")
    fun createJobSearches(
        @RequestBody request: CreateJobSearchesRequest,
        @RequestParam("isApproved") isApproved: Boolean,
        authentication: Authentication
    ): ResponseEntity<CreateJobSearchesResponse> = runBlocking {
        try {
            val userId = authentication.principal as String

            logger.info("Creating job searches request for user: $userId, count=${request.jobSearches.size}, isApproved=$isApproved")

            // Delegate to service with isApproved parameter
            val result = jobSearchCreationService.createJobSearches(
                jobSearches = request.jobSearches,
                userId = userId,
                isApproved = isApproved
            )

            val immediateSearchSummaries = result.immediateSearchTriggerResults.map {
                ImmediateSearchSummary(
                    originalJobSearchId = it.originalJobSearchId,
                    immediateSearchId = it.immediateSearchId,
                    success = it.success,
                    errorMessage = it.errorMessage
                )
            }

            return@runBlocking ResponseEntity.ok(
                CreateJobSearchesResponse(
                    message = result.message,
                    jobSearchIds = result.jobSearchIds,
                    destinationId = result.destinationId,
                    immediateSearchResults = immediateSearchSummaries
                )
            )

        } catch (e: JobSearchCreationException) {
            logger.error("Job search creation failed: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                    CreateJobSearchesResponse(
                        e.message ?: "Job search creation failed",
                        emptyList(),
                        ""
                    )
                )
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid request: ${e.message}")
            return@runBlocking ResponseEntity.badRequest()
                .body(CreateJobSearchesResponse(e.message ?: "Invalid request", emptyList(), ""))
        } catch (e: Exception) {
            logger.error("Unexpected error creating job searches: ${e.message}", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CreateJobSearchesResponse("Internal server error", emptyList(), ""))
        }
    }

    // 5. Get job searches for a user by approval status
    @GetMapping("/searches")
    fun getUserSearches(
        @RequestParam("isApproved") isApproved: Boolean,
        authentication: Authentication
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

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "healthy",
                "service" to "cv-api",
                "timestamp" to System.currentTimeMillis().toString()
            )
        )
    }

    private fun validateUploadedFile(file: MultipartFile): String {
        // Check file size
        if (file.size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("File size exceeds maximum allowed size of 10MB")
        }

        // Check if file is empty
        if (file.isEmpty) {
            throw IllegalArgumentException("Uploaded file is empty")
        }

        // Check file extension
        val fileName =
            file.originalFilename ?: throw IllegalArgumentException("File name is missing")
        val extension = getFileExtension(fileName).lowercase()
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw IllegalArgumentException(
                "Unsupported file type. Allowed types: ${
                    ALLOWED_EXTENSIONS.joinToString(
                        ", "
                    )
                }"
            )
        }
        return extension

        // Check content type
        val contentType = file.contentType
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            logger.warn("Potentially incorrect content type: $contentType for file: $fileName")
            // Don't throw for content type mismatch as browsers can send incorrect MIME types
        }
    }

    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex != -1 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1)
        } else {
            ""
        }
    }

    /**
     * Coroutine to handle S3 upload
     */
    private suspend fun performS3Upload(file: MultipartFile, userId: String, fileExtension: String): S3UploadResult {
        return try {
            logger.info("Starting S3 upload coroutine for user: $userId")
            val savedCV = cvService.uploadAndSaveCV(file, userId, fileExtension)
            logger.info("S3 upload completed successfully: cvId=${savedCV.id}")

            S3UploadResult(
                success = true,
                cvId = savedCV.id,
                linkToCv = savedCV.linkToCv
            )
        } catch (e: Exception) {
            logger.error("S3 upload failed: ${e.message}", e)
            S3UploadResult(
                success = false,
                cvId = "",
                linkToCv = "",
                errorMessage = e.message ?: "Unknown S3 upload error"
            )
        }
    }

    /**
     * Coroutine to handle CV processing and analysis
     */
    private suspend fun performCVProcessing(
        file: MultipartFile,
        userId: String
    ): CVProcessingResult {
        return try {
            logger.info("Starting CV processing coroutine for user: $userId")

            // Extract text from CV
            val extractedText = cvProcessingService.extractTextFromCV(file)
            logger.info("CV text extraction completed: ${extractedText.length} characters")

            // Process with AI
            val processingRequest = CVProcessingRequest(
                extractedText = extractedText,
                userId = userId,
                fileName = file.originalFilename ?: "unknown"
            )

            val result = cvProcessingService.processCVWithAI(processingRequest)
            logger.info("CV processing completed: success=${result.success}")

            result
        } catch (e: Exception) {
            logger.error("CV processing failed: ${e.message}", e)
            CVProcessingResult(
                success = false,
                analysisResult = null,
                errorMessage = e.message ?: "Unknown CV processing error"
            )
        }
    }

    /**
     * Create error response
     */
    private fun createErrorResponse(message: String): UploadAndCreateSearchesResponse {
        return UploadAndCreateSearchesResponse(
            cvId = "",
            linkToCv = "",
            recommendedSearches = emptyList(),
            analysisResult = CVAnalysisResult(
                currentOrDesiredPosition = null,
                previousPositions = emptyList(),
                skillsWithWeights = emptyList(),
                education = emptyList(),
                location = null,
                recommendedSearches = emptyList()
            )
        )
    }
}

// Simplified Data Transfer Objects
data class CVUploadResponse(
    val cvId: String,
    val linkToCv: String
)

data class CVResponse(
    val cvId: String,
    val linkToCv: String
)