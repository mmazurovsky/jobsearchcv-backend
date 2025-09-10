package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to create multiple job searches")
data class CreateJobSearchesRequest(
    @Schema(description = "List of job search configurations to create", required = true)
    val jobSearches: List<JobSearchIn>
)

@Schema(description = "Response for job search creation operation")
data class CreateJobSearchesResponse(
    @Schema(description = "Operation result message", example = "Successfully created 3 job searches", required = true)
    val message: String,
    @Schema(description = "List of created job search IDs", required = true)
    val jobSearchIds: List<String>,
    @Schema(description = "Destination ID for notifications", example = "dest-123", required = false)
    val destinationId: String?,
)

