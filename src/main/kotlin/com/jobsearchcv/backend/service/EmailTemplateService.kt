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
        appendLine("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")
        appendLine("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
        appendLine("<head>")
        appendLine("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />")
        appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>")
        appendLine("    <title>New Job Opportunities</title>")
        appendLine("</head>")
        appendLine("<body style=\"margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; color: #000000; background-color: #ffffff;\">")
        appendLine("    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"background-color: #ffffff;\">")
        appendLine("        <tr>")
        appendLine("            <td align=\"center\">")
        appendLine("                <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"max-width: 600px; background-color: #ffffff;\">")
        appendLine("                    <!-- Header -->")
        appendLine("                    <tr>")
        appendLine("                        <td style=\"padding: 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"font-size: 20px; font-weight: bold; padding-bottom: 8px;\">")
        appendLine("                                        <a href=\"https://applyfirst.app\" style=\"color: #000000; text-decoration: none;\">ApplyFirst</a>")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"font-size: 14px; color: #666666; padding-bottom: 30px;\">${TelegramMessages.SERVICE_SHORT_DESCRIPTION}</td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"font-size: 24px; font-weight: bold; padding-bottom: 24px;\">🎉 Found ${jobs.size} new jobs for $searchName!</td>")
        appendLine("                                </tr>")
        appendLine("                            </table>")
        appendLine("                        </td>")
        appendLine("                    </tr>")
        appendLine("                    ")

        jobs.forEach { job ->
            appendLine("                    <!-- Job Card -->")
            appendLine("                    <tr>")
            appendLine("                        <td style=\"padding: 0 20px 20px 20px;\">")
            appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border: 1px solid #e5e5e5; background-color: #ffffff;\">")
            appendLine("                                <tr>")
            appendLine("                                    <td style=\"padding: 20px;\">")
            appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 14px; font-weight: bold; color: #000000; padding-bottom: 8px;\">Your compatibility: ${job.compatibilityScore}</td>")
            appendLine("                                            </tr>")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 18px; font-weight: bold; color: #000000; padding-bottom: 8px;\">${job.title}</td>")
            appendLine("                                            </tr>")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 16px; color: #666666; padding-bottom: 4px;\">${job.company}</td>")
            appendLine("                                            </tr>")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 14px; color: #666666; padding-bottom: 8px;\">📍 ${job.location}</td>")
            appendLine("                                            </tr>")
            if (job.salary != null && job.salary.isNotBlank()) {
                appendLine("                                            <tr>")
                appendLine("                                                <td style=\"font-size: 14px; color: #059862; font-weight: bold; padding-bottom: 4px;\">💵 ${job.salary}</td>")
                appendLine("                                            </tr>")
            }
            if (job.applicants.isNotBlank()) {
                appendLine("                                            <tr>")
                appendLine("                                                <td style=\"font-size: 14px; color: #666666; padding-bottom: 8px;\">🙋‍♂️ ${job.applicants}</td>")
                appendLine("                                            </tr>")
            }
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 14px; color: #666666; padding-bottom: 8px;\">⌛ ${job.createdAgo}</td>")
            appendLine("                                            </tr>")
            if (job.techstack.isNotEmpty()) {
                val tags = job.techstack.map { tech ->
                    val tag = tech.replace(".", "").replace("/", "").replace(" ", "").lowercase()
                    "#$tag"
                }.joinToString("&nbsp;&nbsp;&nbsp;")
                appendLine("                                            <tr>")
                appendLine("                                                <td style=\"font-size: 12px; color: #666666; padding: 12px 0; line-height: 20px;\">$tags</td>")
                appendLine("                                            </tr>")
            }
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"padding-top: 12px;\">")
            appendLine("                                                    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
            appendLine("                                                        <tr>")
            appendLine("                                                            <td style=\"background-color: #000000; padding: 8px 16px;\">")
            appendLine("                                                                <a href=\"${job.link}\" style=\"color: #ffffff; text-decoration: none; font-size: 14px; font-weight: bold;\">View Job →</a>")
            appendLine("                                                            </td>")
            appendLine("                                                        </tr>")
            appendLine("                                                    </table>")
            appendLine("                                                </td>")
            appendLine("                                            </tr>")
            appendLine("                                        </table>")
            appendLine("                                    </td>")
            appendLine("                                </tr>")
            appendLine("                            </table>")
            appendLine("                        </td>")
            appendLine("                    </tr>")
        }

        appendLine("                    <!-- Footer -->")
        appendLine("                    <tr>")
        appendLine("                        <td style=\"padding: 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"padding-bottom: 20px;\">")
        appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
        appendLine("                                            <tr>")
        appendLine("                                                <td style=\"background-color: #ffffff; border: 1px solid #e5e5e5; padding: 8px 16px;\">")
        appendLine("                                                    <a href=\"#edit-alert-$alertId\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">Edit alert</a>")
        appendLine("                                                </td>")
        appendLine("                                                <td width=\"8\">&nbsp;</td>")
        appendLine("                                                <td style=\"background-color: #ffffff; border: 1px solid #e5e5e5; padding: 8px 16px;\">")
        appendLine("                                                    <a href=\"#unsubscribe-$alertId\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">Unsubscribe</a>")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        appendLine("                                        </table>")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"font-size: 12px; color: #666666; line-height: 18px;\">")
        appendLine("                                        $EMAIL_GROUND_TEXT<br/>")
        appendLine("                                        $BUSINESS_NAME<br/>")
        appendLine("                                        $BUSINESS_ADDRESS")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                            </table>")
        appendLine("                        </td>")
        appendLine("                    </tr>")
        appendLine("                </table>")
        appendLine("            </td>")
        appendLine("        </tr>")
        appendLine("    </table>")
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


    private fun createPlainTextFooter(): String {
        return buildString {
            appendLine(EMAIL_GROUND_TEXT)
            appendLine(BUSINESS_NAME)
            appendLine(BUSINESS_ADDRESS)
        }
    }
}


