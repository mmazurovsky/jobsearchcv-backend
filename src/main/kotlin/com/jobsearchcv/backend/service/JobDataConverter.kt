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
     * Converts ProcessedJobData to ScoredJobData.
     */
    fun toScoredJobData(
        processedJob: ProcessedJobData,
        createdAgo: String,
        scrapedAt: OffsetDateTime,
        userId: String,
        jobSearchId: String,
        keywords: String,
        compatibilityScore: Int,
        filterReason: String?
    ): ScoredJobData {
        return ScoredJobData(
            id = processedJob.id,
            internalId = processedJob.internalId,
            title = processedJob.title,
            company = processedJob.company,
            location = processedJob.location,
            link = processedJob.link,
            createdAgo = createdAgo,
            description = processedJob.description,
            applicants = processedJob.applicants,
            scrapedAt = scrapedAt,
            userId = userId,
            jobSearchId = jobSearchId,
            keywords = keywords,
            techstack = processedJob.techstack,
            tags = processedJob.tags,
            salary = processedJob.salary,
            compatibilityScore = compatibilityScore,
            filterReason = filterReason,
        )
    }
}
