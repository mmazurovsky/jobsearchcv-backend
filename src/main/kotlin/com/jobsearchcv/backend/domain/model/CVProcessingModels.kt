package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a skill with its weight (0-100)
 */
data class SkillWithWeight(
    val skill: String,
    val weight: Int // 0-100, where 100 is expert level
)

/**
 * Result of CV text extraction and analysis
 */
data class CVAnalysisResult(
    @JsonProperty("current_or_desired_position")
    val currentOrDesiredPosition: String?,
    
    @JsonProperty("previous_positions")
    val previousPositions: List<String>,
    
    @JsonProperty("skills_with_weights")
    val skillsWithWeights: List<SkillWithWeight>,
    
    @JsonProperty("education")
    val education: List<String>,
    
    @JsonProperty("location")
    val location: String?,
    
    @JsonProperty("recommended_searches")
    val recommendedSearches: List<JobSearchIn>
)

/**
 * Request for CV processing service
 */
data class CVProcessingRequest(
    val extractedText: String,
    val userId: String,
    val fileName: String
)

/**
 * Response for the uploadAndCreateSearches endpoint
 */
data class UploadAndCreateSearchesResponse(
    val cvId: String,
    val linkToCv: String,
    val recommendedSearches: List<JobSearchIn>,
    val analysisResult: CVAnalysisResult
)

/**
 * Internal result from the CV processing coroutine
 */
data class CVProcessingResult(
    val success: Boolean,
    val analysisResult: CVAnalysisResult?,
    val errorMessage: String? = null
)

/**
 * Internal result from the S3 upload coroutine
 */
data class S3UploadResult(
    val success: Boolean,
    val cvId: String,
    val linkToCv: String,
    val errorMessage: String? = null
)