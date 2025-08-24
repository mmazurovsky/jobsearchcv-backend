package com.jobsearchcv.backend.config

import com.resend.Resend
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ResendConfig {
    
    @Value("\${resend.api-key}")
    private lateinit var apiKey: String
    
    @Bean
    fun resendClient(): Resend {
        return Resend(apiKey)
    }
}