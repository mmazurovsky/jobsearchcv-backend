package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime

/**
 * Raw job data received from scraper via callback.
 * This is NOT stored in MongoDB - only used for processing.
 */
data class ScrapedJobData(
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
    val salary: String?
)

/**
 * Job data after compatibility scoring step.
 * Contains enriched data plus compatibility score and filter reason.
 * This represents the final result before storage.
 */
data class ScoredJobData(
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
    val salary: String?,
    @field:Field("processed_at") val processedAt: OffsetDateTime
)

/**
 * Callback request model for receiving full job data from scraper.
 * Replaces the old approach of sending just job IDs.
 */
data class JobDataCallbackRequest(
    val jobSearchId: String,
    val userId: String,
    val jobs: List<ScrapedJobData>
)

data class JobDataCallbackResponse(
    val status: String,
    val message: String,
    @JsonProperty("received_count") val receivedCount: Int
)