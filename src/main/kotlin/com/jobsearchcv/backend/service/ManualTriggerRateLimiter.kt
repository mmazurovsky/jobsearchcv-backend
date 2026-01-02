package com.jobsearchcv.backend.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Rate limiter for manual job search triggers.
 * Prevents abuse by limiting each job search to one trigger per 30 minutes.
 * Uses in-memory storage (ConcurrentHashMap) for simplicity.
 */
@Service
class ManualTriggerRateLimiter {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ManualTriggerRateLimiter::class.java)
        private val RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(30)
        private const val CLEANUP_INTERVAL_MINUTES = 60L
    }

    // Map of jobSearchId -> last trigger timestamp
    private val triggerHistory = ConcurrentHashMap<String, Instant>()

    init {
        // Schedule periodic cleanup to prevent memory leaks
        val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ManualTriggerCleanup").apply { isDaemon = true }
        }
        cleanupExecutor.scheduleAtFixedRate(
            ::cleanupOldEntries,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
        logger.info("ManualTriggerRateLimiter initialized with ${RATE_LIMIT_WINDOW.toMinutes()} minute rate limit")
    }

    /**
     * Check if a job search can be triggered now
     * @param jobSearchId The job search ID to check
     * @return true if can trigger, false if rate limited
     */
    fun canTrigger(jobSearchId: String): Boolean {
        val lastTrigger = triggerHistory[jobSearchId] ?: return true
        val now = Instant.now()
        val timeSinceLastTrigger = Duration.between(lastTrigger, now)

        return timeSinceLastTrigger >= RATE_LIMIT_WINDOW
    }

    /**
     * Get the next available trigger time for a job search
     * @param jobSearchId The job search ID to check
     * @return Instant when the job search can be triggered again, or null if can trigger now
     */
    fun getNextAvailableTime(jobSearchId: String): Instant? {
        val lastTrigger = triggerHistory[jobSearchId] ?: return null
        val nextAvailable = lastTrigger.plus(RATE_LIMIT_WINDOW)
        val now = Instant.now()

        return if (nextAvailable.isAfter(now)) nextAvailable else null
    }

    /**
     * Record a trigger for a job search
     * @param jobSearchId The job search ID that was triggered
     */
    fun recordTrigger(jobSearchId: String) {
        val now = Instant.now()
        triggerHistory[jobSearchId] = now
        logger.info("Recorded manual trigger for job search: $jobSearchId at $now")
    }

    /**
     * Get time remaining until next trigger is allowed
     * @param jobSearchId The job search ID to check
     * @return Duration remaining, or Duration.ZERO if can trigger now
     */
    fun getTimeUntilNextTrigger(jobSearchId: String): Duration {
        val nextAvailable = getNextAvailableTime(jobSearchId) ?: return Duration.ZERO
        val now = Instant.now()
        return Duration.between(now, nextAvailable)
    }

    /**
     * Clear rate limit for a job search (for testing)
     */
    fun clearRateLimit(jobSearchId: String) {
        triggerHistory.remove(jobSearchId)
        logger.debug("Cleared rate limit for job search: $jobSearchId")
    }

    /**
     * Cleanup old entries to prevent memory leaks
     * Removes entries older than 2x the rate limit window
     */
    private fun cleanupOldEntries() {
        val now = Instant.now()
        val cutoff = now.minus(RATE_LIMIT_WINDOW.multipliedBy(2))
        var removedCount = 0

        triggerHistory.entries.removeIf { (_, lastTrigger) ->
            if (lastTrigger.isBefore(cutoff)) {
                removedCount++
                true
            } else {
                false
            }
        }

        if (removedCount > 0) {
            logger.debug("Cleaned up $removedCount old trigger entries")
        }
    }
}
