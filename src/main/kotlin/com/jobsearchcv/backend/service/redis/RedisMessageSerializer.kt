package com.jobsearchcv.backend.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.JobSearchRequestMessage
import com.jobsearchcv.backend.domain.model.JobSearchResultMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RedisMessageSerializer(
    private val objectMapper: ObjectMapper // Reuses existing Spring Jackson config
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Serializes request message to Redis Stream field map.
     * Stores full JSON in "payload" field and duplicates key fields for easy filtering.
     *
     * @param message The message to serialize
     * @return Map of field names to values for Redis XADD
     */
    fun serializeRequest(message: JobSearchRequestMessage): Map<String, String> {
        return try {
            mapOf(
                "payload" to objectMapper.writeValueAsString(message),
                "job_search_id" to message.jobSearchId, // Duplicate for easy filtering
                "user_id" to message.userId,
                "priority" to message.priority.toString(),
                "timestamp" to message.timestamp.toString()
            )
        } catch (e: Exception) {
            log.error("Failed to serialize JobSearchRequestMessage", e)
            throw IllegalStateException("Serialization failed", e)
        }
    }

    /**
     * Deserializes result message from Redis Stream field map.
     * Expects a "payload" field containing the full JSON.
     *
     * @param fields The Redis Stream message fields
     * @return Deserialized JobSearchResultMessage
     * @throws IllegalStateException if deserialization fails
     */
    fun deserializeResult(fields: Map<String, String>): JobSearchResultMessage {
        return try {
            val payload = fields["payload"]
                ?: throw IllegalArgumentException("Missing 'payload' field")
            objectMapper.readValue<JobSearchResultMessage>(payload)
        } catch (e: Exception) {
            log.error("Failed to deserialize JobSearchResultMessage: fields=$fields", e)
            throw IllegalStateException("Deserialization failed", e)
        }
    }

    /**
     * Creates DLQ entry with error context.
     * Preserves original message and adds DLQ-specific metadata.
     *
     * @param originalMessage The original message fields
     * @param errorMessage The error that caused the DLQ move
     * @param retryCount Number of retry attempts made
     * @return Map of fields for DLQ entry
     */
    fun serializeDlqEntry(
        originalMessage: Map<String, String>,
        errorMessage: String,
        retryCount: Int
    ): Map<String, String> {
        return originalMessage.toMutableMap().apply {
            put("dlq_timestamp", System.currentTimeMillis().toString())
            put("dlq_error", errorMessage)
            put("dlq_retry_count", retryCount.toString())
        }
    }
}
