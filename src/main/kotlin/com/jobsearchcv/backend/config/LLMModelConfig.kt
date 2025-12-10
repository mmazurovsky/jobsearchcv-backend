package com.jobsearchcv.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "llm.models")
class LLMModelConfig {
    // Regular user models (free tier)
    var enrichment: String = "meta-llama/llama-3.3-70b-instruct:free"
    var scoring: String = "meta-llama/llama-3.3-70b-instruct:free"

    // Admin models (premium tier)
    var adminEnrichment: String = "openai/gpt-oss-120b"
    var adminScoring: String = "openai/gpt-oss-120b"
}
