package com.jobsearchcv.backend.config

import com.jobsearchcv.backend.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    @Value("\${cors.allowed-origins}") private val localAllowedOrigins: String
) {
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { } // Use default CORS configuration
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authz ->
                authz
                    // Public endpoints
                    .requestMatchers("/", "/error").permitAll() // Root and error pages
                    .requestMatchers("/api/cv/health").permitAll()
                    .requestMatchers("/api/job-data-callback").permitAll()
                    .requestMatchers("/api/test/**").permitAll() // Test endpoints for Logbook
                    // Stripe webhook endpoint (must be public)
                    .requestMatchers("/api/subscriptions/webhook").permitAll()
                    // Admin endpoints (authenticated via admin secret)
                    .requestMatchers("/api/admin/**").permitAll()
                    // Public job viewing endpoint (for X.com tweet links)
                    .requestMatchers("/api/jobs/**").permitAll()
                    // Public page jobs endpoint (for displaying user's page jobs)
                    .requestMatchers("/api/page-jobs/**").permitAll()
                    // OpenAPI endpoints
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/swagger-ui.html").permitAll()
                    // Protected endpoints
                    .requestMatchers("/api/cv/uploadAndCreateSearches").authenticated()
                    .requestMatchers("/api/cv/**").authenticated()
                    .requestMatchers("/api/job-searches/**").authenticated()
                    .requestMatchers("/api/destinations/**").authenticated()
                    .requestMatchers("/api/scored-jobs/**").authenticated()
                    // All other endpoints require authentication by default
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
    
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            val origins = localAllowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            if (origins.contains("*")) {
                // If wildcard is present, use patterns and disable credentials
                allowedOriginPatterns = listOf("*")
                allowCredentials = false
            } else {
                // Explicit origins with credentials
                allowedOrigins = origins
                allowCredentials = true // Required for Authorization headers
            }
            
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
            allowedHeaders = listOf("*")
            maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}