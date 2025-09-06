package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.EmailContent
import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Internal ResendEmailService - only accessible within this file
 * All external code should use AsyncEmailService instead
 */
internal class InternalResendEmailService(
    private val resendClient: Resend,
    private val fromEmail: String,
    private val fromName: String
) {
    
    private val logger = LoggerFactory.getLogger(InternalResendEmailService::class.java)
    
     suspend fun sendEmail(emailContent: EmailContent): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.info("Sending email to: ${emailContent.recipient} with subject: ${emailContent.subject}")
            
            val params = CreateEmailOptions.builder()
                .from("$fromName <$fromEmail>")
                .to(emailContent.recipient)
                .subject(emailContent.subject)
                .text("") // Empty string to prevent Resend from auto-generating plain text
                .html(emailContent.htmlBody)
                .addHeader("Content-Type", "text/html")
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
    
     suspend fun sendBulkEmails(emailContents: List<EmailContent>): Result<String> = withContext(Dispatchers.IO) {
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
                        .text("") // Empty string to prevent Resend from auto-generating plain text
                        .html(emailContent.htmlBody)
                        .addHeader("Content-Type", "text/html")
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

/**
 * Async email service that sends emails in background coroutines
 * This prevents email sending from blocking webhook responses, job processing, and other operations
 * 
 * This is the ONLY public interface for sending emails - all other code should use this service
 */
@Service
class AsyncEmailService(
    private val resendClient: Resend,
    @Value("\${resend.from-email}") private val fromEmail: String,
    @Value("\${resend.from-name}") private val fromName: String
) {
    
    // Internal email service instance - not injectable from outside
    private val internalResendEmailService = InternalResendEmailService(resendClient, fromEmail, fromName)
    
    companion object {
        private val logger = LoggerFactory.getLogger(AsyncEmailService::class.java)
    }
    
    /**
     * Send email asynchronously (fire-and-forget)
     * Returns immediately without waiting for email to be sent
     * Logs success/failure but doesn't propagate exceptions
     */
    fun sendEmailAsync(emailContent: EmailContent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = internalResendEmailService.sendEmail(emailContent)
                result.fold(
                    onSuccess = { 
                        logger.info("Successfully sent async email to: ${emailContent.recipient}")
                    },
                    onFailure = { exception ->
                        logger.error("Failed to send async email to: ${emailContent.recipient}", exception)
                        // TODO: Could implement retry logic or dead letter queue here
                    }
                )
            } catch (e: Exception) {
                logger.error("Error in async email sending to: ${emailContent.recipient}", e)
            }
        }
    }
    
    /**
     * Send email asynchronously and return a Deferred for those who want to await the result
     * Useful when you need to know if email succeeded but don't want to block immediately
     */
    fun sendEmailAsyncDeferred(emailContent: EmailContent): Deferred<Result<String>> {
        return CoroutineScope(Dispatchers.IO).async {
            try {
                internalResendEmailService.sendEmail(emailContent)
            } catch (e: Exception) {
                logger.error("Error in deferred async email sending to: ${emailContent.recipient}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Send multiple emails asynchronously in parallel
     * More efficient than sending one by one
     */
    fun sendBulkEmailsAsync(emailContents: List<EmailContent>) {
        if (emailContents.isEmpty()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send all emails in parallel
                val jobs = emailContents.map { emailContent ->
                    async {
                        try {
                            val result = internalResendEmailService.sendEmail(emailContent)
                            emailContent.recipient to result
                        } catch (e: Exception) {
                            emailContent.recipient to Result.failure<String>(e)
                        }
                    }
                }
                
                // Await all results and log them
                val results = jobs.awaitAll()
                var successCount = 0
                var failureCount = 0
                
                results.forEach { (recipient, result) ->
                    result.fold(
                        onSuccess = { 
                            successCount++
                            logger.debug("Bulk email success: $recipient")
                        },
                        onFailure = { exception ->
                            failureCount++
                            logger.error("Bulk email failed: $recipient", exception)
                        }
                    )
                }
                
                logger.info("Bulk email completed: $successCount successes, $failureCount failures")
                
            } catch (e: Exception) {
                logger.error("Error in bulk async email sending", e)
            }
        }
    }
    
    /**
     * Send email with retry logic (for critical emails)
     * Retries up to maxRetries times with exponential backoff
     */
    fun sendEmailWithRetry(
        emailContent: EmailContent, 
        maxRetries: Int = 3, 
        initialDelayMs: Long = 1000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 1
            var delay = initialDelayMs
            
            while (attempt <= maxRetries) {
                try {
                    val result = internalResendEmailService.sendEmail(emailContent)
                    if (result.isSuccess) {
                        logger.info("Successfully sent email with retry to: ${emailContent.recipient} on attempt $attempt")
                        return@launch
                    } else {
                        throw Exception("Email send failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    if (attempt == maxRetries) {
                        logger.error("Failed to send email to: ${emailContent.recipient} after $maxRetries attempts", e)
                        return@launch
                    }
                    
                    logger.warn("Email send attempt $attempt failed for: ${emailContent.recipient}, retrying in ${delay}ms", e)
                    delay(delay)
                    attempt++
                    delay *= 2 // Exponential backoff
                }
            }
        }
    }
}