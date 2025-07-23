package com.jobsearchcv.backend.service.client

import com.jobsearchcv.backend.domain.model.SearchJobsParams
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.sentry.Sentry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


data class ScraperResponse(
    val isSuccessful: Boolean,
    val statusCode: Int,
    val body: String
)

@Service
class ScraperClient(

    @Value("\${SCRAPER_SERVICE_URL}")     private val scraperServiceUrl: String,
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ScraperClient::class.java)
    }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        engine {
            requestTimeout = 30_000
        }
    }
    
    suspend fun scrapeJobs(params: SearchJobsParams): ScraperResponse {
        return try {
            val response = client.post("${scraperServiceUrl}/search_jobs") {
                contentType(ContentType.Application.Json)
                setBody(params)
            }
            
            ScraperResponse(
                isSuccessful = response.status.value in 200..299,
                statusCode = response.status.value,
                body = response.bodyAsText()
            )
        } catch (e: Exception) {
            logger.error("Error calling scraper service", e)
            Sentry.captureException(e)
            ScraperResponse(
                isSuccessful = false,
                statusCode = -1,
                body = e.message ?: "Unknown error"
            )
        }
    }
    
    suspend fun checkHealth(): Map<String, Any> {
        return try {
            val response = client.get("${scraperServiceUrl}/health")
            
            if (response.status.value in 200..299) {
                mapOf("status" to "healthy", "message" to "Scraper service is running")
            } else {
                mapOf("status" to "unhealthy", "message" to "Health check failed: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error checking scraper service health", e)
            Sentry.captureException(e)
            mapOf("status" to "unhealthy", "message" to "Error: ${e.message}")
        }
    }
    
    suspend fun checkProxyConnection(): Map<String, Any> {
        return try {
            val response = client.get("${scraperServiceUrl}/check_proxy_connection")
            
            if (response.status.value in 200..299) {
                mapOf("success" to true, "message" to "Proxy connection successful")
            } else {
                mapOf("success" to false, "message" to "Proxy connection failed: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error checking proxy connection", e)
            Sentry.captureException(e)
            mapOf("success" to false, "message" to "Error: ${e.message}")
        }
    }
} 