package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.ScoredJobData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

@Service
class TelegramService(
    @Value("\${telegram.bot-token:}") private val botToken: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate()

    suspend fun sendJobNotifications(chatId: String, searchName: String, jobs: List<ScoredJobData>) {
        if (botToken.isBlank()) {
            logger.warn("Telegram bot token not configured, skipping send")
            return
        }

        sendMessage(chatId, "Found ${jobs.size} matching jobs for $searchName!")

        jobs.forEach { job ->
            try {
                val text = formatJobMessage(job)
                sendMessage(chatId, text)
            } catch (e: Exception) {
                logger.error("Failed to send job ${job.title} to Telegram chat $chatId", e)
            }
        }
    }

    private fun sendMessage(chatId: String, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = mapOf("chat_id" to chatId, "text" to text, "disable_web_page_preview" to true)
        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(url, request, String::class.java)
        if (!response.statusCode.is2xxSuccessful) {
            logger.error("Telegram API error: status={}, body={}", response.statusCode, response.body)
        }
    }

    private fun formatJobMessage(job: ScoredJobData): String {
        return buildString {
            appendLine("Compatibility: ${job.compatibilityScore ?: "N/A"}")
            appendLine("${job.title}")
            appendLine("${job.company}")
            appendLine("${job.location}")
            if (!job.salary.isNullOrBlank()) appendLine("${job.salary}")
            if (job.applicants.isNotBlank()) appendLine("${job.applicants}")
            if (job.techstack.isNotEmpty()) {
                appendLine(
                    job.techstack.joinToString(", ") {
                        "#${it.replace(".", "").replace("/", "").replace(" ", "").lowercase()}"
                    }
                )
            }
            if (job.tags.isNotEmpty()) appendLine(job.tags.joinToString(", ") { it.trim() })
            appendLine(job.link)
        }
    }
}
