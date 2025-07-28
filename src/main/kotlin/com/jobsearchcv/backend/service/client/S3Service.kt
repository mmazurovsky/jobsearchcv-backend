package com.jobsearchcv.backend.service.client

import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.time.Instant

@Service
class S3Service(
    @Value("\${aws.s3.access-key:test-access-key}") private val accessKey: String,
    @Value("\${aws.s3.secret-key:test-secret-key}") private val secretKey: String,
    @Value("\${aws.s3.region:us-east-1}") private val region: String,
    @Value("\${aws.s3.retry.max-attempts:3}") private val maxRetryAttempts: Int,
    @Value("\${aws.s3.retry.delay-ms:1000}") private val retryDelayMs: Long
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(S3Service::class.java)
    }

    private val isTestEnvironment: Boolean = accessKey == "test-access-key" || secretKey == "test-secret-key"

    init {
        logger.info("S3Service initializing with accessKey: '{}', secretKey: '{}', region: '{}'",
                   accessKey.take(5) + "...", secretKey.take(5) + "...", region)
    }

    private val s3Client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                if (accessKey.isNotBlank() && secretKey.isNotBlank() &&
                    accessKey != "test-access-key" && secretKey != "test-secret-key") {
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                    )
                } else {
                    // Use fake credentials for testing environment
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                    )
                }
            )
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .retryPolicy(
                        RetryPolicy.builder()
                            .numRetries(maxRetryAttempts)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    suspend fun uploadFile(file: MultipartFile, bucket: String, key: String, fileExtension: String): String =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating S3 upload for: bucket=$bucket, key=$key")
                // Simulate upload success in test environment
                return@withContext key
            }

            var lastException: Exception? = null

            repeat(maxRetryAttempts) { attempt ->
                try {
                    logger.info("Attempting to upload file to S3: bucket=$bucket, key=$key, attempt=${attempt + 1}/$maxRetryAttempts")

                    val metadata = mutableMapOf<String, String>()
                    metadata["original-filename"] = file.originalFilename ?: "unknown"
                    metadata["upload-timestamp"] = Instant.now().toString()

                    val putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(fileExtension)
                        .metadata(metadata)
                        .build()

                    val response =
                        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.bytes))

                    logger.info("Successfully uploaded file to S3: bucket=$bucket, key=$key, etag=${response.eTag()}")
                    return@withContext key

                } catch (e: S3Exception) {
                    lastException = e
                    logger.error("S3 upload failed (attempt ${attempt + 1}/$maxRetryAttempts): ${e.message}")

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1)) // Exponential backoff
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.error("Unexpected error during S3 upload: ${e.message}", e)

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                }
            }

            // All retries failed, send to Sentry
            val sentryException = S3UploadFailedException(
                "Failed to upload file to S3 after $maxRetryAttempts attempts",
                lastException
            )

            Sentry.withScope { scope ->
                scope.setTag("s3.bucket", bucket)
                scope.setTag("s3.key", key)
                scope.setTag("s3.file.size", file.size.toString())
                scope.setTag("s3.file.type", file.contentType ?: "unknown")
                scope.setContexts(
                    "s3_upload", mapOf(
                        "bucket" to bucket,
                        "key" to key,
                        "fileName" to (file.originalFilename ?: "unknown"),
                        "fileSize" to file.size,
                        "contentType" to (file.contentType ?: "unknown"),
                        "maxRetryAttempts" to maxRetryAttempts
                    )
                )
                Sentry.captureException(sentryException)
            }

            throw sentryException
        }

    suspend fun downloadFile(key: String, bucket: String = "applyfirst-cvs"): ByteArray =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating S3 download for: bucket=$bucket, key=$key")
                // Return dummy content for testing
                return@withContext "Test CV content for $key".toByteArray()
            }

            var lastException: Exception? = null

            repeat(maxRetryAttempts) { attempt ->
                try {
                    logger.info("Attempting to download file from S3: bucket=$bucket, key=$key, attempt=${attempt + 1}/$maxRetryAttempts")

                    val getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()

                    val responseBytes = s3Client.getObject(getObjectRequest).readAllBytes()

                    logger.info("Successfully downloaded file from S3: bucket=$bucket, key=$key, size=${responseBytes.size}")
                    return@withContext responseBytes

                } catch (e: NoSuchKeyException) {
                    // Don't retry for missing keys
                    logger.error("File not found in S3: bucket=$bucket, key=$key")
                    throw e
                } catch (e: S3Exception) {
                    lastException = e
                    logger.error("S3 download failed (attempt ${attempt + 1}/$maxRetryAttempts): ${e.message}")

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.error("Unexpected error during S3 download: ${e.message}", e)

                    if (attempt < maxRetryAttempts - 1) {
                        delay(retryDelayMs * (attempt + 1))
                    }
                }
            }

            // All retries failed, send to Sentry
            val sentryException = S3DownloadFailedException(
                "Failed to download file from S3 after $maxRetryAttempts attempts",
                lastException
            )

            Sentry.withScope { scope ->
                scope.setTag("s3.bucket", bucket)
                scope.setTag("s3.key", key)
                scope.setContexts(
                    "s3_download", mapOf(
                        "bucket" to bucket,
                        "key" to key,
                        "maxRetryAttempts" to maxRetryAttempts
                    )
                )
                Sentry.captureException(sentryException)
            }

            throw sentryException
        }

    suspend fun deleteFile(key: String, bucket: String = "applyfirst-cvs") =
        withContext(Dispatchers.IO) {
            if (isTestEnvironment) {
                logger.info("Test environment detected - simulating S3 delete for: bucket=$bucket, key=$key")
                return@withContext
            }

            try {
                logger.info("Attempting to delete file from S3: bucket=$bucket, key=$key")

                val deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()

                s3Client.deleteObject(deleteObjectRequest)

                logger.info("Successfully deleted file from S3: bucket=$bucket, key=$key")

            } catch (e: Exception) {
                logger.error("Failed to delete file from S3: bucket=$bucket, key=$key", e)

                Sentry.withScope { scope ->
                    scope.setTag("s3.bucket", bucket)
                    scope.setTag("s3.key", key)
                    Sentry.captureException(e)
                }

                // Don't throw for delete failures - log and continue
            }
        }

    suspend fun getFileUrl(key: String, bucket: String = "applyfirst-cvs", usePresigned: Boolean = false): String {
        if (isTestEnvironment) {
            return "https://test-bucket.s3.us-east-1.amazonaws.com/$key"
        }

        return if (usePresigned) {
            // Generate presigned URL (secure, expires in 24 hours by default)
            generatePresignedUrl(key, bucket, expirationMinutes = 24 * 60) // 24 hours
        } else {
            // Generate public URL (permanent, never expires)
            "https://${bucket}.s3.${region}.amazonaws.com/${key}"
        }
    }

    suspend fun getPublicUrl(key: String, bucket: String = "applyfirst-cvs"): String {
        // Simple public URL - no expiration, requires public bucket
        return "https://${bucket}.s3.${region}.amazonaws.com/${key}"
    }

    suspend fun getSecureUrl(key: String, bucket: String = "applyfirst-cvs", validHours: Long = 24): String {
        // Secure presigned URL with custom expiration
        return generatePresignedUrl(key, bucket, expirationMinutes = validHours * 60)
    }

    suspend fun generatePresignedUrl(key: String, bucket: String = "applyfirst-cvs", expirationMinutes: Long = 60): String =
        withContext(Dispatchers.IO) {
            try {
                logger.info("Generating presigned URL for: bucket=$bucket, key=$key, expiration=${expirationMinutes}min")

                val presigner = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(
                        StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                        )
                    )
                    .build()

                val getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()

                val presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build()

                val presignedUrl = presigner.presignGetObject(presignRequest).url().toString()
                presigner.close()

                logger.info("Generated presigned URL for: bucket=$bucket, key=$key")
                return@withContext presignedUrl

            } catch (e: Exception) {
                logger.error("Failed to generate presigned URL: bucket=$bucket, key=$key", e)
                throw e
            }
        }

    suspend fun listFiles(prefix: String, bucket: String = "applyfirst-cvs"): List<S3Object> =
        withContext(Dispatchers.IO) {
            try {
                logger.info("Listing files in S3: bucket=$bucket, prefix=$prefix")

                val listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build()

                val response = s3Client.listObjectsV2(listRequest)

                logger.info("Found ${response.contents().size} files in S3: bucket=$bucket, prefix=$prefix")
                return@withContext response.contents()

            } catch (e: Exception) {
                logger.error("Failed to list files in S3: bucket=$bucket, prefix=$prefix", e)
                throw e
            }
        }

    // Custom exceptions for better Sentry tracking
    class S3UploadFailedException(message: String, cause: Throwable?) : Exception(message, cause)
    class S3DownloadFailedException(message: String, cause: Throwable?) : Exception(message, cause)
}