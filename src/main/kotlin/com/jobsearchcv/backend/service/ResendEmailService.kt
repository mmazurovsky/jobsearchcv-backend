package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.EmailContent
import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ResendEmailService(
    private val resendClient: Resend,
    @Value("\${resend.from-email}") private val fromEmail: String,
    @Value("\${resend.from-name}") private val fromName: String
) : EmailSender {
    
    private val logger = LoggerFactory.getLogger(ResendEmailService::class.java)
    
    override suspend fun sendEmail(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.info("Sending plain text email via Resend")
            
            val params = CreateEmailOptions.builder()
                .from("$fromName <$fromEmail>")
                .to(fromEmail) // Default to sending to self for plain text messages
                .subject("Notification")
                .text(message)
                .build()
            
            val response: CreateEmailResponse = resendClient.emails().send(params)
            
            logger.info("Email sent successfully with ID: ${response.id}")
            Result.success(response.id)
        } catch (e: ResendException) {
            logger.error("Failed to send email via Resend: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Unexpected error sending email: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendEmail(emailContent: EmailContent): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.info("Sending email to: ${emailContent.recipient} with subject: ${emailContent.subject}")
            
            val params = CreateEmailOptions.builder()
                .from("$fromName <$fromEmail>")
                .to(emailContent.recipient)
                .subject(emailContent.subject)
                .html(emailContent.htmlBody)
                .text(emailContent.textBody)
                .build()
            
            val response: CreateEmailResponse = resendClient.emails().send(params)
            
            logger.info("Email sent successfully to ${emailContent.recipient} with ID: ${response.id}")
            Result.success(response.id)
        } catch (e: ResendException) {
            logger.error("Failed to send email to ${emailContent.recipient}: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Unexpected error sending email to ${emailContent.recipient}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendBulkEmails(emailContents: List<EmailContent>): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.info("Sending bulk emails to ${emailContents.size} recipients")
            
            var successCount = 0
            var failureCount = 0
            val failedRecipients = mutableListOf<String>()
            
            // Resend doesn't have a bulk send API, so we send them individually
            // Consider implementing rate limiting if needed
            emailContents.forEach { emailContent ->
                try {
                    val params = CreateEmailOptions.builder()
                        .from("$fromName <$fromEmail>")
                        .to(emailContent.recipient)
                        .subject(emailContent.subject)
                        .html(emailContent.htmlBody)
                        .text(emailContent.textBody)
                        .build()
                    
                    resendClient.emails().send(params)
                    successCount++
                } catch (e: Exception) {
                    logger.error("Failed to send email to ${emailContent.recipient}: ${e.message}")
                    failureCount++
                    failedRecipients.add(emailContent.recipient)
                }
            }
            
            val summary = "Bulk email operation completed. Success: $successCount, Failed: $failureCount"
            logger.info(summary)
            
            if (failureCount > 0) {
                logger.warn("Failed to send emails to: ${failedRecipients.joinToString(", ")}")
            }
            
            Result.success(summary)
        } catch (e: Exception) {
            logger.error("Unexpected error during bulk email operation: ${e.message}", e)
            Result.failure(e)
        }
    }
}