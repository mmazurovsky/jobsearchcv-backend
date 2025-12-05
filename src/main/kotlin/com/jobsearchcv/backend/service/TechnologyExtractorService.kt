package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.EnrichedJobData
import com.jobsearchcv.backend.domain.model.ExtractedTechnologies
import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.Technology
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for extracting technologies from text (job titles, filter text, job descriptions)
 * Uses deterministic keyword matching against the technology database
 */
@Service
class TechnologyExtractorService(
    private val technologyDatabaseService: TechnologyDatabaseService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Extracts required technologies from a job search (user requirements)
     */
    fun extractRequiredTechnologies(jobSearch: JobSearchOut): ExtractedTechnologies {
        val allTerms = mutableListOf<String>()

        // Extract from job title (highest priority for primary tech)
        val titleTerms = extractTermsFromText(jobSearch.jobTitle)
        allTerms.addAll(titleTerms)

        // Extract from filter text (includes both tech and non-tech)
        if (!jobSearch.filterText.isNullOrBlank()) {
            val filterTerms = extractTermsFromText(jobSearch.filterText)
            allTerms.addAll(filterTerms)
        }

        val extracted = categorizeTechnologies(allTerms)

        logger.debug(
            "Extracted from job search '{}': primary={}, secondary={}, unmatched={}",
            jobSearch.jobTitle,
            extracted.primary.map { it.name },
            extracted.secondary.map { it.name },
            extracted.unmatched
        )

        return extracted
    }

    /**
     * Extracts technologies from an enriched job posting
     * Uses LLM-extracted techstack as primary source
     */
    fun extractJobTechnologies(enrichedJob: EnrichedJobData): ExtractedTechnologies {
        val allTerms = mutableListOf<String>()

        // PRIMARY: Use LLM-extracted techstack (already done!)
        allTerms.addAll(enrichedJob.techstack)

        // FALLBACK: If techstack is empty, extract from title
        if (enrichedJob.techstack.isEmpty()) {
            logger.warn(
                "Job {} has empty techstack, falling back to title extraction",
                enrichedJob.id
            )
            val titleTerms = extractTermsFromText(enrichedJob.title)
            allTerms.addAll(titleTerms)

            // Also try first 200 chars of description
            val descriptionStart = enrichedJob.description.take(200)
            val descTerms = extractTermsFromText(descriptionStart)
            allTerms.addAll(descTerms)
        }

        val extracted = categorizeTechnologies(allTerms)

        logger.debug(
            "Extracted from job '{}': primary={}, secondary={}, unmatched={}",
            enrichedJob.title,
            extracted.primary.map { it.name },
            extracted.secondary.map { it.name },
            extracted.unmatched
        )

        return extracted
    }

    /**
     * Extracts terms from text using regex patterns
     * Handles special cases: C++, C#, .NET, React.js, etc.
     */
    private fun extractTermsFromText(text: String): List<String> {
        val terms = mutableListOf<String>()

        // Pattern 1: Special characters (C++, C#, .NET, F#)
        val specialPatterns = listOf(
            Regex("""C\+\+"""),
            Regex("""C#"""),
            Regex("""\.NET(?:\s*Core)?""", RegexOption.IGNORE_CASE),
            Regex("""F#""")
        )
        specialPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                terms.add(match.value)
            }
        }

        // Pattern 2: Words with optional dots/dashes/underscores
        // Matches: React.js, Node.js, spring-boot, scikit-learn
        val wordPattern = Regex("""[A-Za-z][A-Za-z0-9]*(?:[.\-_][A-Za-z][A-Za-z0-9]*)*""")
        wordPattern.findAll(text).forEach { match ->
            val term = match.value
            // Skip common non-tech words
            if (!isCommonNonTechWord(term)) {
                terms.add(term)
            }
        }

        return terms.distinct()
    }

    /**
     * Filters out common non-technology words
     */
    private fun isCommonNonTechWord(term: String): Boolean {
        val lowercaseTerm = term.lowercase()
        val commonWords = setOf(
            "the", "and", "or", "for", "with", "about", "from", "have", "this",
            "that", "will", "can", "should", "would", "could", "may", "might",
            "must", "shall", "need", "want", "work", "job", "role", "team",
            "company", "position", "candidate", "experience", "skills", "years",
            "required", "preferred", "minimum", "maximum", "salary", "benefits",
            "remote", "hybrid", "office", "visa", "sponsorship", "relocation",
            "senior", "junior", "mid", "level", "engineer", "developer", "manager",
            "lead", "architect", "consultant", "analyst", "specialist", "coordinator",
            "administrator", "assistant", "associate", "executive", "director",
            "please", "contact", "apply", "send", "email", "resume", "cv"
        )
        return commonWords.contains(lowercaseTerm)
    }

    /**
     * Categorizes terms into primary (languages/domains), secondary (frameworks/tools), and unmatched
     */
    private fun categorizeTechnologies(terms: List<String>): ExtractedTechnologies {
        val primary = mutableListOf<Technology>()
        val secondary = mutableListOf<Technology>()
        val unmatched = mutableListOf<String>()

        terms.forEach { term ->
            val technology = technologyDatabaseService.findTechnology(term)

            if (technology != null) {
                // Check if already added (avoid duplicates)
                if (technology.primary && !primary.contains(technology)) {
                    primary.add(technology)
                } else if (!technology.primary && !secondary.contains(technology)) {
                    secondary.add(technology)
                }
            } else {
                // Keep unmatched terms for logging/debugging
                unmatched.add(term)
            }
        }

        // Enrich with parent technologies
        // Example: If "Spring Boot" is found, add "Java" as well
        val enrichedPrimary = enrichWithParentTechnologies(primary + secondary)

        return ExtractedTechnologies(
            primary = enrichedPrimary.filter { it.primary }.distinct(),
            secondary = (secondary + enrichedPrimary.filter { !it.primary }).distinct(),
            unmatched = unmatched.distinct()
        )
    }

    /**
     * Enriches technology list with parent technologies
     * Example: Django → adds Python, Spring → adds Java
     */
    private fun enrichWithParentTechnologies(technologies: List<Technology>): List<Technology> {
        val enriched = technologies.toMutableList()

        technologies.forEach { tech ->
            tech.parentTechnology?.let { parentId ->
                technologyDatabaseService.getTechnology(parentId)?.let { parent ->
                    if (!enriched.contains(parent)) {
                        enriched.add(parent)
                        logger.debug("Enriched with parent technology: {} → {}", tech.name, parent.name)
                    }
                }
            }
        }

        return enriched
    }
}
