package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.ScoredJobResponse
import com.jobsearchcv.backend.service.ScoredJobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scored-jobs")
@Tag(name = "Scored Jobs", description = "Fetch scored jobs with enrichment data")
@SecurityRequirement(name = "bearerAuth")
class ScoredJobController(
    private val scoredJobService: ScoredJobService
) {
    private val logger = LoggerFactory.getLogger(ScoredJobController::class.java)

    @GetMapping
    @Operation(
        summary = "Get scored jobs for authenticated user",
        description = "Fetches enriched/scored jobs sent within specified time window. Optionally filters by status and/or job search ID."
    )
    fun getScoredJobs(
        @Parameter(description = "Minutes to look back (max: 10080 = 7 days)")
        @RequestParam(defaultValue = "1440") minutesBack: Long,

        @Parameter(description = "Optional status filter")
        @RequestParam(required = false) status: String?,

        @Parameter(description = "Optional job search ID filter")
        @RequestParam(required = false) jobSearchId: String?,

        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<List<ScoredJobResponse>> {
        val userId = authentication.principal as String

        logger.debug("Request: userId=$userId, minutesBack=$minutesBack, status=$status, jobSearchId=$jobSearchId")

        val jobs = scoredJobService.getRecentScoredJobs(userId, minutesBack, status, jobSearchId)
        return ResponseEntity.ok(jobs)
    }
}
