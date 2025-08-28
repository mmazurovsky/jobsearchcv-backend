package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.SimpleCVInput
import com.jobsearchcv.backend.domain.model.SimpleUserCV
import com.jobsearchcv.backend.repository.SimpleCVRepository
import com.jobsearchcv.backend.service.client.FirebaseStorageService
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime

data class CVListItem(
    val id: String,
    val originalFilename: String,
    val uploadedAt: OffsetDateTime
)

@Service
class SimpleCVService(
    private val cvRepository: SimpleCVRepository,
    private val firebaseStorageService: FirebaseStorageService,
    @Value("\${firebase.storage.bucket:applyfirst-b9c69.firebasestorage.app}") private val storageBucket: String
) {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SimpleCVService::class.java)
    }
    
    suspend fun uploadAndSaveCV(file: MultipartFile, userId: String, fileExtension: String ): SimpleUserCV {
        logger.info("Uploading CV for user: $userId, filename: ${file.originalFilename}")
        
        try {
            val uploadedAt = OffsetDateTime.now()
            
            // Upload to Firebase Storage
            val storagePath = firebaseStorageService.uploadFile(file, userId, fileExtension)
            
            // Save to MongoDB first to get the CV ID
            val tempCvInput = SimpleCVInput(
                userId = userId,
                linkToCv = "", // Will be updated after we get the ID
                fileSize = file.size,
                contentType = fileExtension,
                originalFilename = file.originalFilename,
                storageBucket = storageBucket,
                storagePath = storagePath,
                uploadedAt = uploadedAt
            )
            
            val savedCV = cvRepository.save(tempCvInput.toSimpleUserCV())
            
            // Generate secure download URL pointing to our authenticated endpoint
            val secureDownloadUrl = "/api/cv/${savedCV.id}/download"
            
            // Update CV with secure download URL
            val updatedCV = savedCV.copy(linkToCv = secureDownloadUrl)
            cvRepository.save(updatedCV)
            
            logger.info("Successfully uploaded and saved CV: id=${updatedCV.id}, userId=$userId, uploadedAt=$uploadedAt")
            
            return updatedCV
            
        } catch (e: Exception) {
            logger.error("Failed to upload and save CV for user: $userId", e)
            throw e
        }
    }
    
    suspend fun getCVsByUserId(userId: String): List<SimpleUserCV> {
        logger.debug("Getting CVs for user: $userId")
        val cvs = cvRepository.findByUserId(userId).toList()
        
        // Return CVs with secure download URLs pointing to our authenticated endpoint
        return cvs.map { cv ->
            // Use secure download endpoint instead of signed URLs
            val secureDownloadUrl = "/api/cv/${cv.id}/download"
            cv.copy(linkToCv = secureDownloadUrl)
        }
    }

    suspend fun getCVsListByUserId(userId: String): List<CVListItem> {
        logger.debug("Getting CV list for user: $userId")
        val cvs = cvRepository.findByUserId(userId).toList()
        
        // Return only minimal data for listing
        return cvs.map { cv ->
            CVListItem(
                id = cv.id,
                originalFilename = buildCompleteFilename(cv),
                uploadedAt = cv.uploadedAt
            )
        }
    }

    private fun buildCompleteFilename(cv: SimpleUserCV): String {
        // Get base filename (without extension)
        val baseFilename = cv.originalFilename 
            ?: extractFilenameFromStoragePath(cv.storagePath)
            ?: "cv"
        
        // Remove existing extension if present
        val filenameWithoutExtension = baseFilename.substringBeforeLast(".")
        
        // Get appropriate extension from content type
        val extension = getExtensionFromContentType(cv.contentType)
        
        return "$filenameWithoutExtension.$extension"
    }
    
    private fun getExtensionFromContentType(contentType: String): String {
        return when (contentType.lowercase()) {
            "pdf" -> "pdf"
            "doc" -> "doc" 
            "docx" -> "docx"
            "application/pdf" -> "pdf"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> "pdf" // Default to PDF
        }
    }

    private fun extractFilenameFromStoragePath(storagePath: String?): String? {
        if (storagePath == null) return null
        // Extract original filename from path like: users/userId/cvs/timestamp-uuid-filename.pdf
        val pathParts = storagePath.split("/")
        val filename = pathParts.lastOrNull() ?: return null
        // Remove timestamp and uuid prefix (format: timestamp-uuid-originalname.ext)
        val parts = filename.split("-", limit = 3)
        return if (parts.size >= 3) parts[2] else filename
    }
    
    suspend fun downloadCVContent(storagePath: String): ByteArray {
        logger.info("Downloading CV content from storage path: $storagePath")
        return firebaseStorageService.downloadFile(storagePath)
    }
    
    suspend fun deleteCV(userId: String, cvId: String): Boolean {
        logger.info("Deleting CV: id=$cvId for user: $userId")
        
        val cv = cvRepository.findByUserIdAndId(userId, cvId)
        if (cv == null) {
            logger.warn("CV not found for deletion: id=$cvId, userId=$userId")
            return false
        }
        
        try {
            // Delete from Firebase Storage if storagePath exists
            if (cv.storagePath != null) {
                firebaseStorageService.deleteFile(cv.storagePath)
            } else {
                logger.warn("CV ${cv.id} has no storagePath, skipping Firebase deletion")
            }
            
            // Delete from MongoDB
            val deleted = cvRepository.deleteById(cvId)
            
            if (deleted) {
                logger.info("Successfully deleted CV: id=$cvId, userId=$userId")
                return true
            } else {
                logger.warn("Failed to delete CV from MongoDB: id=$cvId, userId=$userId")
                return false
            }
            
        } catch (e: Exception) {
            logger.error("Failed to delete CV: id=$cvId, userId=$userId", e)
            return false
        }
    }
}