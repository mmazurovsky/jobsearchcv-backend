package com.jobsearchcv.backend.config

import io.sentry.Sentry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class SentryTestController {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SentryTestController::class.java)
    }

    @GetMapping("/sentry-error")
    fun testSentryError(): Map<String, String> {
        try {
            logger.warn("Testing Sentry integration - this is a warning")
            throw RuntimeException("Test exception for Sentry integration")
        } catch (e: Exception) {
            logger.error("Test error captured for Sentry", e)
            Sentry.captureException(e)
            return mapOf("status" to "error captured", "message" to "Check Sentry dashboard")
        }
    }

    @GetMapping("/sentry-warning")
    fun testSentryWarning(): Map<String, String> {
        logger.warn("This is a test warning that should appear in Sentry")
        return mapOf("status" to "warning sent", "message" to "Check Sentry dashboard for warning")
    }
}