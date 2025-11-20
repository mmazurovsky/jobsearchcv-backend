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
     * Priority: Default hashtags + Comment instruction (MANDATORY) > Base message > Techstack hashtags
     * Truncates base message or techstack if necessary to fit within limit
     */
    fun formatJobMessage(job: XComJobData): String {
        // Select 2 random default hashtags (MANDATORY - highest priority)
        val defaultHashtags = selectDefaultHashtags()
        val defaultHashtagsStr = defaultHashtags.joinToString(" ")

        // Build mandatory parts that must ALWAYS be included
        val shortId = job.internalId.take(8)
        val linkPart = "\n💬 Like and comment \"Interested\" to get details"
        val mandatoryHashtagPart = "\n🛠️ $defaultHashtagsStr"
        val mandatoryLength = linkPart.length + mandatoryHashtagPart.length

        // Calculate maximum space available for base message
        val maxBaseMessageLength = MAX_TWEET_LENGTH - mandatoryLength

        // Build base message, truncating if necessary
        val baseMessage = buildTruncatedBaseMessage(job, maxBaseMessageLength)

        // Calculate remaining space for techstack hashtags
        val remainingSpace = MAX_TWEET_LENGTH - baseMessage.length - mandatoryHashtagPart.length - linkPart.length - 1 // -1 for space

        // Add techstack hashtags only if space remains
        val techstackHashtags = if (remainingSpace > 0) {
            truncateTechstack(job.techstack, remainingSpace)
        } else {
            ""
        }

        // Build final hashtag section
        val hashtagSection = if (techstackHashtags.isNotEmpty()) {
            "\n🛠️ $defaultHashtagsStr $techstackHashtags"
        } else {
            mandatoryHashtagPart
        }

        // Assemble final message
        val finalMessage = "$baseMessage$hashtagSection$linkPart"

        // Validate length (should never exceed 280)
        require(finalMessage.length <= MAX_TWEET_LENGTH) {
            "Tweet exceeds $MAX_TWEET_LENGTH characters: ${finalMessage.length}"
        }

        return finalMessage
    }

    /**
     * Builds base message (title, company, location, salary) with truncation if needed
     * Truncates company name and/or title to fit within maxLength
     */
    private fun buildTruncatedBaseMessage(job: XComJobData, maxLength: Int): String {
        // Try to build full message first
        val fullMessage = buildString {
            append("💼 ${job.title} at ${job.company}")
            append("\n📍 ${job.location}")
            if (job.salary != null) {
                append("\n💰 ${job.salary}")
            }
        }

        if (fullMessage.length <= maxLength) {
            return fullMessage
        }

        // Need to truncate - calculate fixed parts
        val locationPart = "\n📍 ${job.location}"
        val salaryPart = if (job.salary != null) "\n💰 ${job.salary}" else ""
        val fixedPartsLength = "💼  at ".length + locationPart.length + salaryPart.length

        val availableForTitleAndCompany = maxLength - fixedPartsLength

        if (availableForTitleAndCompany <= 10) {
            // Not enough space even with truncation - return minimal message
            return "💼 Job$locationPart$salaryPart"
        }

        // Try to fit both title and company with truncation
        val titleLength = job.title.length
        val companyLength = job.company.length

        return when {
            titleLength + companyLength <= availableForTitleAndCompany -> {
                // Both fit
                "💼 ${job.title} at ${job.company}$locationPart$salaryPart"
            }
            titleLength <= availableForTitleAndCompany - 7 -> {
                // Title fits, truncate company (need at least "..." = 3 chars)
                val availableForCompany = availableForTitleAndCompany - titleLength
                val truncatedCompany = if (companyLength > availableForCompany) {
                    job.company.take(availableForCompany - 3) + "..."
                } else {
                    job.company
                }
                "💼 ${job.title} at $truncatedCompany$locationPart$salaryPart"
            }
            else -> {
                // Both need truncation - prioritize title
                val titleSpace = (availableForTitleAndCompany * 0.6).toInt()
                val companySpace = availableForTitleAndCompany - titleSpace

                val truncatedTitle = if (titleLength > titleSpace) {
                    job.title.take(titleSpace - 3) + "..."
                } else {
                    job.title
                }
                val truncatedCompany = if (companyLength > companySpace) {
                    job.company.take(companySpace - 3) + "..."
                } else {
                    job.company
                }
                "💼 $truncatedTitle at $truncatedCompany$locationPart$salaryPart"
            }
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