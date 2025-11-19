package com.jobsearchcv.backend

import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComJobData

/**
 * Utility class for formatting job messages for X.com posting
 * Handles 280 character limit and message formatting
 */
object XComMessages {

    private const val MAX_TWEET_LENGTH = 280

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

        // Convert all technologies to hashtag format
        val hashtags = techstack.map { toHashtag(it) }

        val joined = hashtags.joinToString(" ")

        if (joined.length <= availableSpace) {
            return joined
        }

        // Find the last hashtag that allows the string to fit
        var result = ""
        var currentLength = 0

        for (hashtag in hashtags) {
            val addition = if (result.isEmpty()) hashtag else " $hashtag"

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
     * Converts a technology name to a hashtag format
     * e.g., "Spring Boot" -> "#springboot", "C#" -> "#csharp"
     */
    private fun toHashtag(tech: String): String {
        val normalized = tech
            .lowercase()
            .replace("#", "sharp")
            .replace("+", "plus")
            .replace(Regex("[^a-z0-9]"), "") // Remove spaces, special chars
        return "#$normalized"
    }
}