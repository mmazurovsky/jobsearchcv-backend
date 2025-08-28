package com.jobsearchcv.backend.service.client

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.cloud.StorageClient
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Service
class FirebaseStorageService(
    @Value("\${firebase.storage.bucket:applyfirst-b9c69.firebasestorage.app}") private val bucketName: String,
    @Value("\${firebase.storage.retry.max-attempts:3}") private val maxRetryAttempts: Int,
    @Value("\${firebase.storage.retry.delay-ms:1000}") private val retryDelayMs: Long,
    @Value("\${spring.profiles.active:}") private val activeProfile: String
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FirebaseStorageService::class.java)
    }

    private val isTestEnvironment: Boolean = activeProfile.contains("test")

    init {
        logger.info("FirebaseStorageService initializing with bucket: '{}', profile: '{}'", bucketName, activeProfile)
    }

    private val storage: Storage by lazy {
        if (isTestEnvironment) {
            // For test environment, we'll simulate storage operations
            null as Storage
        } else {
            try {
                // Use the default Firebase app which should be already initialized
                val firebaseApp = FirebaseApp.getInstance()
                StorageClient.getInstance(firebaseApp).bucket(bucketName).storage
            } catch (e: Exception) {
                logger.warn("Failed to get Firebase Storage from FirebaseApp, falling back to default credentials", e)
                // Fallback to default Google Cloud credentials
                StorageOptions.getDefaultInstance().service
            }
        }
    }

    suspend fun uploadFile(file: MultipartFile, userId: String, fileExtension: String): String =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating Firebase Storage upload for userId: $userId")
                return@withContext generateStoragePath(userId, file.originalFilename ?: "test-cv", fileExtension)
            }

            var lastException: Exception? = null
            val storagePath = generateStoragePath(userId, file.originalFilename ?: "cv", fileExtension)

            repeat(maxRetryAttempts) { attempt ->
                try {
                    logger.info("Attempting to upload file to Firebase Storage: path=$storagePath, attempt=${attempt + 1}/$maxRetryAttempts")

                    val blobId = BlobId.of(bucketName, storagePath)
                    val blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType(getContentType(fileExtension))
                        .setMetadata(
                            mapOf(
                                "uploadedBy" to userId,
                                "originalFilename" to (file.originalFilename ?: "unknown"),
                                "uploadTimestamp" to Instant.now().toString(),
                                "fileSize" to file.size.toString()
                            )
                        )
                        .build()

                    val blob = storage.create(blobInfo, file.bytes)

                    logger.info("Successfully uploaded file to Firebase Storage: path=$storagePath, blobId=${blob.blobId}")
                    return@withContext storagePath

                } catch (e: StorageException) {
                    lastException = e
                    logger.error("Firebase Storage upload failed (attempt ${attempt + 1}/$maxRetryAttempts): ${e.message}")

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1)) // Exponential backoff
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.error("Unexpected error during Firebase Storage upload: ${e.message}", e)

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                }
            }

            // All retries failed, send to Sentry
            val sentryException = FirebaseStorageUploadFailedException(
                "Failed to upload file to Firebase Storage after $maxRetryAttempts attempts",
                lastException
            )

            Sentry.withScope { scope ->
                scope.setTag("firebase.storage.bucket", bucketName)
                scope.setTag("firebase.storage.path", storagePath)
                scope.setTag("firebase.storage.file.size", file.size.toString())
                scope.setTag("firebase.storage.file.type", file.contentType ?: "unknown")
                scope.setContexts(
                    "firebase_storage_upload", mapOf(
                        "bucket" to bucketName,
                        "path" to storagePath,
                        "fileName" to (file.originalFilename ?: "unknown"),
                        "fileSize" to file.size,
                        "contentType" to (file.contentType ?: "unknown"),
                        "userId" to userId,
                        "maxRetryAttempts" to maxRetryAttempts
                    )
                )
                Sentry.captureException(sentryException)
            }

            throw sentryException
        }

    suspend fun downloadFile(storagePath: String): ByteArray =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating Firebase Storage download for: path=$storagePath")
                return@withContext "Test CV content for $storagePath".toByteArray()
            }

            var lastException: Exception? = null

            repeat(maxRetryAttempts) { attempt ->
                try {
                    logger.info("Attempting to download file from Firebase Storage: path=$storagePath, attempt=${attempt + 1}/$maxRetryAttempts")

                    val blobId = BlobId.of(bucketName, storagePath)
                    val blob = storage.get(blobId)
                        ?: throw FirebaseStorageNotFoundException("File not found: $storagePath")

                    val content = blob.getContent()

                    logger.info("Successfully downloaded file from Firebase Storage: path=$storagePath, size=${content.size}")
                    return@withContext content

                } catch (e: FirebaseStorageNotFoundException) {
                    // Don't retry for missing files
                    logger.error("File not found in Firebase Storage: path=$storagePath")
                    throw e
                } catch (e: StorageException) {
                    lastException = e
                    logger.error("Firebase Storage download failed (attempt ${attempt + 1}/$maxRetryAttempts): ${e.message}")

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.error("Unexpected error during Firebase Storage download: ${e.message}", e)

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                }
            }

            // All retries failed, send to Sentry
            val sentryException = FirebaseStorageDownloadFailedException(
                "Failed to download file from Firebase Storage after $maxRetryAttempts attempts",
                lastException
            )

            Sentry.withScope { scope ->
                scope.setTag("firebase.storage.bucket", bucketName)
                scope.setTag("firebase.storage.path", storagePath)
                scope.setContexts(
                    "firebase_storage_download", mapOf(
                        "bucket" to bucketName,
                        "path" to storagePath,
                        "maxRetryAttempts" to maxRetryAttempts
                    )
                )
                Sentry.captureException(sentryException)
            }

            throw sentryException
        }

    suspend fun deleteFile(storagePath: String) =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating Firebase Storage delete for: path=$storagePath")
                return@withContext
            }

            try {
                logger.info("Attempting to delete file from Firebase Storage: path=$storagePath")

                val blobId = BlobId.of(bucketName, storagePath)
                val deleted = storage.delete(blobId)

                if (deleted) {
                    logger.info("Successfully deleted file from Firebase Storage: path=$storagePath")
                } else {
                    logger.warn("File not found for deletion in Firebase Storage: path=$storagePath")
                }

            } catch (e: Exception) {
                logger.error("Failed to delete file from Firebase Storage: path=$storagePath", e)

                Sentry.withScope { scope ->
                    scope.setTag("firebase.storage.bucket", bucketName)
                    scope.setTag("firebase.storage.path", storagePath)
                    Sentry.captureException(e)
                }

                // Don't throw for delete failures - log and continue
            }
        }

    suspend fun getSecureDownloadUrl(storagePath: String, validHours: Long = 24): String =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                return@withContext "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/test%2F$storagePath?alt=media&token=test-token"
            }

            try {
                logger.info("Generating signed URL for Firebase Storage: path=$storagePath, validHours=$validHours")

                val blobId = BlobId.of(bucketName, storagePath)
                val blob = storage.get(blobId)
                    ?: throw FirebaseStorageNotFoundException("File not found: $storagePath")

                // Generate signed URL valid for specified hours
                val url = blob.signUrl(validHours, TimeUnit.HOURS)

                logger.info("Generated signed URL for Firebase Storage: path=$storagePath")
                return@withContext url.toString()

            } catch (e: Exception) {
                logger.error("Failed to generate signed URL for Firebase Storage: path=$storagePath", e)
                throw e
            }
        }

    suspend fun getUserFileExists(storagePath: String, userId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                return@withContext true
            }

            try {
                // Verify the file path belongs to the user for security
                if (!storagePath.startsWith("users/$userId/")) {
                    logger.warn("Attempted access to file outside user scope: userId=$userId, path=$storagePath")
                    return@withContext false
                }

                val blobId = BlobId.of(bucketName, storagePath)
                val blob = storage.get(blobId)

                return@withContext blob != null
            } catch (e: Exception) {
                logger.error("Error checking file existence: path=$storagePath, userId=$userId", e)
                return@withContext false
            }
        }

    private fun generateStoragePath(userId: String, originalFilename: String, fileExtension: String): String {
        val timestamp = System.currentTimeMillis()
        val uuid = java.util.UUID.randomUUID()
        val sanitizedFilename = originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        
        return "users/$userId/cvs/${timestamp}-${uuid}-${sanitizedFilename}"
    }

    private fun getContentType(fileExtension: String): String {
        return when (fileExtension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    // Custom exceptions for better error handling
    class FirebaseStorageUploadFailedException(message: String, cause: Throwable?) : Exception(message, cause)
    class FirebaseStorageDownloadFailedException(message: String, cause: Throwable?) : Exception(message, cause)
    class FirebaseStorageNotFoundException(message: String) : Exception(message)
}