package com.jobsearchcv.backend.service.redis

import com.jobsearchcv.backend.domain.model.JobSearchRequestMessage
import com.jobsearchcv.backend.domain.model.SearchJobsParams
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(RedisTemplate::class)
class RedisJobSearchProducer(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messageSerializer: RedisMessageSerializer,
    @Value("\${redis.streams.request-stream:job-search:requests}")
    private val requestStream: String,
    @Value("\${redis.producer.max-retry-attempts:3}")
    private val maxRetryAttempts: Int,
    @Value("\${redis.producer.retry-delay-ms:1000}")
    private val retryDelayMs: Long
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Publishes job search request to Redis Stream with retry logic.
     *
     * @param params The job search parameters
     * @param priority Priority level (1-10, higher = more urgent)
     * @return RecordId if successful, null if all retries failed
     */
    suspend fun publishJobSearchRequest(
        params: SearchJobsParams,
        priority: Int = 5
    ): RecordId? {
        val message = JobSearchRequestMessage.fromSearchJobsParams(params, priority)

        repeat(maxRetryAttempts) { attempt ->
            try {
                val fields = messageSerializer.serializeRequest(message)
                val record = StreamRecords.newRecord()
                    .`in`(requestStream)
                    .ofMap(fields)

                val recordId = redisTemplate.opsForStream<String, String>().add(record)

                log.info(
                    "Published job search request: " +
                            "messageId={}, jobSearchId={}, userId={}, priority={}, stream={}",
                    recordId, message.jobSearchId, message.userId, priority, requestStream
                )

                return recordId

            } catch (e: Exception) {
                val attemptNum = attempt + 1
                log.error(
                    "Failed to publish to Redis (attempt $attemptNum/$maxRetryAttempts): " +
                            "jobSearchId={}, userId={}",
                    message.jobSearchId, message.userId, e
                )

                if (attemptNum < maxRetryAttempts) {
                    delay(retryDelayMs * attemptNum) // Exponential backoff
                } else {
                    // All retries exhausted
                    Sentry.captureException(e)
                    Sentry.captureMessage(
                        "Redis publish failed after $maxRetryAttempts attempts: ${message.jobSearchId}",
                        SentryLevel.ERROR
                    )
                }
            }
        }

        return null // All retries failed
    }

    /**
     * Check if Redis is available (health check).
     *
     * @return true if Redis ping succeeds, false otherwise
     */
    fun isRedisAvailable(): Boolean {
        return try {
            redisTemplate.connectionFactory?.connection?.ping() != null
        } catch (e: Exception) {
            log.warn("Redis health check failed", e)
            false
        }
    }
}
