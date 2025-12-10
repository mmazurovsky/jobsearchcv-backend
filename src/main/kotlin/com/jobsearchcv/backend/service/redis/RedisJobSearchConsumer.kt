package com.jobsearchcv.backend.service.redis

import com.jobsearchcv.backend.domain.model.JobSearchResultMessage
import com.jobsearchcv.backend.service.IncomingJobsProcessingService
import io.sentry.Sentry
import io.sentry.SentryLevel
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.*
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
@ConditionalOnBean(StreamMessageListenerContainer::class)
class RedisJobSearchConsumer(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messageSerializer: RedisMessageSerializer,
    private val incomingJobsProcessingService: IncomingJobsProcessingService,
    private val streamContainer: StreamMessageListenerContainer<String, *>,
    @Value("\${redis.streams.result-stream:job-search:results}")
    private val resultStream: String,
    @Value("\${redis.streams.consumer-group:backend-processors}")
    private val consumerGroup: String,
    @Value("\${redis.streams.dlq-stream:job-search:dlq}")
    private val dlqStream: String,
    @Value("\${redis.consumer.name:backend-consumer}")
    private val consumerName: String,
    @Value("\${redis.consumer.max-retries:3}")
    private val maxRetries: Int,
    @Value("\${redis.consumer.claim-timeout-ms:60000}")
    private val claimTimeoutMs: Long,
    @Value("\${redis.consumer.claim-interval-ms:30000}")
    private val claimIntervalMs: Long
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMessages = ConcurrentHashMap<String, Long>() // messageId -> startTime

    private var subscription: Subscription? = null

    @PostConstruct
    fun initialize() {
        log.info("Initializing Redis Stream consumer: consumer=$consumerName, group=$consumerGroup, stream=$resultStream")

        // Ensure consumer group exists
        createConsumerGroupIfNotExists()

        // Process any pending messages for THIS consumer before starting
        processPendingMessagesOnStartup()

        // Start listening to stream
        startStreamListener()

        // Start background claim worker
        startClaimWorker()

        log.info("Redis Stream consumer initialized successfully")
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down Redis Stream consumer")
        subscription?.cancel()
    }

    private fun createConsumerGroupIfNotExists() {
        try {
            val streamOps = redisTemplate.opsForStream<String, String>()

            // Try to create group (idempotent - will error if exists)
            try {
                streamOps.createGroup(resultStream, consumerGroup)
                log.info("Created consumer group: $consumerGroup on stream: $resultStream")
            } catch (e: Exception) {
                // Group already exists - this is fine
                log.debug("Consumer group already exists: $consumerGroup")
            }

        } catch (e: Exception) {
            log.error("Failed to ensure consumer group exists", e)
            throw IllegalStateException("Cannot initialize Redis consumer", e)
        }
    }

    /**
     * Process any pending messages for THIS consumer on startup.
     * This ensures we complete any work that was interrupted by a crash/restart.
     */
    private fun processPendingMessagesOnStartup() {
        try {
            val streamOps = redisTemplate.opsForStream<String, String>()
            val consumer = Consumer.from(consumerGroup, consumerName)

            // Read pending messages for this consumer (use "0" to start from beginning of PEL)
            val pendingMessages = streamOps.read(
                consumer,
                StreamReadOptions.empty().count(100), // Process up to 100 pending messages
                StreamOffset.create(resultStream, ReadOffset.from("0"))
            )

            if (pendingMessages?.isEmpty() != false) {
                log.info("No pending messages found for consumer: $consumerName")
                return
            }

            log.info("Found ${pendingMessages.size} pending messages for consumer: $consumerName, processing...")

            pendingMessages.forEach { message ->
                @Suppress("UNCHECKED_CAST")
                handleMessage(message as MapRecord<String, String, String>)
            }

            log.info("Finished processing ${pendingMessages.size} pending messages")

        } catch (e: Exception) {
            log.error("Error processing pending messages on startup", e)
            // Don't throw - continue with normal startup
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun startStreamListener() {
        val readOffset = ReadOffset.lastConsumed()
        val streamOffset = StreamOffset.create(resultStream, readOffset)
        val consumer = Consumer.from(consumerGroup, consumerName)

        subscription = streamContainer.register(
            StreamMessageListenerContainer.StreamReadRequest.builder(streamOffset)
                .consumer(consumer)
                .autoAcknowledge(false) // Manual ACK for reliability
                .build(),
            StreamListener { message ->
                handleMessage(message as MapRecord<String, String, String>)
            }
        )

        log.info("Started listening to stream: $resultStream")
    }

    private fun handleMessage(message: MapRecord<String, String, String>) {
        val messageId = message.id.value
        val fields = message.value

        processingMessages[messageId] = System.currentTimeMillis()

        try {
            log.info("Received message: messageId={}, stream={}", messageId, resultStream)

            // Deserialize result message
            val resultMessage = messageSerializer.deserializeResult(fields)

            // Get retry count from stream metadata
            val deliveryCount = getDeliveryCount(messageId)

            log.info(
                "Processing job search result: jobSearchId={}, userId={}, " +
                        "status={}, jobCount={}, deliveryCount={}",
                resultMessage.jobSearchId,
                resultMessage.userId,
                resultMessage.status,
                resultMessage.jobs.size,
                deliveryCount
            )

            // Delegate to existing processing service
            scope.launch {
                try {
                    incomingJobsProcessingService.processIncomingJobData(
                        jobSearchId = resultMessage.jobSearchId,
                        scrapedJobs = resultMessage.jobs,
                        userId = resultMessage.userId,
                        searchName = resultMessage.searchName
                    )

                    // Acknowledge and delete on success
                    acknowledgeAndDelete(messageId)

                } catch (e: Exception) {
                    log.error("Error processing job data from Redis", e)
                    handleProcessingError(messageId, fields, deliveryCount, e)
                } finally {
                    processingMessages.remove(messageId)
                }
            }

        } catch (e: Exception) {
            log.error("Error handling Redis message: messageId=$messageId", e)
            processingMessages.remove(messageId)
            // Don't ACK - message stays in pending for retry
        }
    }

    private fun acknowledgeAndDelete(messageId: String) {
        try {
            val streamOps = redisTemplate.opsForStream<String, String>()

            // ACK message
            streamOps.acknowledge(resultStream, consumerGroup, messageId)

            // Delete message from stream (cleanup)
            streamOps.delete(resultStream, messageId)

            log.info("Acknowledged and deleted message: messageId={}", messageId)

        } catch (e: Exception) {
            log.error("Failed to ACK/delete message: messageId=$messageId", e)
        }
    }

    private fun handleProcessingError(
        messageId: String,
        fields: Map<String, String>,
        deliveryCount: Int,
        error: Exception
    ) {
        if (deliveryCount >= maxRetries) {
            log.error(
                "Message exceeded max retries ($maxRetries), moving to DLQ: messageId=$messageId",
                error
            )

            // Move to DLQ
            moveToDlq(messageId, fields, error.message ?: "Unknown error", deliveryCount)

            // ACK and delete original message after moving to DLQ
            acknowledgeAndDelete(messageId)

            // Alert via Sentry
            Sentry.captureException(error)
            Sentry.captureMessage(
                "Message moved to DLQ after $maxRetries retries: $messageId",
                SentryLevel.ERROR
            )
        } else {
            log.warn(
                "Message processing failed (attempt $deliveryCount/$maxRetries), " +
                        "will retry: messageId=$messageId"
            )
            // Don't ACK - message stays in pending for XCLAIM
        }
    }

    private fun moveToDlq(
        messageId: String,
        fields: Map<String, String>,
        errorMessage: String,
        retryCount: Int
    ) {
        try {
            val dlqFields = messageSerializer.serializeDlqEntry(fields, errorMessage, retryCount)
            val record = StreamRecords.newRecord()
                .`in`(dlqStream)
                .ofMap(dlqFields)

            redisTemplate.opsForStream<String, String>().add(record)

            log.info("Moved message to DLQ: originalMessageId={}, dlqStream={}", messageId, dlqStream)

        } catch (e: Exception) {
            log.error("Failed to move message to DLQ: messageId=$messageId", e)
        }
    }

    /**
     * Background worker that claims abandoned messages (XCLAIM)
     */
    private fun startClaimWorker() {
        scope.launch {
            while (true) {
                try {
                    delay(claimIntervalMs)
                    claimAbandonedMessages()
                } catch (e: Exception) {
                    log.error("Error in claim worker", e)
                }
            }
        }
    }

    private suspend fun claimAbandonedMessages() {
        try {
            val streamOps = redisTemplate.opsForStream<String, String>()

            // Get pending messages for this consumer group
            val pendingInfo = streamOps.pending(
                resultStream,
                consumerGroup,
                Range.unbounded<String>(),
                100L // Check up to 100 messages
            )

            if (pendingInfo.isEmpty) {
                return
            }

            log.debug("Found {} pending messages in group {}", pendingInfo.size(), consumerGroup)

            val claimTimeoutDuration = Duration.ofMillis(claimTimeoutMs)

            pendingInfo.forEach { pendingMessage ->
                val messageId = pendingMessage.id.value
                val idleTime = pendingMessage.elapsedTimeSinceLastDelivery

                // Skip if message is currently being processed
                if (processingMessages.containsKey(messageId)) {
                    return@forEach
                }

                // Claim if idle longer than timeout
                if (idleTime > claimTimeoutDuration) {
                    log.info(
                        "Claiming abandoned message: messageId={}, idleTime={}ms, deliveryCount={}",
                        messageId,
                        idleTime.toMillis(),
                        pendingMessage.totalDeliveryCount
                    )

                    try {
                        // XCLAIM message to this consumer
                        val claimedMessages = streamOps.claim(
                            resultStream,
                            consumerGroup,
                            consumerName,
                            claimTimeoutDuration,
                            RecordId.of(messageId)
                        )

                        // Process claimed messages
                        claimedMessages.forEach { claimed ->
                            @Suppress("UNCHECKED_CAST")
                            handleMessage(claimed as MapRecord<String, String, String>)
                        }

                    } catch (e: Exception) {
                        log.error("Failed to claim message: messageId=$messageId", e)
                    }
                }
            }

        } catch (e: Exception) {
            log.error("Error checking pending messages", e)
        }
    }

    private fun getDeliveryCount(messageId: String): Int {
        return try {
            val streamOps = redisTemplate.opsForStream<String, String>()
            val pendingMessages = streamOps.pending(
                resultStream,
                Consumer.from(consumerGroup, consumerName),
                Range.closed(messageId, messageId),
                1L
            )

            pendingMessages.firstOrNull()?.totalDeliveryCount?.toInt() ?: 1

        } catch (e: Exception) {
            log.warn("Failed to get delivery count for messageId=$messageId", e)
            1
        }
    }
}
