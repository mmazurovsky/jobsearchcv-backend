package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Service for converting between different job data models.
 */
@Service
class JobDataConverter {

    fun enrichedToProcessedJobData(enrichedJob: EnrichedJobData): ProcessedJobData {
        return ProcessedJobData(
            id = enrichedJob.id,
            internalId = UUID.randomUUID().toString(),
            title = enrichedJob.title,
            company = enrichedJob.company,
            location = enrichedJob.location,
            link = enrichedJob.link,
            description = enrichedJob.description,
            applicants = enrichedJob.applicants,
            techstack = enrichedJob.techstack,
            tags = enrichedJob.tags,
            salary = enrichedJob.salary,
            processedAt = OffsetDateTime.now(),
        )
    }

    // ========================================================================
    // INTERMEDIATE STEP CONVERSIONS
    // ========================================================================

    /**
     * Converts ScrapedJobData to TranslatedJobData.
     */
    fun toTranslatedJobData(
        scrapedJob: ScrapedJobData,
        translatedTitle: String,
        translatedDescription: String
    ): TranslatedJobData {
        return TranslatedJobData(
            id = scrapedJob.id,
            title = translatedTitle,
            company = scrapedJob.company,
            location = scrapedJob.location,
            link = scrapedJob.link,
            createdAgo = scrapedJob.createdAgo,
            description = translatedDescription,
            applicants = scrapedJob.applicants,
            scrapedAt = scrapedJob.scrapedAt,
            userId = scrapedJob.userId,
            jobSearchId = scrapedJob.jobSearchId,
            keywords = scrapedJob.keywords
        )
    }

    /**
     * Converts TranslatedJobData to EnrichedJobData.
     */
    fun toEnrichedJobData(
        translatedJob: TranslatedJobData,
        techstack: List<String>,
        tags: List<String>,
        salary: String?
    ): EnrichedJobData {
        return EnrichedJobData(
            id = translatedJob.id,
            title = translatedJob.title,
            company = translatedJob.company,
            location = translatedJob.location,
            link = translatedJob.link,
            createdAgo = translatedJob.createdAgo,
            description = translatedJob.description,
            applicants = translatedJob.applicants,
            scrapedAt = translatedJob.scrapedAt,
            userId = translatedJob.userId,
            jobSearchId = translatedJob.jobSearchId,
            keywords = translatedJob.keywords,
            techstack = techstack,
            tags = tags,
            salary = salary,
        )
    }

    /**
     * Converts EnrichedJobData to ScoredJobData.
     */
    fun toScoredJobData(
        enrichedJob: EnrichedJobData,
        compatibilityScore: Int,
        filterReason: String?
    ): ScoredJobData {
        return ScoredJobData(
            id = enrichedJob.id,
            title = enrichedJob.title,
            company = enrichedJob.company,
            location = enrichedJob.location,
            link = enrichedJob.link,
            createdAgo = enrichedJob.createdAgo,
            description = enrichedJob.description,
            applicants = enrichedJob.applicants,
            scrapedAt = enrichedJob.scrapedAt,
            userId = enrichedJob.userId,
            jobSearchId = enrichedJob.jobSearchId,
            keywords = enrichedJob.keywords,
            techstack = enrichedJob.techstack,
            tags = enrichedJob.tags,
            salary = enrichedJob.salary,
            compatibilityScore = compatibilityScore,
            filterReason = filterReason,
        )
    }
}
