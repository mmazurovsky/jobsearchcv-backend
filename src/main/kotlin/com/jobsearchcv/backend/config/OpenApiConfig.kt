package com.jobsearchcv.backend.config

import com.jobsearchcv.backend.service.UrlService
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    private val urlService: UrlService
) {

    @Value("\${spring.application.name:Job Search CV Backend}")
    private lateinit var applicationName: String

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title(applicationName)
                    .version("1.0.0")
                    .description("""
                        Job Search CV Backend API provides endpoints for:
                        - CV upload and analysis
                        - Job search management
                        - Destination management for job alerts
                        - Job data processing
                        
                        Authentication is required for most endpoints using Firebase JWT tokens.
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("API Support")
                            .email(urlService.getSupportEmail())
                    )
                    .license(
                        License()
                            .name("Proprietary")
                    )
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token from Firebase authentication")
                    )
            )
    }
}