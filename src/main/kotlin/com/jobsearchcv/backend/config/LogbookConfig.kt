package com.jobsearchcv.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.Logbook
import org.zalando.logbook.core.Conditions.*
import org.zalando.logbook.core.HeaderFilters
import org.zalando.logbook.core.QueryFilters
import org.zalando.logbook.json.JsonHttpLogFormatter
import org.zalando.logbook.core.DefaultSink
import org.zalando.logbook.core.DefaultHttpLogWriter

@Configuration
class LogbookConfig {

    @Bean
    fun logbook(): Logbook {
        return Logbook.builder()
            // Exclude unnecessary requests
            .condition(
                exclude(
                    requestTo("/favicon.ico"),
                    requestTo("/actuator/health"),
                    requestTo("/actuator/prometheus"),
                    requestTo("/error"),
                    // Exclude static resources
                    requestTo("/*.css"),
                    requestTo("/*.js"),
                    requestTo("/*.png"),
                    requestTo("/*.jpg"),
                    requestTo("/*.jpeg"),
                    requestTo("/*.gif"),
                    requestTo("/*.ico"),
                    requestTo("/*.svg")
                )
            )
            // Filter out verbose browser headers and obfuscate sensitive ones
            .headerFilter(HeaderFilters.authorization()) // Obfuscate Authorization header
            .headerFilter(
                HeaderFilters.replaceHeaders(
                    setOf(
                        "accept-encoding",
                        "accept-language", 
                        "cache-control",
                        "connection",
                        "sec-ch-ua",
                        "sec-ch-ua-mobile",
                        "sec-ch-ua-platform",
                        "sec-fetch-dest",
                        "sec-fetch-mode",
                        "sec-fetch-site",
                        "sec-fetch-user",
                        "upgrade-insecure-requests",
                        "user-agent"
                    ),
                    "..."
                )
            )
            // Filter sensitive query parameters
            .queryFilter(QueryFilters.replaceQuery("password", "XXX"))
            .queryFilter(QueryFilters.replaceQuery("token", "XXX"))
            .queryFilter(QueryFilters.replaceQuery("secret", "XXX"))
            // Use compact JSON format with custom sink
            .sink(DefaultSink(JsonHttpLogFormatter(), DefaultHttpLogWriter()))
            .build()
    }
}