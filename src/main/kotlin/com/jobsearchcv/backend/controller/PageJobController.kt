package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.PageJobResponse
import com.jobsearchcv.backend.service.PageJobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/page-jobs")
@Tag(name = "Page Jobs", description = "Public endpoint for fetching user's page jobs")
class PageJobController(
    private val pageJobService: PageJobService
) {
    private val logger = LoggerFactory.getLogger(PageJobController::class.java)

    @GetMapping("/{userId}")
    @Operation(
        summary = "Get page jobs for a user",
        description = "Fetches jobs sent to the user in the last 24 hours (1440 minutes) for page display. Optionally filters by tags and techstack. No authentication required."
    )
    fun getPageJobs(
        @Parameter(description = "User ID to fetch jobs for")
        @PathVariable userId: String,

        @Parameter(description = "Minutes to look back (default: 1440 = 24 hours)")
        @RequestParam(defaultValue = "1440") minutesBack: Long,

        @Parameter(description = "Comma-separated list of tags to filter by (e.g., 'entry-level,remote')")
        @RequestParam(required = false) tags: String?,

        @Parameter(description = "Comma-separated list of techstack items to filter by (e.g., 'Kotlin,Spring Boot')")
        @RequestParam(required = false) techstack: String?
    ): ResponseEntity<List<PageJobResponse>> {
        logger.debug("Received request for page jobs: userId=$userId, minutesBack=$minutesBack, tags=$tags, techstack=$techstack")

        // Validate minutesBack parameter
        if (minutesBack <= 0 || minutesBack > 10080) { // Max 7 days (168 hours)
            return ResponseEntity.badRequest().build()
        }

        // Parse comma-separated tags and techstack into lists
        // Sort them to ensure consistent cache keys regardless of input order
        val tagsList = tags?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.sorted()

        val techstackList = techstack?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.sorted()

        val jobs = pageJobService.getPageJobsForUser(userId, minutesBack, tagsList, techstackList)
        return ResponseEntity.ok(jobs)
    }
}
