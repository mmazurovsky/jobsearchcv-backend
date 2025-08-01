package com.jobsearchcv.backend.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*
import jakarta.annotation.PostConstruct

@Service
class EmailService(
    @Value("\${aws.ses.region:us-east-1}") private val awsRegion: String,
    @Value("\${aws.ses.from-email}") private val fromEmail: String,
    @Value("\${aws.ses.from-name:Job Search CV}") private val fromName: String
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private lateinit var sesClient: SesClient

    @PostConstruct
    fun init() {
        sesClient = SesClient.builder()
            .region(Region.of(awsRegion))
            .build()
    }

    fun sendEmail(emailContent: EmailContent): Boolean {
        return try {
            val destination = Destination.builder()
                .toAddresses(emailContent.recipient)
                .build()

            val content = Content.builder()
                .data(emailContent.subject)
                .build()

            val bodyBuilder = Body.builder()
                .html(Content.builder().data(emailContent.htmlBody).build())
                .text(Content.builder().data(emailContent.textBody).build())

            val message = Message.builder()
                .subject(content)
                .body(bodyBuilder.build())
                .build()

            val emailRequest = SendEmailRequest.builder()
                .source("$fromName <$fromEmail>")
                .destination(destination)
                .message(message)
                .build()

            val response = sesClient.sendEmail(emailRequest)
            logger.info("Email sent successfully to ${emailContent.recipient}, messageId: ${response.messageId()}")
            true
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

        emailContents.forEach { emailContent ->
            val success = sendEmail(emailContent)
            if (success) {
                successful.add(emailContent.recipient)
            } else {
                failed.add(emailContent.recipient to "Failed to send email")
            }
        }

        return BulkEmailResult(successful, failed)
    }

    data class BulkEmailResult(
        val successful: List<String>,
        val failed: List<Pair<String, String>>
    )
}