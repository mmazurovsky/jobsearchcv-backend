package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.EmailContent

interface EmailSender {
    suspend fun sendEmail(message: String): Result<String>
    suspend fun sendEmail(emailContent: EmailContent): Result<String>
    suspend fun sendBulkEmails(emailContents: List<EmailContent>): Result<String>
}