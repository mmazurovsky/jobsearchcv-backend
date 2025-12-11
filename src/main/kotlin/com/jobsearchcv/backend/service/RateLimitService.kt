package com.jobsearchcv.backend.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for rate limiting prompt processing requests to prevent abuse and prompt injection attacks
 */
@Service
class RateLimitService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RateLimitService::class.java)
        private const val MAX_REQUESTS = 10
        private val TIME_WINDOW: Duration = Duration.ofMinutes(1)
    }

    private val userRequests = ConcurrentHashMap<String, MutableList<Instant>>()

    /**
     * Check if user has exceeded rate limit
     * @param userId User ID to check
     * @throws IllegalArgumentException if rate limit exceeded
     */
    fun checkRateLimit(userId: String) {
        val now = Instant.now()
        val requests = userRequests.getOrPut(userId) { mutableListOf() }

        // Remove old requests outside time window
        requests.removeIf { it.isBefore(now.minus(TIME_WINDOW)) }

        if (requests.size >= MAX_REQUESTS) {
            logger.warn("Rate limit exceeded for user: $userId (${requests.size} requests in ${TIME_WINDOW.toMinutes()} minute(s))")
            throw IllegalArgumentException("Rate limit exceeded. Try again in ${TIME_WINDOW.toMinutes()} minute(s)")
        }

        requests.add(now)
        logger.debug("Rate limit check passed for user: $userId (${requests.size}/$MAX_REQUESTS requests)")
    }

    /**
     * Get current request count for a user
     */
    fun getCurrentRequestCount(userId: String): Int {
        val now = Instant.now()
        val requests = userRequests[userId] ?: return 0

        // Clean up old requests
        requests.removeIf { it.isBefore(now.minus(TIME_WINDOW)) }

        return requests.size
    }

    /**
     * Clear rate limit data for a user (useful for testing)
     */
    fun clearUserRateLimit(userId: String) {
        userRequests.remove(userId)
        logger.debug("Cleared rate limit data for user: $userId")
    }
}
