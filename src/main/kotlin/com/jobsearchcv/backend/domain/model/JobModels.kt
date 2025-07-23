package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.jobsearchcv.backend.TelegramMessages
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.OffsetDateTime

data class JobSearchIn(
    val jobTitle: String,
    val location: String,
    val jobTypes: List<JobType>,
    val remoteTypes: List<RemoteType>,
    val timePeriod: TimePeriod,
    val filterText: String? = null,
) {
    fun toHumanReadableString(): String {
        return TelegramMessages.getJobSearchDetails(this)
    }
}

@Document(collection = "job_searches")
data class JobSearchOut(
    @Id val id: String,
    @field:Field("job_title") val jobTitle: String,
    val location: String,
    @field:Field("job_types") val jobTypes: List<JobType> = emptyList(),
    @field:Field("remote_types") val remoteTypes: List<RemoteType> = emptyList(),
    @field:Field("time_period") val timePeriod: TimePeriod,
    @Indexed(unique = false) @field:Field("user_id") val userId: String,
    @Indexed(unique = false) @field:Field("created_at") val createdAt: OffsetDateTime,
    @field:Field("filter_text") val filterText: String? = null,
    @field:Field("destination") val destination: String? = null
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
         * Creates a persistent JobSearchOut from JobSearchIn with a new UUID
         */
        fun fromJobSearchIn(input: JobSearchIn, userId: String): JobSearchOut {
            return JobSearchOut(
                id = java.util.UUID.randomUUID().toString(),
                jobTitle = input.jobTitle,
                location = input.location,
                jobTypes = input.jobTypes,
                remoteTypes = input.remoteTypes,
                timePeriod = input.timePeriod,
                userId = userId,
                filterText = input.filterText,
                createdAt = OffsetDateTime.now(),
            )
        }

        /**
         * Creates a temporary JobSearchOut from JobSearchIn with a temporary ID prefix
         */
        fun fromJobSearchInAsTemp(input: JobSearchIn, userId: String): JobSearchOut {
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
            )
        }
    }
}

@Document(collection = "sent_jobs")
@CompoundIndexes(
    CompoundIndex(name = "user_job_idx", def = "{'user_id': 1, 'job_url': 1}")
)
data class SentJobOut(
    @Indexed(unique = false) @field:Field("user_id") val userId: String,
    @Indexed(unique = false) @field:Field("destination") val destination: String? = null,
    @Indexed(unique = false) @field:Field("job_url") val jobUrl: String,
    @Indexed(unique = false) @field:Field("sent_at") val sentAt: OffsetDateTime = OffsetDateTime.now(),
)

data class SearchJobsParams(
    val keywords: String,
    val location: String,
    @JsonProperty("time_period") val timePeriod: String,
    @JsonProperty("job_types") val jobTypes: List<String> = emptyList(),
    @JsonProperty("remote_types") val remoteTypes: List<String> = emptyList(),
    @JsonProperty("filter_text") val filterText: String? = null,
    @JsonProperty("callback_url") val callbackUrl: String,
    @JsonProperty("job_search_id") val jobSearchId: String? = null,
//    TODO: type should be string
    @JsonProperty("user_id") val userId: String? = null
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
