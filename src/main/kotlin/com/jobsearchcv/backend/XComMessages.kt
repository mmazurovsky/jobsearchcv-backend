package com.jobsearchcv.backend

import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComJobData

/**
 * Utility class for formatting job messages for X.com posting
 * Handles 280 character limit and message formatting
 */
object XComMessages {

    private const val MAX_TWEET_LENGTH = 280
    private const val JOB_LINK_BASE = "t.me/sixfigs_bot?start="

    /**
     * Formats a job for X.com posting with 280 character limit
     * Truncates techstack if necessary to fit within limit
     */
    fun formatJobMessage(job: XComJobData): String {
        // Build message without techstack and link first
        val baseMessageWithoutLink = buildString {
            append("💼 ${job.title} at ${job.company}")
            append("\n📍 ${job.location}")
            if (job.salary != null) {
                append("\n💰 ${job.salary}")
            }
        }

        val linkPart = "\n🔗 ${job.internalJobLink}"

        // Calculate available space for techstack
        val availableSpace =
            MAX_TWEET_LENGTH - baseMessageWithoutLink.length - linkPart.length - "\n🛠️ ".length

        if (job.techstack.isEmpty()) {
            return baseMessageWithoutLink + linkPart
        }

        val truncatedTechstack = truncateTechstack(job.techstack, availableSpace)

        return if (truncatedTechstack.isNotEmpty()) {
            "$baseMessageWithoutLink\n🛠️ $truncatedTechstack$linkPart"
        } else {
            baseMessageWithoutLink + linkPart
        }
    }

    /**
     * Truncates techstack to fit within the available character space
     * Removes technologies from the end until it fits, but only at comma boundaries
     */
    private fun truncateTechstack(techstack: List<String>, availableSpace: Int): String {
        if (techstack.isEmpty()) return ""

        val joined = techstack.joinToString(",") {
            "#$it"
        }

        if (joined.length <= availableSpace) {
            return joined
        }

        // Find the last comma that allows the string to fit
        var result = ""
        var currentLength = 0

        for (tech in techstack) {
            val addition = if (result.isEmpty()) tech else ", $tech"

            if (currentLength + addition.length <= availableSpace) {
                result += addition
                currentLength += addition.length
            } else {
                break
            }
        }

        return result
    }

    /**
     * Generates the job link for X.com posts using internal ID
     */
    fun generateJobLink(internalId: String): String {
        return "$JOB_LINK_BASE$internalId"
    }

    /**
     * Converts ScoredJobData to XComJobData for X.com posting
     * Excludes compatibility scores and uses custom job link
     */
    fun toXComJobData(
        scoredJob: ScoredJobData,
        internalId: String
    ): XComJobData {
        return XComJobData(
            internalId = internalId,
            title = scoredJob.title,
            company = scoredJob.company,
            location = scoredJob.location,
            techstack = scoredJob.techstack,
            salary = scoredJob.salary,
            internalJobLink = generateJobLink(internalId)
        )
    }
}