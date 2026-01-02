package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.SearchJobsParams
import com.jobsearchcv.backend.service.client.ScraperClient
import com.jobsearchcv.backend.service.redis.RedisJobSearchProducer
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ScraperJobService(
    private val scraperClient: ScraperClient,
    @Value("\${CALLBACK_URL}") private val callbackUrl: String,
    @Value("\${redis.streams.enabled:false}") private val redisEnabled: Boolean,
    @Value("\${redis.fallback-to-http:true}") private val fallbackToHttp: Boolean,
) {

    @Autowired(required = false)
    private var redisProducer: RedisJobSearchProducer? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ScraperJobService::class.java)
    }

    private val semaphore = Semaphore(4) // Limit to 4 concurrent jobs like main_project

    @PostConstruct
    fun initialize() {
        logger.info(
            "ScraperJobService initialized - Redis Streams: {}, Fallback to HTTP: {}, Producer available: {}",
            if (redisEnabled) "ENABLED" else "DISABLED",
            if (fallbackToHttp) "ENABLED" else "DISABLED",
            if (redisProducer != null) "YES" else "NO"
        )

        if (!redisEnabled && !fallbackToHttp) {
            logger.warn("WARNING: Both Redis Streams and HTTP fallback are disabled! Job searches will fail!")
        }

        if (!redisEnabled) {
            logger.info("Redis Streams is disabled. All job search requests will use HTTP communication.")
        }
    }

    suspend fun triggerScraperJobAndLog(jobSearch: JobSearchOut) {
        triggerScraperJobWithSearchName(jobSearch, jobSearch.timePeriod.displayName, null)
    }

    suspend fun triggerScraperJobWithSearchName(jobSearch: JobSearchOut, timePeriod: String, searchName: String?) {
        semaphore.withPermit {
            // Build callback URL
            val callbackUrl = callbackUrl.trimEnd('/') + "/api/job-data-callback"

            val params =
                    SearchJobsParams(
                            keywords = jobSearch.jobTitle,
                            location = jobSearch.location,
                            jobTypes = jobSearch.jobTypes.map { it.label },
                            remoteTypes = jobSearch.remoteTypes.map { it.label },
                            timePeriod = timePeriod,
                            filterText = jobSearch.filterText,
                            callbackUrl = callbackUrl,
                            jobSearchId = jobSearch.id,
                            userId = jobSearch.userId,
                            searchName = searchName
                    )

            // Determine priority (admin jobs get priority 10, regular jobs get 5)
            val priority = if (jobSearch.isAdmin == true) 10 else 5

            // Log communication method decision
            logger.debug(
                "Job search request routing decision: jobSearchId={}, redisEnabled={}, redisProducer={}, fallbackEnabled={}",
                jobSearch.id, redisEnabled, if (redisProducer != null) "available" else "null", fallbackToHttp
            )

            // Try Redis Streams first if enabled
            var sentViaRedis = false
            if (redisEnabled && redisProducer != null) {
                logger.info("Attempting to send job search via Redis Streams: jobSearchId={}", jobSearch.id)
                try {
                    val producer = redisProducer  // Local variable to avoid smart cast issues
                    val messageId = producer?.publishJobSearchRequest(params, priority)
                    if (messageId != null) {
                        sentViaRedis = true
                        logger.info(
                            "✓ Successfully sent job search via Redis Streams: jobSearchId={}, messageId={}, priority={}, searchName={}",
                            jobSearch.id, messageId, priority, searchName
                        )
                    } else {
                        logger.warn(
                            "✗ Redis publish returned null after retries, will use HTTP fallback: jobSearchId={}",
                            jobSearch.id
                        )
                    }
                } catch (e: Exception) {
                    logger.error(
                        "✗ Redis publish threw exception, will use HTTP fallback: jobSearchId={}, error={}",
                        jobSearch.id, e.message, e
                    )
                }
            } else {
                val reason = when {
                    !redisEnabled -> "Redis Streams is disabled (ENABLE_REDIS_STREAMS=false)"
                    redisProducer == null -> "RedisProducer bean is not available (Redis configuration may have failed)"
                    else -> "Unknown reason"
                }
                logger.info("Skipping Redis Streams: {}. Will use HTTP fallback.", reason)
            }

            // Fallback to HTTP if Redis failed or disabled
            if (!sentViaRedis && fallbackToHttp) {
                logger.info("Attempting to send job search via HTTP: jobSearchId={}", jobSearch.id)
                try {
                    val response = scraperClient.scrapeJobs(params)
                    val logDataWithStatus =
                            jobSearch.toLogString() +
                                    ("callback_url" to callbackUrl) +
                                    ("status_code" to response.statusCode) +
                                    ("search_name" to searchName) +
                                    ("fallback_used" to !redisEnabled)

                    if (!response.isSuccessful) {
                        val logDataWithResponse = logDataWithStatus + ("response_text" to response.body)
                        logger.error("✗ Failed to trigger scraper job via HTTP: {}", logDataWithResponse)
                    } else {
                        logger.info("✓ Successfully sent job search via HTTP: {}", logDataWithStatus)
                    }
                } catch (e: Exception) {
                    logger.error("✗ Exception triggering scraper job via HTTP: {}", jobSearch.toLogString(), e)
                }
            } else if (!sentViaRedis && !fallbackToHttp) {
                logger.error(
                    "✗ CRITICAL: Job search could not be sent! Redis failed and HTTP fallback is disabled: jobSearchId={}",
                    jobSearch.id
                )
            }
        }
    }
}
