package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import com.jobsearchcv.backend.repository.JobSearchPromptRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PromptJobSearchCreationService(
    private val promptProcessingService: PromptProcessingService,
    private val jobSearchPromptRepository: JobSearchPromptRepository,
    private val jobSearchRepository: JobSearchRepository
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PromptJobSearchCreationService::class.java)
    }

    /**
     * Complete workflow for creating job searches from a prompt:
     * 1. Process prompt with AI to generate job searches
     * 2. Save the prompt to database
     * 3. Save job searches with reference to the prompt
     *
     * @return Result containing promptId and created job searches
     */
    suspend fun createJobSearchesFromPrompt(
        prompt: String,
        userId: String
    ): PromptJobSearchCreationResult {
        try {
            logger.info("Creating job searches from prompt for user: $userId")

            // Step 1: Process prompt with AI
            val processingResult = promptProcessingService.processPromptWithAI(prompt, userId)

            if (!processingResult.success || processingResult.recommendedSearches == null) {
                logger.error("Prompt processing failed: ${processingResult.errorMessage}")
                return PromptJobSearchCreationResult(
                    success = false,
                    promptId = null,
                    jobSearches = emptyList(),
                    errorMessage = processingResult.errorMessage ?: "Failed to process prompt"
                )
            }

            // Step 2: Save the prompt record
            val promptRecord = JobSearchPrompt.create(userId, prompt)
            val savedPrompt = jobSearchPromptRepository.save(promptRecord)
            logger.debug("Saved prompt with ID: ${savedPrompt.id}")

            // Step 3: Convert and save job searches with promptId reference
            val recommendedSearches = processingResult.recommendedSearches
            val savedSearches = if (recommendedSearches.isNotEmpty()) {
                val jobSearchOuts = recommendedSearches.map { searchIn ->
                    JobSearchOut.fromJobSearchIn(
                        searchIn,
                        userId,
                        isApproved = false,
                        promptId = savedPrompt.id
                    )
                }
                jobSearchRepository.saveAll(jobSearchOuts)
            } else {
                emptyList()
            }

            logger.info("Successfully created ${savedSearches.size} job searches from prompt: promptId=${savedPrompt.id}")

            // Convert saved searches back to JobSearchIn for response
            val jobSearchesForResponse = savedSearches.map { saved ->
                JobSearchIn(
                    id = saved.id,
                    jobTitle = saved.jobTitle,
                    location = saved.location,
                    jobTypes = saved.jobTypes,
                    remoteTypes = saved.remoteTypes,
                    timePeriod = saved.timePeriod,
                    filterText = saved.filterText
                )
            }

            return PromptJobSearchCreationResult(
                success = true,
                promptId = savedPrompt.id,
                promptText = savedPrompt.promptText,
                createdAt = savedPrompt.createdAt,
                jobSearches = jobSearchesForResponse,
                errorMessage = null
            )

        } catch (e: Exception) {
            logger.error("Error creating job searches from prompt: ${e.message}", e)
            return PromptJobSearchCreationResult(
                success = false,
                promptId = null,
                jobSearches = emptyList(),
                errorMessage = "Internal error: ${e.message}"
            )
        }
    }
}

/**
 * Result of prompt-based job search creation
 */
data class PromptJobSearchCreationResult(
    val success: Boolean,
    val promptId: String? = null,
    val promptText: String? = null,
    val createdAt: java.time.OffsetDateTime? = null,
    val jobSearches: List<JobSearchIn>,
    val errorMessage: String? = null
)
