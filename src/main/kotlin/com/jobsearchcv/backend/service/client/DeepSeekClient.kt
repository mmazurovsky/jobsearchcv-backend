package com.jobsearchcv.backend.service.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val REQUEST_TIMEOUT_SECONDS = 60L
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .version(HttpClient.Version.HTTP_1_1)
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
                val response = callAPIWithRetry(request)
                logger.info("✅ DeepSeek API response received - Status: {}", response.statusCode())
                
                when (response.statusCode()) {
                    200 -> {
                        val content = parseSuccessResponse(response.body())
                        logger.info("✅ DeepSeek API success - Response length: {} characters", content.length)
                        logger.debug("🤖 DeepSeek API response content: {}", content.take(500) + "...")
                        DeepSeekResponse(success = true, content = content)
                    }
                    400 -> {
                        logger.error("❌ DeepSeek API bad request (400): {}", response.body())
                        DeepSeekResponse(
                            success = false,
                            errorMessage = "Bad request: Check prompt format and parameters",
                            statusCode = 400
                        )
                    }
                    401 -> {
                        logger.error("❌ DeepSeek API unauthorized (401): Invalid API key")
                        DeepSeekResponse(
                            success = false,
                            errorMessage = "Unauthorized: Invalid API key",
                            statusCode = 401
                        )
                    }
                    429 -> {
                        logger.error("❌ DeepSeek API rate limit exceeded (429)")
                        DeepSeekResponse(
                            success = false,
                            errorMessage = "Rate limit exceeded: Try again later",
                            statusCode = 429
                        )
                    }
                    500, 502, 503, 504 -> {
                        logger.error("❌ DeepSeek API server error ({}): {}", response.statusCode(), response.body())
                        DeepSeekResponse(
                            success = false,
                            errorMessage = "Server error: DeepSeek API is temporarily unavailable",
                            statusCode = response.statusCode()
                        )
                    }
                    else -> {
                        logger.error("❌ DeepSeek API unexpected status ({}): {}", response.statusCode(), response.body())
                        DeepSeekResponse(
                            success = false,
                            errorMessage = "API request failed with status ${response.statusCode()}",
                            statusCode = response.statusCode()
                        )
                    }
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

    private suspend fun callAPIWithRetry(request: DeepSeekRequest): HttpResponse<String> {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                logger.info("🔄 Attempt ${attempt + 1} of $MAX_RETRIES for DeepSeek API call")
                return callAPI(request)
            } catch (e: Exception) {
                lastException = e
                val isRetryableError = when (e) {
                    is java.io.IOException -> e.message?.contains("RST_STREAM") == true ||
                            e.message?.contains("Connection reset") == true ||
                            e.message?.contains("timeout") == true
                    is java.net.http.HttpTimeoutException -> true
                    else -> false
                }
                
                if (isRetryableError && attempt < MAX_RETRIES - 1) {
                    val delayMs = INITIAL_DELAY_MS * (attempt + 1)
                    logger.warn("⚠️ Retryable error on attempt ${attempt + 1}: ${e.message}. Waiting ${delayMs}ms before retry...")
                    delay(delayMs)
                } else {
                    logger.error("❌ Non-retryable error or max retries reached: ${e.message}")
                    throw e
                }
            }
        }
        
        throw lastException ?: RuntimeException("Failed after $MAX_RETRIES attempts")
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
            .header("Accept", "application/json")
            .header("User-Agent", "JobSearchCV/1.0")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
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