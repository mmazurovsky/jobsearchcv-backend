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
        description = "Fetches jobs sent to the user in the last 24 hours (1440 minutes) for page display. Optionally filters by seniority level. No authentication required."
    )
    fun getPageJobs(
        @Parameter(description = "User ID to fetch jobs for")
        @PathVariable userId: String,

        @Parameter(description = "Minutes to look back (default: 1440 = 24 hours)")
        @RequestParam(defaultValue = "1440") minutesBack: Long,

        @Parameter(description = "Seniority level to filter by (e.g., 'entry-level', 'mid-level', 'senior')")
        @RequestParam(required = false) seniority: String?
    ): ResponseEntity<List<PageJobResponse>> {
        logger.debug("Received request for page jobs: userId=$userId, minutesBack=$minutesBack, seniority=$seniority")

        // Validate minutesBack parameter
        if (minutesBack <= 0 || minutesBack > 10080) { // Max 7 days (168 hours)
            return ResponseEntity.badRequest().build()
        }

        // Normalize seniority - treat blank strings as null (no filtering)
        val normalizedSeniority = seniority?.takeIf { it.isNotBlank() }

        val jobs = pageJobService.getPageJobsForUser(userId, minutesBack, normalizedSeniority)
        return ResponseEntity.ok(jobs)
    }
}
