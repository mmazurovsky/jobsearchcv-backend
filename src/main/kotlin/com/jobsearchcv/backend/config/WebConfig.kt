package com.jobsearchcv.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    
    override fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
        // Set async request timeout to 60 seconds (60000 ms)
        configurer.setDefaultTimeout(60000)
    }
}