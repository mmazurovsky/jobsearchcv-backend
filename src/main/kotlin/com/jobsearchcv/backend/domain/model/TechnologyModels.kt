package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Root container for the technology database loaded from YAML
 */
data class TechnologyDatabase(
    val version: String,
    val technologies: List<Technology>
)

/**
 * Represents a single technology, domain expertise, or tool
 */
data class Technology(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val category: TechnologyCategory,
    val subcategory: String,
    val primary: Boolean,
    @JsonProperty("parent_technology")
    val parentTechnology: String? = null,
    val related: List<RelatedTechnology> = emptyList()
)

/**
 * Represents a related technology with similarity score
 */
data class RelatedTechnology(
    val id: String,
    val similarity: Double,
    val reason: String
)

/**
 * Technology categories covering both technical and non-technical domains
 */
enum class TechnologyCategory {
    // Technical categories
    LANGUAGE,           // Programming languages (Java, Python, etc.)
    FRAMEWORK,          // Web frameworks (Spring, React, etc.)
    DATABASE,           // Databases (PostgreSQL, MongoDB, etc.)
    CLOUD,              // Cloud platforms (AWS, Azure, GCP)
    DEVOPS,             // DevOps tools (Docker, Kubernetes, etc.)
    MESSAGING,          // Message brokers (Kafka, RabbitMQ, etc.)
    API,                // API technologies (REST, GraphQL, etc.)
    MOBILE,             // Mobile development (Android, iOS, Flutter)
    FRONTEND,           // Frontend technologies
    BACKEND,            // Backend technologies
    TESTING,            // Testing frameworks
    TOOLS,              // Development tools (Git, Jira, etc.)
    DATA_ENGINEERING,   // Data engineering tools (Spark, Airflow, etc.)
    AI_ML,              // AI/ML frameworks (TensorFlow, PyTorch, etc.)

    // Non-technical categories
    DOMAIN,             // Domain expertise (Accounting, Marketing, HR, etc.)
    TOOL,               // Domain-specific tools (QuickBooks, Salesforce, etc.)
    METHODOLOGY,        // Methodologies (Agile, Scrum, Lean Six Sigma)
    CERTIFICATION,      // Certifications (PMP, etc.)
    COMPLIANCE,         // Compliance standards (HIPAA, GDPR)
    STANDARD;           // Industry standards (GAAP, IFRS)

    companion object {
        /**
         * Categories considered "primary" for filtering
         * (languages and domain expertise)
         */
        val PRIMARY_CATEGORIES = setOf(
            LANGUAGE,
            DOMAIN
        )
    }
}

/**
 * Result of extracting technologies from text
 */
data class ExtractedTechnologies(
    val primary: List<Technology>,      // Languages or domain expertise
    val secondary: List<Technology>,    // Frameworks, tools, databases
    val unmatched: List<String>         // Terms not found in database
)

/**
 * Match between a required technology and available technology
 */
data class TechnologyMatch(
    val requiredTech: Technology,
    val matchedTech: Technology?,
    val similarity: Double,
    val matchType: MatchType
)

/**
 * Type of match based on similarity score
 */
enum class MatchType {
    EXACT,                  // 1.0 - Exact match
    HIGH_SIMILARITY,        // 0.85-0.94 - Highly similar (Java/Kotlin)
    MODERATE_SIMILARITY,    // 0.70-0.84 - Same ecosystem (Spring/Micronaut)
    LOW_SIMILARITY,         // 0.50-0.69 - Related but different
    WEAK_SIMILARITY,        // 0.20-0.49 - Distant relation
    NO_MATCH                // 0.0-0.19 - No meaningful similarity
}

/**
 * Result of technology matching between requirements and available tech
 */
data class TechnologyMatchResult(
    val overallScore: Double,               // 0.0-1.0 combined score
    val primaryScore: Double,               // 0.0-1.0 primary tech score
    val secondaryScore: Double,             // 0.0-1.0 secondary tech score
    val primaryMatches: List<TechnologyMatch>,
    val secondaryMatches: List<TechnologyMatch>,
    val passesModerateFilter: Boolean       // At least one primary >= 0.70
)

/**
 * Result of pre-filtering jobs
 */
data class PreFilterResult(
    val passedJobs: List<PreFilteredJob>,
    val filteredJobs: List<PreFilteredJob>,
    val passedCount: Int,
    val filteredCount: Int
)

/**
 * Job with pre-filter metadata
 */
data class PreFilteredJob(
    val job: com.jobsearchcv.backend.domain.model.EnrichedJobData,
    val matchResult: TechnologyMatchResult?,
    val filterReason: String?               // e.g., "Missing primary technology: Java"
)
