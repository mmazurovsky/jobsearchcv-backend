package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.TelegramMessages
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.EmailContent
import org.springframework.stereotype.Service

@Service
class EmailTemplateService {

    companion object {
        const val BUSINESS_NAME = "Antkowiak Services GbR"
        const val BUSINESS_ADDRESS = "73262 Germany, Reichenbach an der Fils, Katherinenstr. 4"
        const val EMAIL_GROUND_TEXT = "You're receiving this email because you have an active job alert created at applyfirst.app"
    }

    fun createJobNotificationEmail(
        recipient: String,
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String
    ): EmailContent {
        val subject = "🎉 Found ${jobs.size} new jobs for $searchName!"

        val htmlBody = createHtmlEmail(searchName, jobs, alertId)
        val textBody = createPlainTextEmail(searchName, jobs, alertId)

        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    private fun createHtmlEmail(
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
        appendLine("    <link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap' rel='stylesheet'>")
        appendLine("    <style>")
        appendLine("        body {")
        appendLine("            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;")
        appendLine("            line-height: 1.6;")
        appendLine("            color: #000000;")
        appendLine("            background-color: #ffffff;")
        appendLine("            margin: 0;")
        appendLine("            padding: 0;")
        appendLine("            text-align: left;")
        appendLine("        }")
        appendLine("        .container {")
        appendLine("            max-width: 600px;")
        appendLine("            margin: 0 auto;")
        appendLine("            padding: 20px;")
        appendLine("            text-align: left;")
        appendLine("        }")
        appendLine("        .brand-name {")
        appendLine("            margin: 0 0 8px 0;")
        appendLine("            text-align: left;")
        appendLine("        }")
        appendLine("        .brand-name a {")
        appendLine("            font-family: 'Inter', sans-serif;")
        appendLine("            font-size: 20px;")
        appendLine("            font-weight: 700;")
        appendLine("            color: #000000;")
        appendLine("            text-decoration: none;")
        appendLine("        }")
        appendLine("        .brand-name a:hover {")
        appendLine("            text-decoration: underline;")
        appendLine("        }")
        appendLine("        .header {")
        appendLine("            padding-bottom: 20px;")
        appendLine("            margin-bottom: 30px;")
        appendLine("            text-align: left;")
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
        appendLine("            background-color: #ffffff;")
        appendLine("            text-align: left;")
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
        appendLine("            color: #000000;")
        appendLine("            font-size: 14px;")
        appendLine("            font-weight: 600;")
        appendLine("            margin-bottom: 8px;")
        appendLine("        }")
        appendLine("        .footer {")
        appendLine("            margin-top: 20px;")
        appendLine("            padding-top: 20px;")
        appendLine("            text-align: left;")
        appendLine("            color: #666666;")
        appendLine("            font-size: 14px;")
        appendLine("        }")
        appendLine("        .footer-buttons {")
        appendLine("            margin: 20px 0;")
        appendLine("            text-align: left;")
        appendLine("        }")
        appendLine("        .footer-button {")
        appendLine("            display: inline-block;")
        appendLine("            background-color: #ffffff;")
        appendLine("            color: #666666;")
        appendLine("            text-decoration: none;")
        appendLine("            padding: 8px 16px;")
        appendLine("            border: 1px solid #e5e5e5;")
        appendLine("            border-radius: 6px;")
        appendLine("            font-size: 14px;")
        appendLine("            margin: 0 8px 0 0;")
        appendLine("        }")
        appendLine("        .footer-button:hover {")
        appendLine("            background-color: #f5f5f5;")
        appendLine("            color: #333333;")
        appendLine("        }")
        appendLine("    </style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("    <div class=\"container\">")
        appendLine("        <div class=\"brand-name\"><a href=\"https://applyfirst.app\">ApplyFirst</a></div>")
        appendLine("        <div style=\"color: #666666; font-size: 14px; margin-bottom: 30px; text-align: left;\">${TelegramMessages.SERVICE_SHORT_DESCRIPTION}</div>")
        appendLine("        ")
        appendLine("        <h2 style=\"margin-bottom: 24px;\">🎉 Found ${jobs.size} new jobs for $searchName!</h2>")
        appendLine("        ")

        jobs.forEach { job ->
            appendLine("        <div class=\"job-card\">")
            appendLine("            <div class=\"compatibility\">Your compatibility: ${job.compatibilityScore}</div>")
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
        append(createEmailFooter())
        appendLine("        </div>")
        appendLine("    </div>")
        appendLine("</body>")
        appendLine("</html>")
    }


    private fun createPlainTextEmail(
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String
    ): String {
        val messageBody = TelegramMessages.getJobNotificationMessage(searchName, jobs, null)

        return buildString {
            appendLine("🎉 Found ${jobs.size} new jobs for $searchName!")
            appendLine()
            appendLine("ApplyFirst - Your personalised Job Search AI Agent")
            appendLine()
            appendLine(messageBody)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("Edit alert: #edit-alert-$alertId")
            appendLine("Unsubscribe: #unsubscribe-$alertId")
            appendLine()
            appendLine(createPlainTextFooter())
        }
    }

    private fun createEmailFooter(): String {
        return buildString {
            appendLine("            <div style=\"font-size: 12px; color: #666666; margin-top: 20px; text-align: left;\">")
            appendLine("                <div style=\"margin-bottom: 10px;\">$EMAIL_GROUND_TEXT</div>")
            appendLine("                <div>$BUSINESS_NAME</div>")
            appendLine("                <div>$BUSINESS_ADDRESS</div>")
            appendLine("            </div>")
        }
    }

    private fun createPlainTextFooter(): String {
        return buildString {
            appendLine(EMAIL_GROUND_TEXT)
            appendLine(BUSINESS_NAME)
            appendLine(BUSINESS_ADDRESS)
        }
    }
}


