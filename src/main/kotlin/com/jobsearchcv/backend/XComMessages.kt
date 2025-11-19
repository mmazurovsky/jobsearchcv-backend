package com.jobsearchcv.backend

import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.XComJobData

/**
 * Utility class for formatting job messages for X.com posting
 * Handles 280 character limit and message formatting
 */
object XComMessages {

    private const val MAX_TWEET_LENGTH = 280

    // Default hashtags pool - 2 will be randomly selected for each tweet
    private val DEFAULT_HASHTAGS = listOf(
        "#techjobs", "#itjobs", "#hiring", "#remotework",
        "#remotejobs", "#careers", "#jobmarket", "#techhiring"
    )

    /**
     * Selects 2 random unique hashtags from the default pool
     */
    private fun selectDefaultHashtags(): List<String> {
        return DEFAULT_HASHTAGS.shuffled().take(2)
    }

    /**
     * Formats a job for X.com posting with 280 character limit
     * Includes 2 random default hashtags before techstack hashtags
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

        // Select 2 random default hashtags
        val defaultHashtags = selectDefaultHashtags()
        val defaultHashtagsStr = defaultHashtags.joinToString(" ")

        // Calculate available space for techstack (after default hashtags)
        val hashtagSectionPrefix = "\n🛠️ $defaultHashtagsStr"
        val availableSpace =
            MAX_TWEET_LENGTH - baseMessageWithoutLink.length - hashtagSectionPrefix.length - linkPart.length - 1 // -1 for space before techstack

        // Build hashtag section with default hashtags first
        val techstackHashtags = truncateTechstack(job.techstack, availableSpace)

        val hashtagSection = if (techstackHashtags.isNotEmpty()) {
            "\n🛠️ $defaultHashtagsStr $techstackHashtags"
        } else {
            "\n🛠️ $defaultHashtagsStr"
        }

        return "$baseMessageWithoutLink$hashtagSection$linkPart"
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