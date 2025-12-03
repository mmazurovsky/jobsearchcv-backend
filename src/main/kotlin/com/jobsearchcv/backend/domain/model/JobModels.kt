package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.jobsearchcv.backend.TelegramMessages
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.OffsetDateTime

@Schema(description = "Job search input configuration")
data class JobSearchIn(
    @Schema(description = "Unique identifier for the job search", example = "search-123", required = true)
    val id: String,
    @Schema(description = "Job title to search for", example = "Senior Software Engineer", required = true)
    val jobTitle: String,
    @Schema(description = "Location for job search", example = "San Francisco, CA", required = true)
    val location: String,
    @Schema(description = "Types of job positions to include", required = true)
    val jobTypes: List<JobType>,
    @Schema(description = "Remote work preferences", required = true)
    val remoteTypes: List<RemoteType>,
    @Schema(description = "Search frequency and timing", required = true)
    val timePeriod: TimePeriod,
    @Schema(description = "Additional filter text for job descriptions", example = "Spring Boot", required = false)
    val filterText: String? = null,
) {
    fun toHumanReadableString(): String {
        return TelegramMessages.getJobSearchDetails(this)
    }
}

@Document(collection = "job_searches")
@Schema(description = "Saved job search configuration with metadata")
data class JobSearchOut(
    @Id 
    @Schema(description = "Unique identifier for the job search", example = "search-123e4567-e89b-12d3-a456-426614174000", required = true)
    val id: String,
    @field:Field("job_title") 
    @Schema(description = "Job title to search for", example = "Senior Software Engineer", required = true)
    val jobTitle: String,
    @Schema(description = "Location for job search", example = "San Francisco, CA", required = true)
    val location: String,
    @field:Field("job_types") 
    @Schema(description = "Types of job positions to include", required = true)
    val jobTypes: List<JobType> = emptyList(),
    @field:Field("remote_types") 
    @Schema(description = "Remote work preferences", required = true)
    val remoteTypes: List<RemoteType> = emptyList(),
    @field:Field("time_period") 
    @Schema(description = "Search frequency and timing", required = true)
    val timePeriod: TimePeriod,
    @Indexed(unique = false) @field:Field("user_id") 
    @Schema(description = "ID of the user who owns this job search", required = true)
    val userId: String,
    @Indexed(unique = false) @field:Field("created_at") 
    @Schema(description = "Timestamp when the job search was created", required = true)
    val createdAt: OffsetDateTime,
    @field:Field("filter_text") 
    @Schema(description = "Additional filter text for job descriptions", example = "Spring Boot", required = false)
    val filterText: String? = null,
    @Indexed(unique = false) @field:Field("is_approved")
    @Schema(description = "Whether the job search is approved for automated scheduling", required = true)
    val isApproved: Boolean = false,
    @field:Field("destination")
    @Schema(description = "Destination channel for sending job results (e.g., 'xcom_us_tech', 'email', 'telegram')", required = false)
    val destination: String? = null,
    @Indexed(unique = false) @field:Field("is_subscribed")
    @Schema(description = "Whether user is subscribed to receive email notifications for this job search", required = true)
    val isSubscribed: Boolean = true,
    @field:Field("is_admin")
    @Schema(description = "Whether this is an admin job search that bypasses premium checks", required = false)
    val isAdmin: Boolean? = null,
    @Indexed(unique = false) @field:Field("prompt_id")
    @Schema(description = "ID of the prompt that was used to create this job search (null for backward compatibility)", required = false)
    val promptId: String? = null
) {
    fun toLogString(): String {
        return "id=$id, title=$jobTitle, location=$location, " +
                "job_types=${jobTypes.map { it.label }}, " +
                "remote_types=${remoteTypes.map { it.label }}, " +
                "time_period=${timePeriod.displayName}"
    }

    fun toMessage(): String {
        return buildString {
            appendLine("🆔 Alert ID: $id")
            appendLine("📝 Job Title: $jobTitle")
            appendLine("📍 Location: $location")
            appendLine("💼 Job Types: ${jobTypes.joinToString(", ") { it.label }}")
            appendLine("🏠 Remote Types: ${remoteTypes.joinToString(", ") { it.label }}")
            appendLine("🔍 Filter Text: ${filterText ?: "None"}")
            appendLine("⏰ Frequency: ${timePeriod.displayName}")
        }
    }

    companion object {
        /**
         * Creates a persistent JobSearchOut from JobSearchIn with a new UUID and destination
         * Replaces temporary client-side IDs (starting with "temp-") with proper UUIDs
         */
        fun fromJobSearchIn(input: JobSearchIn, userId: String, destinationId: String? = null, isApproved: Boolean = false, isAdmin: Boolean? = null, promptId: String? = null): JobSearchOut {
            return JobSearchOut(
                id = if (input.id.startsWith("temp-")) {
                    java.util.UUID.randomUUID().toString()
                } else {
                    input.id
                },
                jobTitle = input.jobTitle,
                location = input.location,
                jobTypes = input.jobTypes,
                remoteTypes = input.remoteTypes,
                timePeriod = input.timePeriod,
                userId = userId,
                filterText = input.filterText,
                createdAt = OffsetDateTime.now(),
                destination = destinationId,
                isApproved = isApproved,
                isSubscribed = true,
                isAdmin = isAdmin,
                promptId = promptId
            )
        }

        /**
         * Creates a temporary JobSearchOut from JobSearchIn with a temporary ID prefix
         */
        fun fromJobSearchInAsTemp(input: JobSearchIn, userId: String, destinationId: String? = null, isAdmin: Boolean? = null, promptId: String? = null): JobSearchOut {
            return JobSearchOut(
                id = "temp-${java.util.UUID.randomUUID()}",
                jobTitle = input.jobTitle,
                location = input.location,
                jobTypes = input.jobTypes,
                remoteTypes = input.remoteTypes,
                timePeriod = input.timePeriod,
                userId = userId,
                filterText = input.filterText,
                createdAt = OffsetDateTime.now(),
                destination = destinationId,
                isApproved = false,
                isSubscribed = true,
                isAdmin = isAdmin,
                promptId = promptId
            )
        }
    }
}

@Document(collection = "sent_jobs")
@CompoundIndexes(
    CompoundIndex(name = "user_job_idx", def = "{'user_id': 1, 'job_url': 1}"),
    CompoundIndex(name = "user_sent_at_idx", def = "{'user_id': 1, 'sent_at': -1}")
)
data class SentJobOut(
    @Indexed(unique = false) @field:Field("user_id") val userId: String,
    @Indexed(unique = false) @field:Field("destination") val destination: String? = null,
    @Indexed(unique = false) @field:Field("job_url") val jobUrl: String,
    @Indexed(unique = false) @field:Field("sent_at") val sentAt: OffsetDateTime = OffsetDateTime.now(),
    @Indexed(unique = false) @field:Field("internal_id") val internalId: String? = null,
)

data class SearchJobsParams(
    val keywords: String,
    val location: String,
    @JsonProperty("time_period") val timePeriod: String,
    @JsonProperty("job_types") val jobTypes: List<String> = emptyList(),
    @param:JsonProperty("remote_types") val remoteTypes: List<String> = emptyList(),
    @JsonProperty("filter_text") val filterText: String? = null,
    @JsonProperty("callback_url") val callbackUrl: String,
    @JsonProperty("job_search_id") val jobSearchId: String? = null,
    @JsonProperty("user_id") val userId: String? = null,
    @JsonProperty("search_name") val searchName: String? = null
)

/**
 * Job data specifically formatted for X.com posting
 * Contains only the necessary fields for creating X.com posts without compatibility scores
 */
data class XComJobData(
    val internalId: String,
    val title: String,
    val company: String,
    val location: String,
    val techstack: List<String>,
    val salary: String?,
    val internalJobLink: String
)

/**
 * Public job response for viewing job details via public API
 * Used when users click X.com tweet links
 */
data class PublicJobResponse(
    val internalId: String,
    val title: String,
    val company: String,
    val location: String,
    val link: String,
    val techstack: List<String>,
    val tags: List<String>,
    val salary: String?,
    val processedAt: java.time.OffsetDateTime
)

/**
 * Response model for page jobs endpoint
 * Excludes link (external URL) and _id (MongoDB internal ID)
 */
data class PageJobResponse(
    val internalId: String,
    val title: String,
    val company: String,
    val location: String,
    val techstack: List<String>,
    val tags: List<String>,
    val salary: String?,
    val processedAt: java.time.OffsetDateTime,
)
