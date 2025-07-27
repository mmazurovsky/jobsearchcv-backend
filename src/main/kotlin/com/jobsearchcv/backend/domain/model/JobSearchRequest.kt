package com.jobsearchcv.backend.domain.model

data class CreateJobSearchesRequest(
    val jobSearches: List<JobSearchIn>
)

data class CreateJobSearchesResponse(
    val message: String,
    val jobSearchIds: List<String>,
    val destinationId: String?,
    val immediateSearchResults: List<ImmediateSearchSummary>? = null
)

data class ImmediateSearchSummary(
    val originalJobSearchId: String,
    val immediateSearchId: String,
    val success: Boolean,
    val errorMessage: String? = null
)