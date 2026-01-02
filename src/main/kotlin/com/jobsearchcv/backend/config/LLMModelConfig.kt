package com.jobsearchcv.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "llm.models")
class LLMModelConfig {
    // Free models (used for ADMIN searches - internal testing)
    var enrichment: String = "meta-llama/llama-3.3-70b-instruct:free"
    var scoring: String = "meta-llama/llama-3.3-70b-instruct:free"

    // Premium models (used for NON-ADMIN searches - real users)
    // Note: Property names have "admin" prefix for historical reasons, but these are used for non-admin searches
    var adminEnrichment: String = "openai/gpt-oss-120b"
    var adminScoring: String = "openai/gpt-oss-120b"
}
