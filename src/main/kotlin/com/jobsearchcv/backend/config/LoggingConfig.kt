package com.jobsearchcv.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.CommonsRequestLoggingFilter

@Configuration
class LoggingConfig {

    @Bean
    fun requestLoggingFilter(): CommonsRequestLoggingFilter {
        val loggingFilter = CommonsRequestLoggingFilter()
        loggingFilter.setIncludeClientInfo(true)
        loggingFilter.setIncludeQueryString(true)
        loggingFilter.setIncludePayload(false) // Don't log request body
        loggingFilter.setIncludeHeaders(true)
        loggingFilter.setMaxPayloadLength(0) // Ensure no payload is logged
        loggingFilter.setAfterMessagePrefix("REQUEST: ")
        loggingFilter.setBeforeMessagePrefix("REQUEST: ")
        return loggingFilter
    }
}