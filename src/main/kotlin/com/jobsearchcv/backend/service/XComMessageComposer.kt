package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.XComMessages
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComJobData
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Service for composing X.com messages from job data
 * Delegates to XComMessages for formatting logic
 */
@Service
class XComMessageComposer(
    private val urlService: UrlService
) {

    /**
     * Converts ScoredJobData to XComJobData
     */
    fun createXComJobData(job: ScoredJobData): XComJobData {
        val internalJobLink = buildInternalJobLink(job.internalId)

        return XComJobData(
            internalId = job.internalId,
            title = job.title,
            company = job.company,
            location = job.location,
            techstack = job.techstack,
            salary = job.salary,
            internalJobLink = internalJobLink
        )
    }

    /**
     * Formats a job into a tweet text within 280 character limit
     * Uses XComMessages for formatting with emojis and smart truncation
     */
    fun formatTweet(job: XComJobData): String {
        return XComMessages.formatJobMessage(job)
    }

    /**
     * Builds internal job link for ApplyFirst platform
     * Format: {websiteUrl}/jobs/{jobId}
     */
    private fun buildInternalJobLink(jobId: String): String {
        val websiteUrl = urlService.getWebsiteUrl()
        val encodedJobId = URLEncoder.encode(jobId, StandardCharsets.UTF_8)
        return "$websiteUrl/jobs/$encodedJobId"
    }
}
