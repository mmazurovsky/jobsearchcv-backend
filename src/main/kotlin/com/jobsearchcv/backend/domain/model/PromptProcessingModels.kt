package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.OffsetDateTime

/**
 * Request for creating job searches from free-form prompt
 */
@Schema(description = "Request to generate job searches from a text prompt")
data class CreateSearchesFromPromptRequest(
    @Schema(
        description = "Free-form text describing desired job searches",
        example = "I want to find senior backend developer jobs in San Francisco",
        required = true
    )
    val prompt: String
)

/**
 * Response for createSearchesFromPrompt endpoint
 */
@Schema(description = "Response containing generated job searches from prompt")
data class CreateSearchesFromPromptResponse(
    @Schema(
        description = "ID of the saved prompt record",
        example = "prompt-123e4567-e89b-12d3-a456-426614174000",
        required = true
    )
    val promptId: String,

    @Schema(
        description = "Original prompt text provided by user",
        required = true
    )
    val prompt: String,

    @Schema(
        description = "Generated job searches with IDs from database",
        required = true
    )
    val recommendedSearches: List<JobSearchIn>,

    @Schema(
        description = "Timestamp when prompt was processed",
        required = true
    )
    val createdAt: String
)

/**
 * MongoDB document for storing job search prompts
 */
@Document(collection = "job_search_prompts")
@CompoundIndexes(
    CompoundIndex(name = "user_created_at_idx", def = "{'user_id': 1, 'created_at': -1}")
)
@Schema(description = "Stored job search prompt with metadata")
data class JobSearchPrompt(
    @Id
    @Schema(
        description = "Unique identifier for the prompt",
        example = "prompt-123e4567-e89b-12d3-a456-426614174000",
        required = true
    )
    val id: String,

    @Indexed(unique = false)
    @field:Field("user_id")
    @Schema(
        description = "ID of the user who created this prompt",
        required = true
    )
    val userId: String,

    @field:Field("prompt_text")
    @Schema(
        description = "Original prompt text",
        required = true
    )
    val promptText: String,

    @Indexed(unique = false)
    @field:Field("created_at")
    @Schema(
        description = "Timestamp when prompt was created",
        required = true
    )
    val createdAt: OffsetDateTime
) {
    companion object {
        fun create(userId: String, promptText: String): JobSearchPrompt {
            return JobSearchPrompt(
                id = "prompt-${java.util.UUID.randomUUID()}",
                userId = userId,
                promptText = promptText,
                createdAt = OffsetDateTime.now()
            )
        }
    }
}

/**
 * Internal processing result for prompt analysis
 */
data class PromptProcessingResult(
    val success: Boolean,
    val recommendedSearches: List<JobSearchIn>?,
    val errorMessage: String? = null
)
