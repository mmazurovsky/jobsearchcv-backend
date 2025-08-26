package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.EmailContent
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val resendEmailService: ResendEmailService
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    fun sendEmail(emailContent: EmailContent): Boolean {
        return try {
            runBlocking {
                val result = resendEmailService.sendEmail(emailContent)
                result.isSuccess
            }
        } catch (e: Exception) {
            logger.error("Failed to send email to ${emailContent.recipient}", e)
            false
        }
    }

    fun sendBulkEmails(
        emailContents: List<EmailContent>
    ): BulkEmailResult {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()

        runBlocking {
            val result = resendEmailService.sendBulkEmails(emailContents)
            if (result.isSuccess) {
                // Parse the summary to get counts
                val summary = result.getOrNull() ?: ""
                logger.info("Bulk email result: $summary")
                
                // For now, we'll process them individually to get detailed results
                emailContents.forEach { emailContent ->
                    val individualResult = resendEmailService.sendEmail(emailContent)
                    if (individualResult.isSuccess) {
                        successful.add(emailContent.recipient)
                    } else {
                        failed.add(emailContent.recipient to (individualResult.exceptionOrNull()?.message ?: "Unknown error"))
                    }
                }
            } else {
                // If bulk operation failed entirely, mark all as failed
                emailContents.forEach { emailContent ->
                    failed.add(emailContent.recipient to (result.exceptionOrNull()?.message ?: "Bulk operation failed"))
                }
            }
        }

        return BulkEmailResult(successful, failed)
    }

    data class BulkEmailResult(
        val successful: List<String>,
        val failed: List<Pair<String, String>>
    )
}