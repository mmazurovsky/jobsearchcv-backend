package com.jobsearchcv.backend.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.service.ScraperJobService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class LocalJobSearchDef(
    @JsonProperty("_id") val id: String,
    @JsonProperty("job_title") val jobTitle: String,
    val location: String,
    @JsonProperty("job_types") val jobTypes: List<String> = emptyList(),
    @JsonProperty("remote_types") val remoteTypes: List<String> = emptyList(),
    @JsonProperty("time_period") val timePeriod: String = "1 hour",
    @JsonProperty("filter_text") val filterText: String? = null
)

@Component
@ConditionalOnProperty(name = ["local-dev.seed.enabled"], havingValue = "true")
class LocalDevJobSearchSeeder(
    private val jobSearchRepository: JobSearchRepository,
    private val scraperJobService: ScraperJobService,
    private val objectMapper: ObjectMapper,
    @Value("\${local-dev.seed.json-path}") private val jsonPath: String,
    @Value("\${local-dev.seed.trigger-delay-ms}") private val triggerDelayMs: Long
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LocalDevJobSearchSeeder::class.java)
        private const val LOCAL_USER_ID = "local-user-001"
    }

    private val scheduler = Executors.newScheduledThreadPool(2)

    @EventListener(ApplicationReadyEvent::class)
    @Order(2)
    fun onApplicationReady() = runBlocking {
        logger.info("=== Local Dev Job Search Seeder starting ===")

        val file = File(jsonPath)
        if (!file.exists()) {
            logger.warn("Seed file not found at {}. Skipping.", jsonPath)
            return@runBlocking
        }

        val defs: List<LocalJobSearchDef> = try {
            objectMapper.readValue(file)
        } catch (e: Exception) {
            logger.error("Failed to parse {}: {}", jsonPath, e.message, e)
            return@runBlocking
        }

        logger.info("Loaded {} job search definitions from {}", defs.size, jsonPath)

        val searches = defs.map { def ->
            JobSearchOut(
                id = def.id,
                jobTitle = def.jobTitle,
                location = def.location,
                jobTypes = def.jobTypes.mapNotNull { JobType.fromLabel(it) },
                remoteTypes = def.remoteTypes.mapNotNull { RemoteType.fromLabel(it) },
                timePeriod = TimePeriod.fromDisplayName(def.timePeriod) ?: TimePeriod.getDefault(),
                userId = LOCAL_USER_ID,
                createdAt = OffsetDateTime.now(),
                filterText = def.filterText,
                isApproved = true,
                isAdmin = true,
                isSubscribed = true
            )
        }

        // Upsert each search
        for (search in searches) {
            try {
                jobSearchRepository.save(search)
                logger.info("Upserted job search: {}", search.toLogString())
            } catch (e: Exception) {
                logger.error("Failed to upsert job search {}: {}", search.id, e.message, e)
            }
        }

        // Trigger scraping immediately for each search
        for (search in searches) {
            try {
                scraperJobService.triggerScraperJobAndLog(search)
                logger.info("Triggered scraper for: {}", search.toLogString())
            } catch (e: Exception) {
                logger.error("Failed to trigger scraper for {}: {}", search.id, e.message, e)
            }
            if (searches.last() != search) {
                delay(triggerDelayMs)
            }
        }

        // Schedule recurring triggers based on each search's time_period
        for (search in searches) {
            val periodSeconds = search.timePeriod.seconds.toLong()
            scheduler.scheduleAtFixedRate(
                { triggerSearch(search.id) },
                periodSeconds,
                periodSeconds,
                TimeUnit.SECONDS
            )
            logger.info("Scheduled recurring trigger for {} every {} ({}s)",
                search.id, search.timePeriod.displayName, periodSeconds)
        }

        logger.info("=== Local Dev Job Search Seeder finished ===")
    }

    private fun triggerSearch(searchId: String) {
        try {
            runBlocking {
                val jobSearch = jobSearchRepository.findById(searchId)
                if (jobSearch == null) {
                    logger.warn("Job search {} no longer exists, skipping", searchId)
                    return@runBlocking
                }
                logger.info("=== Scheduled re-trigger for {} ===", jobSearch.toLogString())
                scraperJobService.triggerScraperJobAndLog(jobSearch)
            }
        } catch (e: Exception) {
            logger.error("Failed to re-trigger scraper for {}: {}", searchId, e.message, e)
        }
    }
}
