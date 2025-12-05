package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobsearchcv.backend.domain.model.Technology
import com.jobsearchcv.backend.domain.model.TechnologyDatabase
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * Service for loading and querying the technology database
 * Provides O(1) lookup performance through in-memory indexes
 */
@Service
class TechnologyDatabaseService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // In-memory indexes for fast lookup
    private lateinit var technologiesById: Map<String, Technology>
    private lateinit var technologiesByAlias: Map<String, Technology>
    private lateinit var primaryTechnologies: Set<String>
    private lateinit var similarityMatrix: Map<Pair<String, String>, Double>

    @PostConstruct
    fun loadDatabase() {
        val startTime = System.currentTimeMillis()
        logger.info("Loading technology database...")

        try {
            val database = loadFromYaml()
            buildIndexes(database)

            val loadTime = System.currentTimeMillis() - startTime
            logger.info(
                "Technology database loaded successfully: {} technologies, {} aliases, {} ms",
                database.technologies.size,
                technologiesByAlias.size,
                loadTime
            )
        } catch (e: Exception) {
            logger.error("Failed to load technology database", e)
            // Initialize empty indexes to prevent NPE
            technologiesById = emptyMap()
            technologiesByAlias = emptyMap()
            primaryTechnologies = emptySet()
            similarityMatrix = emptyMap()
        }
    }

    /**
     * Loads technology database from YAML file
     */
    private fun loadFromYaml(): TechnologyDatabase {
        val resource = ClassPathResource("technology-database.yml")
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        return resource.inputStream.use { inputStream ->
            mapper.readValue<TechnologyDatabase>(inputStream)
        }
    }

    /**
     * Builds in-memory indexes for fast lookup
     */
    private fun buildIndexes(database: TechnologyDatabase) {
        // Index by ID
        technologiesById = database.technologies.associateBy { it.id }

        // Index by normalized alias
        // Each alias maps to ONE technology (first match wins)
        val aliasMap = mutableMapOf<String, Technology>()
        database.technologies.forEach { tech ->
            tech.aliases.forEach { alias ->
                val normalized = normalizeText(alias)
                if (!aliasMap.containsKey(normalized)) {
                    aliasMap[normalized] = tech
                }
            }
        }
        technologiesByAlias = aliasMap

        // Index primary technologies
        primaryTechnologies = database.technologies
            .filter { it.primary }
            .map { it.id }
            .toSet()

        // Build symmetric similarity matrix
        val matrix = mutableMapOf<Pair<String, String>, Double>()
        database.technologies.forEach { tech ->
            tech.related.forEach { related ->
                // Store both directions (symmetric)
                matrix[Pair(tech.id, related.id)] = related.similarity
                matrix[Pair(related.id, tech.id)] = related.similarity
            }
        }
        similarityMatrix = matrix

        logger.debug(
            "Indexes built: {} by ID, {} by alias, {} primary techs, {} similarity pairs",
            technologiesById.size,
            technologiesByAlias.size,
            primaryTechnologies.size,
            similarityMatrix.size
        )
    }

    /**
     * Normalizes text for consistent matching
     * Removes all non-alphanumeric characters and converts to lowercase
     */
    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(".", "")       // react.js → reactjs
            .replace("-", "")       // react-js → reactjs
            .replace("_", "")       // react_js → reactjs
            .replace(" ", "")       // machine learning → machinelearning
            .replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Finds a technology by term (checks aliases)
     * Returns null if not found
     */
    fun findTechnology(term: String): Technology? {
        val normalized = normalizeText(term)
        return technologiesByAlias[normalized]
    }

    /**
     * Gets a technology by ID
     */
    fun getTechnology(techId: String): Technology? {
        return technologiesById[techId]
    }

    /**
     * Checks if a technology is primary (language or domain)
     */
    fun isPrimaryTechnology(techId: String): Boolean {
        return primaryTechnologies.contains(techId)
    }

    /**
     * Gets similarity score between two technologies
     * Returns 1.0 for same technology, 0.0 if no relationship defined
     */
    fun getSimilarity(tech1Id: String, tech2Id: String): Double {
        if (tech1Id == tech2Id) return 1.0
        return similarityMatrix[Pair(tech1Id, tech2Id)] ?: 0.0
    }

    /**
     * Gets all technologies (for debugging/admin)
     */
    fun getAllTechnologies(): List<Technology> {
        return technologiesById.values.toList()
    }

    /**
     * Gets statistics about the database
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "total_technologies" to technologiesById.size,
            "total_aliases" to technologiesByAlias.size,
            "primary_technologies" to primaryTechnologies.size,
            "similarity_relationships" to similarityMatrix.size / 2, // Divide by 2 (symmetric)
            "categories" to technologiesById.values
                .groupBy { it.category }
                .mapValues { it.value.size }
        )
    }
}
