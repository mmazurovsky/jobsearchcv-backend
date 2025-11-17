package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime

/**
 * Raw job data received from scraper via callback.
 * This is NOT stored in MongoDB - only used for processing.
 */
@Schema(description = "Raw job data from scraper")
data class ScrapedJobData(
    @Schema(description = "Unique job identifier", required = true)
    val id: String,
    @Schema(description = "Job title", example = "Senior Software Engineer", required = true)
    val title: String,
    @Schema(description = "Company name", example = "Google", required = true)
    val company: String,
    @Schema(description = "Job location", example = "Mountain View, CA", required = true)
    val location: String,
    @Schema(description = "Job posting URL", required = true)
    val link: String,
    @Schema(description = "Time since job was posted", example = "2 days ago", required = true)
    val createdAgo: String,
    @Schema(description = "Job description text", required = true)
    val description: String,
    @Schema(description = "Number of applicants", example = "50+", required = true)
    val applicants: String,
    @Schema(description = "Timestamp when job was scraped", required = true)
    val scrapedAt: OffsetDateTime,
    @Schema(description = "User ID who triggered the search", required = true)
    val userId: String,
    @Schema(description = "Job search ID that found this job", required = true)
    val jobSearchId: String,
    @Schema(description = "Search keywords used", required = true)
    val keywords: String
)

/**
 * Job data after translation step.
 * Contains scraped data with translated title and description.
 */
data class TranslatedJobData(
    val id: String,
    val title: String,           // Translated to English
    val company: String,
    val location: String,
    val link: String,
    val createdAgo: String,
    val description: String,     // Translated to English
    val applicants: String,
    val scrapedAt: OffsetDateTime,
    val userId: String,
    val jobSearchId: String,
    val keywords: String
)

/**
 * Job data after enrichment step.
 * Contains translated data plus LLM-extracted techstack and salary.
 */
data class EnrichedJobData(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val link: String,
    val createdAgo: String,
    val description: String,
    val applicants: String,
    val scrapedAt: OffsetDateTime,
    val userId: String,
    val jobSearchId: String,
    val keywords: String,
    
    // LLM enrichment results
    val techstack: List<String>,
    val tags: List<String> = emptyList(),
    val salary: String?
)

/**
 * Job data after compatibility scoring step.
 * Contains enriched data plus compatibility score and filter reason.
 * This represents the final result before storage.
 */
data class ScoredJobData(
    val id: String,
    val internalId: String,
    val title: String,
    val company: String,
    val location: String,
    val link: String,
    val createdAgo: String,
    val description: String,
    val applicants: String,
    val scrapedAt: OffsetDateTime,
    val userId: String,
    val jobSearchId: String,
    val keywords: String,
    
    // LLM enrichment results
    val techstack: List<String>,
    val tags: List<String> = emptyList(),
    val salary: String?,
    
    // LLM scoring results
    val compatibilityScore: Int,
    val filterReason: String?
)

@Document(collection = "processed_jobs")
data class ProcessedJobData(
    @Id @field:Field("_id") val id: String,
//    TODO: might be not needed internal id
    @field:Field("internal_id") val internalId: String,
    val title: String,
    val company: String,
    val location: String,
    @Indexed val link: String,
    val description: String,
    val applicants: String,
    // LLM processing results
    @field:Field("techstack") val techstack: List<String>,
    @field:Field("tags") val tags: List<String> = emptyList(),
    val salary: String?,
    @field:Field("processed_at") val processedAt: OffsetDateTime
)

/**
 * Callback request model for receiving full job data from scraper.
 * Replaces the old approach of sending just job IDs.
 */
@Schema(description = "Request containing scraped job data from external scraper")
data class JobDataCallbackRequest(
    @Schema(description = "Job search ID that triggered the scraping", required = true)
    val jobSearchId: String,
    @Schema(description = "User ID who owns the job search", required = true)
    val userId: String,
    @Schema(description = "List of scraped job data", required = true)
    val jobs: List<ScrapedJobData>,
    @param:Schema(description = "Optional search name for special searches like monthly overview", required = false)
    val searchName: String? = null
)

@Schema(description = "Response for job data callback")
data class JobDataCallbackResponse(
    @Schema(description = "Processing status", example = "received", required = true)
    val status: String,
    @Schema(description = "Response message", example = "Job data processing started", required = true)
    val message: String,
    @JsonProperty("received_count") 
    @Schema(description = "Number of jobs received", example = "25", required = true)
    val receivedCount: Int
)
