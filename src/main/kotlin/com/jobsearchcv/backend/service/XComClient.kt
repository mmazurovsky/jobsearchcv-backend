package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobsearchcv.backend.domain.model.TweetErrorResponse
import com.jobsearchcv.backend.domain.model.TweetRequest
import com.jobsearchcv.backend.domain.model.TweetResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.annotation.PreDestroy

/**
 * HTTP client for posting tweets to X.com via external API
 */
@Service
class XComClient(
    @Value("\${xcom.api-url}") private val apiUrl: String,
    @Value("\${xcom.api-key}") private val apiKey: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(XComClient::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                // Use the application's ObjectMapper configuration
                setDefaultPropertyInclusion(objectMapper.serializationConfig.defaultPropertyInclusion)
                setPropertyNamingStrategy(objectMapper.serializationConfig.propertyNamingStrategy)
            }
        }
        expectSuccess = false // Don't throw on non-2xx responses
    }

    /**
     * Posts a tweet to X.com
     * @return Result with TweetResponse on success, or error message on failure
     */
    suspend fun postTweet(username: String, text: String): Result<TweetResponse> {
        return try {
            logger.debug("Posting tweet to X.com for username: $username")

            val request = TweetRequest(
                username = username,
                text = text,
                mediaIds = null
            )

            val response = httpClient.post("$apiUrl/tweet") {
                header("X-API-Key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val tweetResponse = response.body<TweetResponse>()
                    logger.info("Successfully posted tweet for username: $username, tweetId: ${tweetResponse.tweetId}")
                    Result.success(tweetResponse)
                }
                HttpStatusCode.Unauthorized -> {
                    logger.error("Unauthorized: Invalid or missing API key")
                    Result.failure(Exception("Unauthorized: Invalid or missing API key"))
                }
                HttpStatusCode.NotFound -> {
                    val errorBody = try {
                        response.body<TweetErrorResponse>()
                    } catch (e: Exception) {
                        TweetErrorResponse(detail = "Account not found")
                    }
                    logger.error("Account not found: ${errorBody.detail}")
                    Result.failure(Exception("Account not found: ${errorBody.detail}"))
                }
                HttpStatusCode.InternalServerError -> {
                    val errorBody = try {
                        response.body<TweetErrorResponse>()
                    } catch (e: Exception) {
                        TweetErrorResponse(error = "Internal server error")
                    }
                    logger.error("X.com API error: ${errorBody.error ?: errorBody.detail}")
                    Result.failure(Exception("X.com API error: ${errorBody.error ?: errorBody.detail}"))
                }
                else -> {
                    val errorText = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        "Unknown error"
                    }
                    logger.error("Unexpected response from X.com API: ${response.status}, body: $errorText")
                    Result.failure(Exception("Unexpected response: ${response.status}"))
                }
            }
        } catch (e: Exception) {
            logger.error("Exception while posting tweet", e)
            Result.failure(e)
        }
    }

    @PreDestroy
    fun cleanup() {
        httpClient.close()
    }
}
