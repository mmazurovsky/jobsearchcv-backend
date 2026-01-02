package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.XComMessages
import com.jobsearchcv.backend.domain.model.JobSearchOut
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
     * Formats a page overview message for batch job posting with hashtags
     * @param jobCount Number of jobs in the batch
     * @param jobSearch Job search configuration with title, location, timePeriod
     * @param pagePath Page path from destination (e.g., "us-tech-jobs")
     * @param hashtags Optional list of hashtags to include (3 random will be selected)
     * @return Formatted tweet text within 280 character limit
     */
    fun formatPageOverviewTweet(
        jobCount: Int,
        jobSearch: JobSearchOut,
        pagePath: String,
        hashtags: List<String>? = null
    ): String {
        val websiteUrl = urlService.getWebsiteUrl()
        val pageUrl = "$websiteUrl/job-search/$pagePath"
        val jobWord = if (jobCount == 1) "job" else "jobs"

        // Select 3 random hashtags if available
        val selectedHashtags = selectRandomHashtags(hashtags, 3)

        // Build base message using StringBuilder
        val baseMessage = StringBuilder().apply {
            append("Found $jobCount new ${jobSearch.jobTitle} $jobWord in ${jobSearch.location} published on Linkedin in the last ${jobSearch.timePeriod.displayName}. ")
            append("\nBe early to apply!\n")
            append("🔗 $pageUrl")
        }.toString()

        // Try with hashtags first
        if (selectedHashtags.isNotEmpty()) {
            val messageWithHashtags = StringBuilder(baseMessage).apply {
                append("\n${selectedHashtags.joinToString(" ") { "#$it" }}")
            }.toString()

            if (messageWithHashtags.length <= 280) {
                return messageWithHashtags
            }
        }

        // Try without hashtags
        if (baseMessage.length <= 280) {
            return baseMessage
        }

        // Truncate base message if still too long
        val suffix = "Be early to apply!\n🔗 $pageUrl"
        val availableSpace = 280 - suffix.length - 3 // -3 for "... "
        val prefix = baseMessage.substringBefore("Be early to apply!")

        return StringBuilder().apply {
            append("${prefix.take(availableSpace).trim()}... ")
            append(suffix)
        }.toString()
    }

    /**
     * Selects N random hashtags from the list
     * @param hashtags List of available hashtags (can be null or empty)
     * @param count Number of hashtags to select
     * @return List of randomly selected hashtags (empty if input is null/empty)
     */
    private fun selectRandomHashtags(hashtags: List<String>?, count: Int): List<String> {
        if (hashtags.isNullOrEmpty()) return emptyList()

        return if (hashtags.size <= count) {
            hashtags.shuffled()
        } else {
            hashtags.shuffled().take(count)
        }
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
