package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.service.SimpleCVService
import com.jobsearchcv.backend.service.CVProcessingService
import com.jobsearchcv.backend.service.CVListItem
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.domain.model.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.security.core.Authentication
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.core.io.ByteArrayResource

@RestController
@RequestMapping("/api/cv")
@Tag(name = "CV Management", description = "Upload, process, and manage CVs")
@SecurityRequirement(name = "bearerAuth")
class CVController(
    private val cvService: SimpleCVService,
    private val cvProcessingService: CVProcessingService,
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

    @PostMapping("/uploadAndCreateSearches")
    @Operation(
        summary = "Upload CV and create job searches",
        description = "Uploads a CV file, saves it to S3, analyzes it with AI, and creates recommended job searches"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "CV uploaded and searches created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid file or request"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun uploadCVAndCreateSearches(
        @RequestParam("file") @Parameter(description = "CV file (PDF, DOC, or DOCX)") file: MultipartFile,
        @Parameter(hidden = true) authentication: Authentication
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

    @GetMapping("/user")
    @Operation(
        summary = "Get user CVs",
        description = "Retrieves all CVs for the authenticated user"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "CVs retrieved successfully"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun getUserCVs(
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<List<CVListResponse>> = runBlocking {
        try {
            // Extract user ID from authentication
            val userId = authentication.principal as String

            logger.info("Getting CVs for user: $userId")

            val cvs = cvService.getCVsListByUserId(userId)

            val cvResponses = cvs.map { cv ->
                CVListResponse(
                    cvId = cv.id,
                    originalFilename = cv.originalFilename,
                    uploadedAt = cv.uploadedAt.toString()
                )
            }

            return@runBlocking ResponseEntity.ok(cvResponses)

        } catch (e: Exception) {
            logger.error("Failed to get CVs for user", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{cvId}")
    @Operation(
        summary = "Delete CV",
        description = "Deletes a CV by ID. Only the owner can delete their CVs."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "CV deleted successfully"),
        ApiResponse(responseCode = "404", description = "CV not found"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun deleteCV(
        @Parameter(description = "CV ID") @PathVariable cvId: String,
        @Parameter(hidden = true) authentication: Authentication
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


    @GetMapping("/{cvId}/download")
    @Operation(
        summary = "Download CV file securely",
        description = "Downloads a CV file with authentication. User can only download their own CVs."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "CV file downloaded successfully"),
        ApiResponse(responseCode = "403", description = "Not authorized to access this CV"),
        ApiResponse(responseCode = "404", description = "CV not found"),
        ApiResponse(responseCode = "500", description = "Server error")
    )
    fun downloadCV(
        @Parameter(description = "CV ID", example = "cv-123e4567-e89b-12d3-a456-426614174000")
        @PathVariable cvId: String,
        authentication: Authentication
    ): ResponseEntity<ByteArrayResource> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Secure CV download request: cvId=$cvId, userId=$userId")

            // Find the CV and verify ownership
            val userCVs = cvService.getCVsByUserId(userId)
            val requestedCV = userCVs.find { it.id == cvId }
                ?: return@runBlocking ResponseEntity.notFound().build()

            // Check if CV has storage path (new CVs) vs direct URL (legacy CVs)
            if (requestedCV.storagePath != null) {
                // New secure approach: download from Firebase Storage
                val fileContent = cvService.downloadCVContent(requestedCV.storagePath)
                val contentType = getContentTypeFromExtension(requestedCV.contentType)
                val filename = extractFilenameFromPath(requestedCV.storagePath)
                
                val resource = ByteArrayResource(fileContent)
                
                logger.info("Successfully serving CV: cvId=$cvId, size=${fileContent.size}")
                
                return@runBlocking ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                    .body(resource)
            } else {
                // Legacy approach: redirect to existing URL (less secure)
                logger.warn("Legacy CV download for cvId=$cvId - redirecting to URL")
                return@runBlocking ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, requestedCV.linkToCv)
                    .build()
            }

        } catch (e: Exception) {
            logger.error("Failed to download CV: cvId=$cvId", e)
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

    private fun getContentTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    private fun extractFilenameFromPath(storagePath: String): String {
        // Extract original filename from path like: users/userId/cvs/timestamp-uuid-filename.pdf
        val pathParts = storagePath.split("/")
        val filename = pathParts.lastOrNull() ?: "cv.pdf"
        // Remove timestamp and uuid prefix (format: timestamp-uuid-originalname.ext)
        val parts = filename.split("-", limit = 3)
        return if (parts.size >= 3) parts[2] else filename
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

// Data Transfer Objects
@Schema(description = "Response for CV upload operation")
data class CVUploadResponse(
    @Schema(description = "Unique identifier for the uploaded CV", example = "cv-123e4567-e89b-12d3-a456-426614174000", required = true)
    val cvId: String,
    @Schema(description = "Direct link to the CV file", example = "https://s3.amazonaws.com/bucket/cv-123.pdf", required = true)
    val linkToCv: String
)

@Schema(description = "CV information response for listing")
data class CVListResponse(
    @Schema(description = "Unique identifier for the CV", example = "cv-123e4567-e89b-12d3-a456-426614174000", required = true)
    val cvId: String,
    @Schema(description = "Original filename when uploaded", example = "resume.pdf", required = true)
    val originalFilename: String,
    @Schema(description = "When the CV was uploaded", example = "2024-01-15T10:30:00Z", required = true)
    val uploadedAt: String
)