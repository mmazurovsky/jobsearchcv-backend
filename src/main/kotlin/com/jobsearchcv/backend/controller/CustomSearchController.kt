package com.jobsearchcv.backend.controller

import com.fasterxml.jackson.annotation.JsonProperty
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.service.ScraperJobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.UUID

data class CustomSearchRequest(
    @JsonProperty("_id") val id: String? = null,
    @JsonProperty("user_id") val userId: String,
    @JsonProperty("job_title") val jobTitle: String,
    val location: String,
    @JsonProperty("job_types") val jobTypes: List<String> = emptyList(),
    @JsonProperty("remote_types") val remoteTypes: List<String> = emptyList(),
    @JsonProperty("time_period") val timePeriod: String = "1 hour",
    @JsonProperty("filter_text") val filterText: String? = null
)

@RestController
@RequestMapping("/api/custom-search")
class CustomSearchController(
    private val jobSearchRepository: JobSearchRepository,
    private val scraperJobService: ScraperJobService
) {

    private val logger = LoggerFactory.getLogger(CustomSearchController::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    @PostMapping("/trigger")
    fun trigger(@RequestBody request: CustomSearchRequest): ResponseEntity<Map<String, String>> {
        val jobTypes = request.jobTypes.mapNotNull { JobType.fromLabel(it) }
        val remoteTypes = request.remoteTypes.mapNotNull { RemoteType.fromLabel(it) }
        val timePeriod = TimePeriod.fromDisplayName(request.timePeriod) ?: TimePeriod.getDefault()

        val jobSearch = JobSearchOut(
            id = request.id ?: UUID.randomUUID().toString(),
            jobTitle = request.jobTitle,
            location = request.location,
            jobTypes = jobTypes,
            remoteTypes = remoteTypes,
            timePeriod = timePeriod,
            userId = request.userId,
            createdAt = OffsetDateTime.now(),
            filterText = request.filterText,
            isApproved = true,
            isSubscribed = false,
            isAdmin = true
        )

        jobSearchRepository.save(jobSearch)
        logger.info("Saved custom job search: id={}, userId={}, title={}", jobSearch.id, jobSearch.userId, jobSearch.jobTitle)

        scope.launch {
            try {
                scraperJobService.triggerScraperJobAndLog(jobSearch)
                logger.info("Scraper triggered for custom search: id={}", jobSearch.id)
            } catch (e: Exception) {
                logger.error("Failed to trigger scraper for custom search: id={}", jobSearch.id, e)
            }
        }

        return ResponseEntity.accepted().body(
            mapOf("status" to "accepted", "jobSearchId" to jobSearch.id)
        )
    }
}
