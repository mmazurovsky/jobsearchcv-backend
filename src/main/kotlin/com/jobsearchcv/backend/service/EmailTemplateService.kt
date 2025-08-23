package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.TelegramMessages
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.service.EmailTemplateService.Companion.BUSINESS_ADDRESS
import org.springframework.stereotype.Service

@Service
class EmailTemplateService {

    companion object {
        const val BUSINESS_ADDRESS = "Job Search CV, 123 Tech Street, San Francisco, CA 94105, USA"
    }

    fun createJobNotificationEmail(
        recipient: String,
        username: String,
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String
    ): EmailContent {
        val subject = "🎉 Found ${jobs.size} new jobs for $searchName!"

        val htmlBody = createHtmlEmail(username, searchName, jobs, alertId)
        val textBody = createPlainTextEmail(username, searchName, jobs, alertId)

        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    private fun createHtmlEmail(
        username: String,
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String
    ): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\">")
        appendLine("<head>")
        appendLine("    <meta charset=\"UTF-8\">")
        appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        appendLine("    <title>New Job Opportunities</title>")
        appendLine("    <style>")
        appendLine("        body {")
        appendLine("            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;")
        appendLine("            line-height: 1.6;")
        appendLine("            color: #000000;")
        appendLine("            background-color: #ffffff;")
        appendLine("            margin: 0;")
        appendLine("            padding: 0;")
        appendLine("        }")
        appendLine("        .container {")
        appendLine("            max-width: 600px;")
        appendLine("            margin: 0 auto;")
        appendLine("            padding: 20px;")
        appendLine("        }")
        appendLine("        .header {")
        appendLine("            border-bottom: 1px solid #e5e5e5;")
        appendLine("            padding-bottom: 20px;")
        appendLine("            margin-bottom: 30px;")
        appendLine("        }")
        appendLine("        .header h1 {")
        appendLine("            margin: 0;")
        appendLine("            font-size: 24px;")
        appendLine("            font-weight: 600;")
        appendLine("        }")
        appendLine("        .job-card {")
        appendLine("            border: 1px solid #e5e5e5;")
        appendLine("            border-radius: 8px;")
        appendLine("            padding: 20px;")
        appendLine("            margin-bottom: 20px;")
        appendLine("            background-color: #fafafa;")
        appendLine("        }")
        appendLine("        .job-title {")
        appendLine("            font-size: 18px;")
        appendLine("            font-weight: 600;")
        appendLine("            margin-bottom: 8px;")
        appendLine("            color: #000000;")
        appendLine("        }")
        appendLine("        .job-company {")
        appendLine("            font-size: 16px;")
        appendLine("            color: #666666;")
        appendLine("            margin-bottom: 4px;")
        appendLine("        }")
        appendLine("        .job-location {")
        appendLine("            font-size: 14px;")
        appendLine("            color: #666666;")
        appendLine("            margin-bottom: 8px;")
        appendLine("        }")
        appendLine("        .job-salary {")
        appendLine("            font-size: 14px;")
        appendLine("            color: #059862;")
        appendLine("            font-weight: 500;")
        appendLine("            margin-bottom: 4px;")
        appendLine("        }")
        appendLine("        .job-meta {")
        appendLine("            font-size: 14px;")
        appendLine("            color: #666666;")
        appendLine("            margin-bottom: 8px;")
        appendLine("        }")
        appendLine("        .job-tags {")
        appendLine("            margin-top: 12px;")
        appendLine("            margin-bottom: 12px;")
        appendLine("        }")
        appendLine("        .job-tag {")
        appendLine("            display: inline-block;")
        appendLine("            background-color: #e5e5e5;")
        appendLine("            color: #333333;")
        appendLine("            padding: 4px 8px;")
        appendLine("            border-radius: 4px;")
        appendLine("            font-size: 12px;")
        appendLine("            margin-right: 6px;")
        appendLine("            margin-bottom: 6px;")
        appendLine("        }")
        appendLine("        .job-link {")
        appendLine("            display: inline-block;")
        appendLine("            background-color: #000000;")
        appendLine("            color: #ffffff;")
        appendLine("            text-decoration: none;")
        appendLine("            padding: 8px 16px;")
        appendLine("            border-radius: 6px;")
        appendLine("            font-size: 14px;")
        appendLine("            margin-top: 12px;")
        appendLine("        }")
        appendLine("        .job-link:hover {")
        appendLine("            background-color: #333333;")
        appendLine("        }")
        appendLine("        .compatibility {")
        appendLine("            display: inline-block;")
        appendLine("            background-color: #000000;")
        appendLine("            color: #ffffff;")
        appendLine("            padding: 4px 12px;")
        appendLine("            border-radius: 4px;")
        appendLine("            font-size: 12px;")
        appendLine("            font-weight: 600;")
        appendLine("            margin-bottom: 12px;")
        appendLine("        }")
        appendLine("        .footer {")
        appendLine("            margin-top: 40px;")
        appendLine("            padding-top: 20px;")
        appendLine("            border-top: 1px solid #e5e5e5;")
        appendLine("            text-align: center;")
        appendLine("            color: #666666;")
        appendLine("            font-size: 14px;")
        appendLine("        }")
        appendLine("        .footer-buttons {")
        appendLine("            margin: 20px 0;")
        appendLine("        }")
        appendLine("        .footer-button {")
        appendLine("            display: inline-block;")
        appendLine("            background-color: #ffffff;")
        appendLine("            color: #000000;")
        appendLine("            text-decoration: none;")
        appendLine("            padding: 8px 16px;")
        appendLine("            border: 1px solid #e5e5e5;")
        appendLine("            border-radius: 6px;")
        appendLine("            font-size: 14px;")
        appendLine("            margin: 0 8px;")
        appendLine("        }")
        appendLine("        .footer-button:hover {")
        appendLine("            background-color: #f5f5f5;")
        appendLine("        }")
        appendLine("    </style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("    <div class=\"container\">")
        appendLine("        <div class=\"header\">")
        appendLine("            <h1>Hey $username</h1>")
        appendLine("        </div>")
        appendLine("        ")
        appendLine("        <h2 style=\"margin-bottom: 24px;\">🎉 Found ${jobs.size} new jobs for $searchName!</h2>")
        appendLine("        ")

        jobs.forEach { job ->
            appendLine("        <div class=\"job-card\">")
            appendLine("            <div class=\"compatibility\">Compatibility: ${job.compatibilityScore}</div>")
            appendLine("            <div class=\"job-title\">${job.title}</div>")
            appendLine("            <div class=\"job-company\">${job.company}</div>")
            appendLine("            <div class=\"job-location\">📍 ${job.location}</div>")
            if (job.salary != null && job.salary.isNotBlank()) {
                appendLine("            <div class=\"job-salary\">💵 ${job.salary}</div>")
            }
            if (job.applicants.isNotBlank()) {
                appendLine("            <div class=\"job-meta\">🙋‍♂️ ${job.applicants}</div>")
            }
            appendLine("            <div class=\"job-meta\">⌛ ${job.createdAgo}</div>")
            if (job.techstack.isNotEmpty()) {
                appendLine("            <div class=\"job-tags\">")
                job.techstack.forEach { tech ->
                    val tag = tech.replace(".", "").replace("/", "").replace(" ", "").lowercase()
                    appendLine("                <span class=\"job-tag\">#$tag</span>")
                }
                appendLine("            </div>")
            }
            appendLine("            <a href=\"${job.link}\" class=\"job-link\">View Job →</a>")
            appendLine("        </div>")
        }

        appendLine("        ")
        appendLine("        <div class=\"footer\">")
        appendLine("            <div class=\"footer-buttons\">")
        appendLine("                <a href=\"#edit-alert-$alertId\" class=\"footer-button\">Edit alert</a>")
        appendLine("                <a href=\"#unsubscribe-$alertId\" class=\"footer-button\">Unsubscribe</a>")
        appendLine("            </div>")
        appendLine("            <p>${TelegramMessages.BOT_SHORT_DESCRIPTION}</p>")
        appendLine("            <p style=\"font-size: 12px; color: #999999;\">$BUSINESS_ADDRESS</p>")
        appendLine("        </div>")
        appendLine("    </div>")
        appendLine("</body>")
        appendLine("</html>")
    }


    private fun createPlainTextEmail(
        username: String,
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String
    ): String {
        val messageBody = TelegramMessages.getJobNotificationMessage(searchName, jobs, null)

        return buildString {
            appendLine("Hey $username")
            appendLine()
            appendLine(messageBody)
            appendLine()
            appendLine("---")
            appendLine(TelegramMessages.BOT_SHORT_DESCRIPTION)
            appendLine()
            appendLine("Edit alert: #edit-alert-$alertId")
            appendLine("Unsubscribe: #unsubscribe-$alertId")
            appendLine()
            appendLine(BUSINESS_ADDRESS)
        }
    }
}


data class EmailContent(
    val recipient: String,
    val subject: String,
    val htmlBody: String,
    val textBody: String
)