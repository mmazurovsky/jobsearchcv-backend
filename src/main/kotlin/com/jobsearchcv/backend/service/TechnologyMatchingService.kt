package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for calculating technology match scores between requirements and available technologies
 * Implements moderate strictness filtering (at least one primary tech must match with >= 0.70 similarity)
 */
@Service
class TechnologyMatchingService(
    private val technologyDatabaseService: TechnologyDatabaseService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Similarity threshold for moderate strictness filter
     * At least one primary technology must have this similarity or higher
     */
    companion object {
        const val MODERATE_STRICTNESS_THRESHOLD = 0.70
    }

    /**
     * Calculates technology match between required and available technologies
     */
    fun calculateTechnologyMatch(
        required: ExtractedTechnologies,
        available: ExtractedTechnologies
    ): TechnologyMatchResult {

        // Match primary technologies (most important)
        val primaryMatches = matchPrimaryTechnologies(
            required.primary,
            available.primary + available.secondary  // Check against all available
        )

        // Match secondary technologies (supporting evidence)
        val secondaryMatches = matchSecondaryTechnologies(
            required.secondary,
            available.primary + available.secondary
        )

        // Calculate scores
        val primaryScore = calculateScore(primaryMatches, required.primary.size)
        val secondaryScore = calculateScore(secondaryMatches, required.secondary.size)

        // Weighted combination: Primary 70%, Secondary 30%
        val overallScore = if (required.primary.isEmpty() && required.secondary.isEmpty()) {
            1.0  // No requirements = perfect match
        } else if (required.primary.isEmpty()) {
            secondaryScore  // Only secondary requirements
        } else {
            (primaryScore * 0.70) + (secondaryScore * 0.30)
        }

        // Moderate strictness filter: At least ONE primary tech must match >= 0.70
        val passesModerateFilter = passesModerateStrictnessFilter(
            primaryMatches,
            required.primary
        )

        logger.debug(
            "Match result: overall={}, primary={}, secondary={}, passes={}",
            String.format("%.2f", overallScore),
            String.format("%.2f", primaryScore),
            String.format("%.2f", secondaryScore),
            passesModerateFilter
        )

        return TechnologyMatchResult(
            overallScore = overallScore,
            primaryScore = primaryScore,
            secondaryScore = secondaryScore,
            primaryMatches = primaryMatches,
            secondaryMatches = secondaryMatches,
            passesModerateFilter = passesModerateFilter
        )
    }

    /**
     * Matches primary technologies (languages, domain expertise)
     */
    private fun matchPrimaryTechnologies(
        required: List<Technology>,
        available: List<Technology>
    ): List<TechnologyMatch> {
        if (required.isEmpty()) {
            return emptyList()
        }

        return required.map { requiredTech ->
            val bestMatch = findBestMatch(requiredTech, available)
            TechnologyMatch(
                requiredTech = requiredTech,
                matchedTech = bestMatch?.technology,
                similarity = bestMatch?.similarity ?: 0.0,
                matchType = determineMatchType(bestMatch?.similarity ?: 0.0)
            )
        }
    }

    /**
     * Matches secondary technologies (frameworks, tools, databases)
     */
    private fun matchSecondaryTechnologies(
        required: List<Technology>,
        available: List<Technology>
    ): List<TechnologyMatch> {
        if (required.isEmpty()) {
            return emptyList()
        }

        return required.map { requiredTech ->
            val bestMatch = findBestMatch(requiredTech, available)
            TechnologyMatch(
                requiredTech = requiredTech,
                matchedTech = bestMatch?.technology,
                similarity = bestMatch?.similarity ?: 0.0,
                matchType = determineMatchType(bestMatch?.similarity ?: 0.0)
            )
        }
    }

    /**
     * Finds the best matching technology from available list
     * Returns the technology with highest similarity score
     */
    private fun findBestMatch(
        required: Technology,
        available: List<Technology>
    ): MatchCandidate? {
        var bestMatch: MatchCandidate? = null
        var bestSimilarity = 0.0

        available.forEach { availableTech ->
            val similarity = if (required.id == availableTech.id) {
                1.0  // Exact match
            } else {
                technologyDatabaseService.getSimilarity(required.id, availableTech.id)
            }

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = MatchCandidate(availableTech, similarity)
            }
        }

        return bestMatch
    }

    /**
     * Determines match type based on similarity score
     */
    private fun determineMatchType(similarity: Double): MatchType {
        return when {
            similarity >= 1.0 -> MatchType.EXACT
            similarity >= 0.85 -> MatchType.HIGH_SIMILARITY
            similarity >= 0.70 -> MatchType.MODERATE_SIMILARITY
            similarity >= 0.50 -> MatchType.LOW_SIMILARITY
            similarity >= 0.20 -> MatchType.WEAK_SIMILARITY
            else -> MatchType.NO_MATCH
        }
    }

    /**
     * Calculates average similarity score
     */
    private fun calculateScore(matches: List<TechnologyMatch>, requiredCount: Int): Double {
        if (requiredCount == 0) return 1.0  // No requirements = perfect score

        val totalSimilarity = matches.sumOf { it.similarity }
        return (totalSimilarity / requiredCount).coerceIn(0.0, 1.0)
    }

    /**
     * Checks if job passes moderate strictness filter
     * MODERATE: At least ONE primary tech must have similarity >= 0.70
     * This is OR logic, not AND
     */
    private fun passesModerateStrictnessFilter(
        primaryMatches: List<TechnologyMatch>,
        required: List<Technology>
    ): Boolean {
        if (required.isEmpty()) {
            return true  // No primary requirements = pass
        }

        // At least ONE primary tech must match with >= 0.70 similarity
        val passes = primaryMatches.any { it.similarity >= MODERATE_STRICTNESS_THRESHOLD }

        if (!passes) {
            logger.debug(
                "Job failed moderate filter: best primary match = {}",
                primaryMatches.maxOfOrNull { it.similarity } ?: 0.0
            )
        }

        return passes
    }

    /**
     * Internal data class for match candidates
     */
    private data class MatchCandidate(
        val technology: Technology,
        val similarity: Double
    )
}
