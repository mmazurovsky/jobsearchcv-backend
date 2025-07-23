package com.jobsearchcv.backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: String
) : WebMvcConfigurer {
    
    override fun addCorsMappings(registry: CorsRegistry) {
        println("🔧 Configuring CORS with origins: $allowedOrigins")
        
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (origins.contains("*")) {
            // If wildcard is present, remove credentials to allow it
            registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
        } else {
            // Explicit origins with credentials
            registry.addMapping("/**")
                .allowedOrigins(*origins.toTypedArray())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600)
        }
    }
}