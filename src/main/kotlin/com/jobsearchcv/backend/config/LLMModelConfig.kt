package com.jobsearchcv.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "llm.models")
class LLMModelConfig {
    var enrichment: String = "deepseek/deepseek-v4-flash"
    var scoring: String = "deepseek/deepseek-v4-flash"
    var adminEnrichment: String = "deepseek/deepseek-v4-flash"
    var adminScoring: String = "deepseek/deepseek-v4-flash"
}
