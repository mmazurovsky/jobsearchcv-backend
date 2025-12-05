package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for applying deterministic pre-filtering to jobs before LLM scoring
 * Implements moderate strictness: filters jobs that don't have required primary technologies
 *
 * Edge case handling philosophy: When in doubt, PASS to LLM (avoid false negatives)
 */
@Service
class DeterministicJobFilterService(
    private val technologyExtractorService: TechnologyExtractorService,
    private val technologyMatchingService: TechnologyMatchingService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Applies pre-filter to jobs before LLM scoring
     * Returns jobs that passed and jobs that were filtered
     */
    fun applyPreFilter(
        enrichedJobs: List<EnrichedJobData>,
        jobSearch: JobSearchOut
    ): PreFilterResult {
        logger.info(
            "[JobSearch: {}] Applying deterministic pre-filter to {} jobs",
            jobSearch.id,
            enrichedJobs.size
        )

        // Extract required technologies from search criteria
        val requiredTechs = technologyExtractorService.extractRequiredTechnologies(jobSearch)

        logger.info(
            "[JobSearch: {}] Required technologies - Primary: {}, Secondary: {}",
            jobSearch.id,
            requiredTechs.primary.map { it.name },
            requiredTechs.secondary.map { it.name }
        )

        // EDGE CASE 1: No technologies extracted from search → PASS ALL JOBS
        if (requiredTechs.primary.isEmpty() && requiredTechs.secondary.isEmpty()) {
            logger.info(
                "[JobSearch: {}] No technology requirements found, passing all {} jobs to LLM",
                jobSearch.id,
                enrichedJobs.size
            )
            val passedJobs = enrichedJobs.map { PreFilteredJob(it, null, null) }
            return PreFilterResult(
                passedJobs = passedJobs,
                filteredJobs = emptyList(),
                passedCount = passedJobs.size,
                filteredCount = 0
            )
        }

        val passedJobs = mutableListOf<PreFilteredJob>()
        val filteredJobs = mutableListOf<PreFilteredJob>()

        enrichedJobs.forEach { job ->
            // Extract technologies from job
            val jobTechs = technologyExtractorService.extractJobTechnologies(job)

            // EDGE CASE 2: Job has NO matched technologies (all unknown) → PASS
            // Let LLM decide - might be emerging tech or industry-specific terms
            if (jobTechs.primary.isEmpty() && jobTechs.secondary.isEmpty()) {
                logger.debug(
                    "[JobSearch: {}] Job {} has no matched techs (unknown/emerging tech), passing to LLM",
                    jobSearch.id,
                    job.id
                )
                passedJobs.add(PreFilteredJob(job, null, null))
                return@forEach
            }

            // Calculate technology match
            val matchResult = technologyMatchingService.calculateTechnologyMatch(
                required = requiredTechs,
                available = jobTechs
            )

            // Apply moderate strictness filter
            if (matchResult.passesModerateFilter) {
                // Job passes pre-filter
                passedJobs.add(
                    PreFilteredJob(
                        job = job,
                        matchResult = matchResult,
                        filterReason = null
                    )
                )
                logger.debug(
                    "[JobSearch: {}] Job '{}' PASSED pre-filter (primary score: {}, matches: {})",
                    jobSearch.id,
                    job.title,
                    String.format("%.2f", matchResult.primaryScore),
                    matchResult.primaryMatches
                        .filter { it.similarity >= 0.70 }
                        .map { "${it.requiredTech.name}→${it.matchedTech?.name}(${String.format("%.2f", it.similarity)})" }
                )
            } else {
                // Job filtered out
                val reason = buildFilterReason(matchResult, requiredTechs, jobTechs)
                filteredJobs.add(
                    PreFilteredJob(
                        job = job,
                        matchResult = matchResult,
                        filterReason = reason
                    )
                )
                logger.debug(
                    "[JobSearch: {}] Job '{}' FILTERED - Reason: {}",
                    jobSearch.id,
                    job.title,
                    reason
                )
            }
        }

        val passRate = if (enrichedJobs.isNotEmpty()) {
            (passedJobs.size.toDouble() / enrichedJobs.size * 100).toInt()
        } else {
            0
        }

        logger.info(
            "[JobSearch: {}] Pre-filter results: {} passed, {} filtered ({}% pass rate)",
            jobSearch.id,
            passedJobs.size,
            filteredJobs.size,
            passRate
        )

        return PreFilterResult(
            passedJobs = passedJobs,
            filteredJobs = filteredJobs,
            passedCount = passedJobs.size,
            filteredCount = filteredJobs.size
        )
    }

    /**
     * Builds a human-readable filter reason
     */
    private fun buildFilterReason(
        matchResult: TechnologyMatchResult,
        required: ExtractedTechnologies,
        available: ExtractedTechnologies
    ): String {
        // Find primary technologies that didn't match well enough
        val missingPrimary = matchResult.primaryMatches
            .filter { it.similarity < TechnologyMatchingService.MODERATE_STRICTNESS_THRESHOLD }
            .map { match ->
                val bestMatch = match.matchedTech
                if (bestMatch != null) {
                    "${match.requiredTech.name} (best match: ${bestMatch.name} with ${String.format("%.0f", match.similarity * 100)}% similarity)"
                } else {
                    match.requiredTech.name
                }
            }

        return when {
            missingPrimary.isNotEmpty() -> {
                "Missing or incompatible primary technology: ${missingPrimary.joinToString(", ")}"
            }
            else -> {
                "Technology requirements not met (primary score: ${(matchResult.primaryScore * 100).toInt()}%)"
            }
        }
    }
}
