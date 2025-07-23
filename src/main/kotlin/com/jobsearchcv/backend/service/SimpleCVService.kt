package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.SimpleCVInput
import com.jobsearchcv.backend.domain.model.SimpleUserCV
import com.jobsearchcv.backend.repository.SimpleCVRepository
import com.jobsearchcv.backend.service.client.S3Service
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class SimpleCVService(
    private val cvRepository: SimpleCVRepository,
    private val s3Service: S3Service,
    @Value("\${aws.s3.bucket.cvs}") private val cvsBucket: String
) {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SimpleCVService::class.java)
    }
    
    suspend fun uploadAndSaveCV(file: MultipartFile, userId: String): SimpleUserCV {
        logger.info("Uploading CV for user: $userId, filename: ${file.originalFilename}")
        
        try {
            // Generate S3 key with proper structure
            val fileName = file.originalFilename ?: "cv_${System.currentTimeMillis()}"
            val s3Key = "$userId-$fileName-${java.util.UUID.randomUUID()}"
            
            // Upload to S3
            val uploadedKey = s3Service.uploadFile(file, cvsBucket, s3Key)
            
            // Generate permanent public URL (never expires)
            val fileUrl = s3Service.getPublicUrl(uploadedKey, cvsBucket)
            
            // Create CV record
            val cvInput = SimpleCVInput(
                userId = userId,
                linkToCv = fileUrl,
                fileSize = file.size,
                contentType = file.contentType ?: "application/octet-stream",
                s3Bucket = cvsBucket,
                s3Key = uploadedKey
            )
            
            // Save to MongoDB
            val savedCV = cvRepository.save(cvInput.toSimpleUserCV())
            
            logger.info("Successfully uploaded and saved CV: id=${savedCV.id}, userId=$userId")
            
            return savedCV
            
        } catch (e: Exception) {
            logger.error("Failed to upload and save CV for user: $userId", e)
            throw e
        }
    }
    
    suspend fun getCVsByUserId(userId: String): List<SimpleUserCV> {
        logger.debug("Getting CVs for user: $userId")
        return cvRepository.findByUserId(userId).toList()
    }
    
    suspend fun deleteCV(userId: String, cvId: String): Boolean {
        logger.info("Deleting CV: id=$cvId for user: $userId")
        
        val cv = cvRepository.findByUserIdAndId(userId, cvId)
        if (cv == null) {
            logger.warn("CV not found for deletion: id=$cvId, userId=$userId")
            return false
        }
        
        try {
            // Delete from S3
            s3Service.deleteFile(cv.s3Key, cv.s3Bucket)
            
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