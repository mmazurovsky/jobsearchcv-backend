package com.jobsearchcv.backend.service.client

/**
 * Request model for LLM API calls (OpenRouter, etc.)
 */
data class LLMRequest(
    val prompt: String,
    val temperature: Double = 0.1,
    val maxTokens: Int = 1000,
    val model: String
)

/**
 * Response model for LLM API calls
 */
data class LLMResponse(
    val success: Boolean,
    val content: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

/**
 * Configuration for LLM model selection and parameters
 */
data class LLMConfig(
    val model: String,
    val temperature: Double,
    val modelContextTokens: Int,      // Total context window
    val maxOutputTokens: Int          // Reserved for response
) {
    // Calculated: available tokens for input
    val maxInputTokens: Int
        get() = modelContextTokens - maxOutputTokens

    // Calculated: input-to-output ratio (e.g., 48500 / 1500 = 32.3)
    // Meaning: for every 1 output token, we have 32.3 input tokens available
    val inputToOutputTokenRatio: Double
        get() = maxInputTokens.toDouble() / maxOutputTokens.toDouble()

    companion object {
        // Default config for enrichment (structured data extraction)
        fun forEnrichment(modelOverride: String? = null) = LLMConfig(
            model = modelOverride ?: "deepseek/deepseek-v4-flash",
            temperature = 0.1,            // Low for deterministic JSON extraction
            modelContextTokens = 80000,  // Increased from 50K to support larger batches
            maxOutputTokens = 10000        // ~30 jobs × 50 tokens/job
        )

        // Default config for scoring (compatibility evaluation)
        fun forScoring(modelOverride: String? = null) = LLMConfig(
            model = modelOverride ?: "deepseek/deepseek-v4-flash",
            temperature = 0.3,            // Slightly higher for nuanced scoring
            modelContextTokens = 80000,  // Increased from 50K to support larger batches
            maxOutputTokens = 10000        // ~30 jobs × 80 tokens/job
        )
    }
}

