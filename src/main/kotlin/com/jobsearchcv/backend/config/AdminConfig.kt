package com.jobsearchcv.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "admin")
class AdminConfig {
    lateinit var secret: String
}