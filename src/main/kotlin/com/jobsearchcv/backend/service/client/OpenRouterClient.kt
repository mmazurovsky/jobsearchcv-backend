package com.jobsearchcv.backend.service.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobsearchcv.backend.service.UrlService
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

@Service
class OpenRouterClient(
    @Value("\${OPENROUTER_API_KEY}")
    private val openRouterApiKey: String,
    private val urlService: UrlService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OpenRouterClient::class.java)
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
        // Reduced timeouts from 30s/60s to 15s/45s for faster failure detection
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val REQUEST_TIMEOUT_SECONDS = 45L
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
        
        // Default models - easy to change
//        const val QWEN_1_5B = "qwen/qwen-2-1.5b-instruct"
//        const val QWEN_2_5B = "qwen/qwen-2.5-coder-32b-instruct"
//        const val DEFAULT_MODEL = QWEN_1_5B
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .version(HttpClient.Version.HTTP_1_1)
        .build()
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    suspend fun chat(request: LLMRequest): LLMResponse {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("🤖 OpenRouter API call started - Model: {}, Temperature: {}, MaxTokens: {}",
                    request.model, request.temperature, request.maxTokens)
                logger.info("🤖 OpenRouter API prompt length: {} characters", request.prompt.length)
                logger.debug("🤖 OpenRouter API prompt: {}", request.prompt.take(500) + "...")

                if (openRouterApiKey.isNullOrBlank()) {
                    logger.error("❌ OpenRouter API key not configured")
                    return@withContext LLMResponse(
                        success = false,
                        errorMessage = "OpenRouter API key not configured"
                    )
                }

                logger.info("🌐 Calling OpenRouter API...")
                val response = callAPIWithRetry(request)
                logger.info("✅ OpenRouter API response received - Status: {}", response.statusCode())

                when (response.statusCode()) {
                    200 -> {
                        val content = parseSuccessResponse(response.body())
                        logger.info("✅ OpenRouter API success - Response length: {} characters", content.length)
                        logger.debug("🤖 OpenRouter API response content: {}", content.take(500) + "...")
                        LLMResponse(success = true, content = content)
                    }
                    400 -> {
                        logger.error("❌ OpenRouter API bad request (400): {}", response.body())
                        LLMResponse(
                            success = false,
                            errorMessage = "Bad request: Check prompt format and parameters",
                            statusCode = 400
                        )
                    }
                    401 -> {
                        logger.error("❌ OpenRouter API unauthorized (401): Invalid API key")
                        LLMResponse(
                            success = false,
                            errorMessage = "Unauthorized: Invalid API key",
                            statusCode = 401
                        )
                    }
                    429 -> {
                        logger.error("❌ OpenRouter API rate limit exceeded (429)")
                        LLMResponse(
                            success = false,
                            errorMessage = "Rate limit exceeded: Try again later",
                            statusCode = 429
                        )
                    }
                    500, 502, 503, 504 -> {
                        logger.error("❌ OpenRouter API server error ({}): {}", response.statusCode(), response.body())
                        LLMResponse(
                            success = false,
                            errorMessage = "Server error: OpenRouter API is temporarily unavailable",
                            statusCode = response.statusCode()
                        )
                    }
                    else -> {
                        logger.error("❌ OpenRouter API unexpected status ({}): {}", response.statusCode(), response.body())
                        LLMResponse(
                            success = false,
                            errorMessage = "API request failed with status ${response.statusCode()}",
                            statusCode = response.statusCode()
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("💥 Error calling OpenRouter API", e)
                LLMResponse(
                    success = false,
                    errorMessage = "Failed to call OpenRouter API: ${e.message}"
                )
            }
        }
    }

    private suspend fun callAPIWithRetry(request: LLMRequest): HttpResponse<String> {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                logger.info("🔄 Attempt ${attempt + 1} of $MAX_RETRIES for OpenRouter API call")
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

    private suspend fun callAPI(request: LLMRequest): HttpResponse<String> {
        // Use the model from request, or default to Qwen model
        val modelToUse = request.model

        
        val requestBody = objectMapper.writeValueAsString(mapOf(
            "model" to modelToUse,
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
            .uri(URI.create(OPENROUTER_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $openRouterApiKey")
            .header("Accept", "application/json")
            .header("User-Agent", "JobSearchCV/1.0")
            .header("HTTP-Referer", urlService.getWebsiteUrl()) // Optional but recommended by OpenRouter
            .header("X-Title", "JobSearchCV Backend") // Optional metadata for OpenRouter
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }

    private fun parseSuccessResponse(responseBody: String): String {
        try {
            val responseJson = objectMapper.readTree(responseBody)
            val choices = responseJson.path("choices")

            // Check if choices array exists and is not empty
            if (choices.isMissingNode || !choices.isArray || choices.size() == 0) {
                logger.error("❌ OpenRouter API response missing or empty 'choices' array. Response: {}", responseBody)
                throw IllegalStateException("OpenRouter API response missing or empty 'choices' array")
            }

            val firstChoice = choices.get(0)
            if (firstChoice == null) {
                logger.error("❌ OpenRouter API response first choice is null. Response: {}", responseBody)
                throw IllegalStateException("OpenRouter API response first choice is null")
            }

            val message = firstChoice.path("message")
            if (message.isMissingNode) {
                logger.error("❌ OpenRouter API response missing 'message' field. Response: {}", responseBody)
                throw IllegalStateException("OpenRouter API response missing 'message' field")
            }

            val content = message.path("content")
            if (content.isMissingNode) {
                logger.error("❌ OpenRouter API response missing 'content' field. Response: {}", responseBody)
                throw IllegalStateException("OpenRouter API response missing 'content' field")
            }

            return content.asText()
        } catch (e: Exception) {
            logger.error("❌ Failed to parse OpenRouter API response: {}", e.message, e)
            logger.error("❌ Response body was: {}", responseBody)
            throw e
        }
    }

    fun isAvailable(): Boolean {
        return !openRouterApiKey.isNullOrBlank()
    }
}