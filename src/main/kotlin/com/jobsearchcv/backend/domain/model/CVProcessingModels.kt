package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Represents a skill with its weight (0-100)
 */
@Schema(description = "A skill with its proficiency weight")
data class SkillWithWeight(
    @Schema(description = "Skill name", example = "Java", required = true)
    val skill: String,
    @Schema(description = "Proficiency weight (0-100, where 100 is expert level)", example = "85", required = true)
    val weight: Int // 0-100, where 100 is expert level
)

/**
 * Result of CV text extraction and analysis
 */
@Schema(description = "Result of CV analysis by AI")
data class CVAnalysisResult(
    @JsonProperty("current_or_desired_position")
    @Schema(description = "Current or desired job position", example = "Senior Software Engineer", required = false)
    val currentOrDesiredPosition: String?,
    
    @JsonProperty("previous_positions")
    @Schema(description = "List of previous job positions", required = true)
    val previousPositions: List<String>,
    
    @JsonProperty("skills_with_weights")
    @Schema(description = "Skills extracted from CV with proficiency weights", required = true)
    val skillsWithWeights: List<SkillWithWeight>,
    
    @JsonProperty("education")
    @Schema(description = "Education background", required = true)
    val education: List<String>,
    
    @JsonProperty("location")
    @Schema(description = "Location preference", example = "New York, NY", required = false)
    val location: String?,
    
    @JsonProperty("recommended_searches")
    @Schema(description = "AI-recommended job searches based on CV analysis", required = true)
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
@Schema(description = "Response for CV upload and job search creation")
data class UploadAndCreateSearchesResponse(
    @Schema(description = "Unique identifier for the uploaded CV", example = "cv-123e4567-e89b-12d3-a456-426614174000", required = true)
    val cvId: String,
    @Schema(description = "Direct link to the uploaded CV file", example = "https://s3.amazonaws.com/bucket/cv-123.pdf", required = true)
    val linkToCv: String,
    @Schema(description = "AI-recommended job searches created from CV analysis", required = true)
    val recommendedSearches: List<JobSearchIn>,
    @Schema(description = "Detailed CV analysis results", required = true)
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