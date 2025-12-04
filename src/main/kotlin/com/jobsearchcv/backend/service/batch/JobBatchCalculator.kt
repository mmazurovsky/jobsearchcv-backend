package com.jobsearchcv.backend.service.batch

import com.jobsearchcv.backend.service.client.LLMConfig
import org.slf4j.LoggerFactory

/**
 * Generic calculator for splitting jobs into LLM-compatible batches
 *
 * Algorithm:
 * 1. Calculate available input tokens = modelContextTokens - maxOutputTokens - basePromptTokens
 * 2. For each job, estimate tokens from content using tokensPerChar
 * 3. Group jobs into batches ensuring total estimated tokens < available input tokens
 * 4. Skip oversized jobs that can't fit in a single batch
 *
 * @param T The job data type (TranslatedJobData, EnrichedJobData, etc.)
 * @param R The batch request type (BatchEnrichmentRequest, BatchCompatibilityRequest, etc.)
 */
class JobBatchCalculator<T, R>(
    private val llmConfig: LLMConfig,
    private val jobSearchId: String,
    private val tokensPerChar: Double = DEFAULT_TOKENS_PER_CHAR,
    private val jobContentMaxChars: Int = DEFAULT_JOB_CONTENT_MAX_CHARS
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val DEFAULT_TOKENS_PER_CHAR = 0.25
        const val DEFAULT_JOB_CONTENT_MAX_CHARS = 4000
    }

    /**
     * Splits jobs into batches that fit within the model's context window
     *
     * @param jobs List of jobs to batch
     * @param promptTemplate The base prompt template (without jobs) for token estimation
     * @param contentExtractor Function to extract text content from a job for token estimation
     * @param requestBuilder Function to convert a job to a batch request object
     * @return List of batches, where each batch is a list of request objects
     */
    fun createBatches(
        jobs: List<T>,
        promptTemplate: String,
        contentExtractor: (T) -> String,
        requestBuilder: (T) -> R
    ): List<List<R>> {
        if (jobs.isEmpty()) {
            logger.info("[JobSearch: $jobSearchId] No jobs to batch")
            return emptyList()
        }

        // Calculate token budget
        val basePromptTokens = estimateTokens(promptTemplate)
        val availableInputTokens = calculateAvailableInputTokens(basePromptTokens)

        logger.info(
            "[JobSearch: $jobSearchId] Batch calculation: modelContext=${llmConfig.modelContextTokens}, " +
            "maxOutput=${llmConfig.maxOutputTokens}, basePrompt=$basePromptTokens, " +
            "available=$availableInputTokens, inputToOutputRatio=${String.format("%.2f", llmConfig.inputToOutputTokenRatio)}"
        )

        val batches = mutableListOf<List<R>>()
        var currentBatch = mutableListOf<R>()
        var currentBatchEstimatedTokens = 0
        var skippedJobsCount = 0

        for (job in jobs) {
            val request = requestBuilder(job)
            val jobContent = contentExtractor(job)
            val truncatedContent = jobContent.take(jobContentMaxChars)
            val estimatedJobTokens = estimateTokens(truncatedContent)

            // Check if this single job exceeds available tokens
            if (estimatedJobTokens > availableInputTokens) {
                logger.warn(
                    "[JobSearch: $jobSearchId] Skipping oversized job ($estimatedJobTokens tokens > " +
                    "$availableInputTokens available). Content length: ${truncatedContent.length} chars"
                )
                skippedJobsCount++
                continue
            }

            // Check if adding this job would exceed token limit
            val newBatchTokens = currentBatchEstimatedTokens + estimatedJobTokens

            if (currentBatch.isNotEmpty() && newBatchTokens > availableInputTokens) {
                // Current batch is full, start a new one
                logger.debug(
                    "[JobSearch: $jobSearchId] Batch complete with ${currentBatch.size} jobs " +
                    "(~$currentBatchEstimatedTokens tokens)"
                )
                batches.add(currentBatch.toList())
                currentBatch = mutableListOf(request)
                currentBatchEstimatedTokens = estimatedJobTokens
            } else {
                // Add to current batch
                currentBatch.add(request)
                currentBatchEstimatedTokens = newBatchTokens
            }
        }

        // Add final batch if not empty
        if (currentBatch.isNotEmpty()) {
            logger.debug(
                "[JobSearch: $jobSearchId] Final batch with ${currentBatch.size} jobs " +
                "(~$currentBatchEstimatedTokens tokens)"
            )
            batches.add(currentBatch)
        }

        logger.info(
            "[JobSearch: $jobSearchId] Created ${batches.size} batches from ${jobs.size} jobs " +
            "(skipped $skippedJobsCount oversized)"
        )

        return batches
    }

    /**
     * Calculate how many tokens are available for job content in the input
     *
     * Formula: modelContextTokens - maxOutputTokens - basePromptTokens
     *
     * Explanation:
     * - modelContextTokens: Total window the model can handle
     * - maxOutputTokens: Reserved for the model's response
     * - basePromptTokens: Used by prompt template (instructions, formatting)
     * - Result: Space left for actual job data
     */
    private fun calculateAvailableInputTokens(basePromptTokens: Int): Int {
        return llmConfig.modelContextTokens - llmConfig.maxOutputTokens - basePromptTokens
    }

    /**
     * Estimate token count from text content
     *
     * Uses simple character-to-token conversion factor.
     * For English text, ~0.25 tokens per character is a reasonable estimate.
     */
    private fun estimateTokens(content: String): Int {
        return (content.length * tokensPerChar).toInt()
    }
}
