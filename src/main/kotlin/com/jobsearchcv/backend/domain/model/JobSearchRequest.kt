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
    @Schema(description = "Results of immediate search triggers", required = false)
    val immediateSearchResults: List<ImmediateSearchSummary>? = null
)

@Schema(description = "Summary of immediate search trigger result")
data class ImmediateSearchSummary(
    @Schema(description = "Original job search ID that triggered the immediate search", required = true)
    val originalJobSearchId: String,
    @Schema(description = "ID of the immediate search that was triggered", required = true)
    val immediateSearchId: String,
    @Schema(description = "Whether the immediate search was successful", required = true)
    val success: Boolean,
    @Schema(description = "Error message if the search failed", required = false)
    val errorMessage: String? = null
)