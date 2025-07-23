package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.service.SimpleCVService
import com.jobsearchcv.backend.service.CVProcessingService
import com.jobsearchcv.backend.service.UserAuthService
import com.jobsearchcv.backend.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/cv")
class CVController(
    private val cvService: SimpleCVService,
    private val cvProcessingService: CVProcessingService,
    private val userAuthService: UserAuthService
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
    suspend fun uploadAndCreateSearches(
        @RequestParam("file") file: MultipartFile,
        request: HttpServletRequest
    ): ResponseEntity<UploadAndCreateSearchesResponse> = coroutineScope {
        try {
            // Extract user ID from request (bearer token or generate temporary ID)
            val userId = userAuthService.extractUserIdFromRequest(request)
            
            logger.info("CV upload and search creation request from user: $userId (${if (userAuthService.isTemporaryUser(userId)) "guest" else "authenticated"}), file: ${file.originalFilename}")
            
            // Validate file
            validateUploadedFile(file)
            
            // Start both coroutines in parallel
            val s3UploadDeferred = async {
                performS3Upload(file, userId)
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
                return@coroutineScope ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to upload CV: ${s3Result.errorMessage}"))
            }
            
            if (!processingResult.success) {
                logger.error("CV processing failed: ${processingResult.errorMessage}")
                return@coroutineScope ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to process CV: ${processingResult.errorMessage}"))
            }
            
            logger.info("Successfully uploaded CV and created searches: userId=$userId, cvId=${s3Result.cvId}")
            
            return@coroutineScope ResponseEntity.ok(
                UploadAndCreateSearchesResponse(
                    cvId = s3Result.cvId,
                    linkToCv = s3Result.linkToCv,
                    recommendedSearches = processingResult.analysisResult!!.recommendedSearches,
                    analysisResult = processingResult.analysisResult!!
                )
            )
            
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid CV upload request: ${e.message}")
            return@coroutineScope ResponseEntity.badRequest()
                .body(createErrorResponse("Invalid request: ${e.message}"))
        } catch (e: Exception) {
            logger.error("Failed to upload CV and create searches: ${e.message}", e)
            return@coroutineScope ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error: ${e.message}"))
        }
    }
    
    // 2. Get all CVs for a user (CV IDs and links)
    @GetMapping("/user")
    suspend fun getUserCVs(
        request: HttpServletRequest
    ): ResponseEntity<List<CVResponse>> {
        try {
            // Extract user ID from request (bearer token or generate temporary ID)
            val userId = userAuthService.extractUserIdFromRequest(request)
            
            logger.info("Getting CVs for user: $userId (${if (userAuthService.isTemporaryUser(userId)) "guest" else "authenticated"})")
            
            val cvs = cvService.getCVsByUserId(userId)
            
            val cvResponses = cvs.map { cv ->
                CVResponse(
                    cvId = cv.id,
                    linkToCv = cv.linkToCv
                )
            }
            
            return ResponseEntity.ok(cvResponses)
            
        } catch (e: Exception) {
            logger.error("Failed to get CVs for user", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
    
    // 3. Delete CV by CV ID
    @DeleteMapping("/{cvId}")
    suspend fun deleteCV(
        @PathVariable cvId: String,
        request: HttpServletRequest
    ): ResponseEntity<Void> {
        try {
            // Extract user ID from request (bearer token or generate temporary ID)
            val userId = userAuthService.extractUserIdFromRequest(request)
            
            logger.info("Delete CV request: cvId=$cvId, userId=$userId (${if (userAuthService.isTemporaryUser(userId)) "guest" else "authenticated"})")
            
            val deleted = cvService.deleteCV(userId, cvId)
            
            return if (deleted) {
                logger.info("Successfully deleted CV: cvId=$cvId, userId=$userId")
                ResponseEntity.noContent().build()
            } else {
                logger.warn("CV not found for deletion: cvId=$cvId, userId=$userId")
                ResponseEntity.notFound().build()
            }
            
        } catch (e: Exception) {
            logger.error("Failed to delete CV: cvId=$cvId", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
    
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "healthy",
            "service" to "cv-api",
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }
    
    private fun validateUploadedFile(file: MultipartFile) {
        // Check file size
        if (file.size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("File size exceeds maximum allowed size of 10MB")
        }
        
        // Check if file is empty
        if (file.isEmpty) {
            throw IllegalArgumentException("Uploaded file is empty")
        }
        
        // Check file extension
        val fileName = file.originalFilename ?: throw IllegalArgumentException("File name is missing")
        val extension = getFileExtension(fileName).lowercase()
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw IllegalArgumentException("Unsupported file type. Allowed types: ${ALLOWED_EXTENSIONS.joinToString(", ")}")
        }
        
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
    private suspend fun performS3Upload(file: MultipartFile, userId: String): S3UploadResult {
        return try {
            logger.info("Starting S3 upload coroutine for user: $userId")
            val savedCV = cvService.uploadAndSaveCV(file, userId)
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
    private suspend fun performCVProcessing(file: MultipartFile, userId: String): CVProcessingResult {
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