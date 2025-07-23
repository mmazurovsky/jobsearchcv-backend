package com.jobsearchcv.backend.service.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class DeepSeekRequest(
    val prompt: String,
    val temperature: Double = 0.1,
    val maxTokens: Int = 1000,
    val model: String = "deepseek-chat"
)

data class DeepSeekResponse(
    val success: Boolean,
    val content: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

@Service
class DeepSeekClient(


    @Value("\${DEEPSEEK_API_KEY}")     private    val deepseekApiKey: String,

    ) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DeepSeekClient::class.java)
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    suspend fun chat(request: DeepSeekRequest): DeepSeekResponse {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("🤖 DeepSeek API call started - Model: {}, Temperature: {}, MaxTokens: {}", 
                    request.model, request.temperature, request.maxTokens)
                logger.info("🤖 DeepSeek API prompt length: {} characters", request.prompt.length)
                logger.debug("🤖 DeepSeek API prompt: {}", request.prompt.take(500) + "...")
                
                if (deepseekApiKey.isNullOrBlank()) {
                    logger.error("❌ DeepSeek API key not configured")
                    return@withContext DeepSeekResponse(
                        success = false,
                        errorMessage = "DeepSeek API key not configured"
                    )
                }

                logger.info("🌐 Calling DeepSeek API...")
                val response = callAPI(request)
                logger.info("✅ DeepSeek API response received - Status: {}", response.statusCode())
                
                if (response.statusCode() == 200) {
                    val content = parseSuccessResponse(response.body())
                    logger.info("✅ DeepSeek API success - Response length: {} characters", content.length)
                    logger.debug("🤖 DeepSeek API response content: {}", content.take(500) + "...")
                    DeepSeekResponse(success = true, content = content)
                } else {
                    logger.error("❌ DeepSeek API error: {} - {}", response.statusCode(), response.body())
                    DeepSeekResponse(
                        success = false,
                        errorMessage = "API request failed with status ${response.statusCode()}",
                        statusCode = response.statusCode()
                    )
                }
            } catch (e: Exception) {
                logger.error("💥 Error calling DeepSeek API", e)
                DeepSeekResponse(
                    success = false,
                    errorMessage = "Failed to call DeepSeek API: ${e.message}"
                )
            }
        }
    }

    private suspend fun callAPI(request: DeepSeekRequest): HttpResponse<String> {
        val requestBody = objectMapper.writeValueAsString(mapOf(
            "model" to request.model,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to request.prompt
                )
            ),
            "temperature" to request.temperature,
            "max_tokens" to request.maxTokens
        ))

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${deepseekApiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }

    private fun parseSuccessResponse(responseBody: String): String {
        val responseJson = objectMapper.readTree(responseBody)
        return responseJson.path("choices").get(0).path("message").path("content").asText()
    }

    fun isAvailable(): Boolean {
        return !deepseekApiKey.isNullOrBlank()
    }
} 