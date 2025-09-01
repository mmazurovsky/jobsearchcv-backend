package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.TelegramMessages
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.EmailContent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EmailTemplateService(
    @Value("\${stripe.customer-portal-url}") private val customerPortalUrl: String
) {

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


    fun createTrialEndingEmail(recipient: String): EmailContent {
        val subject = "Your ApplyFirst trial ends in 3 days"
        val htmlBody = createSystemEmailHtml(
            title = "Your trial is ending soon",
            content = """
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Your 3-day ApplyFirst Premium trial will end in 3 days.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Your subscription will automatically continue at $14/month to maintain premium features.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 20px;">To cancel or manage your subscription, visit the customer portal.</p>
            """.trimIndent(),
            ctaText = "Manage Subscription",
            ctaUrl = customerPortalUrl
        )
        val textBody = createSystemEmailText(
            title = "Your trial is ending soon",
            content = "Your 3-day ApplyFirst Premium trial will end in 3 days. Your subscription will automatically continue at $14/month to maintain premium features. To cancel or manage your subscription, visit the customer portal."
        )
        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    fun createPaymentFailedEmail(recipient: String): EmailContent {
        val subject = "Payment failed - Update your payment method"
        val htmlBody = createSystemEmailHtml(
            title = "Payment Failed",
            content = """
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">We couldn't process your ApplyFirst Premium payment.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Your premium access has been suspended. Update your payment method to restore access.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 20px;">Need help? Reply to this email.</p>
            """.trimIndent(),
            ctaText = "Update Payment Method",
            ctaUrl = customerPortalUrl
        )
        val textBody = createSystemEmailText(
            title = "Payment Failed",
            content = "We couldn't process your ApplyFirst Premium payment. Your premium access has been suspended. Update your payment method to restore access. Need help? Reply to this email."
        )
        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    fun createWelcomeEmail(recipient: String): EmailContent {
        val subject = "Welcome to ApplyFirst Premium!"
        val htmlBody = createSystemEmailHtml(
            title = "Welcome to Premium!",
            content = """
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Thank you for subscribing to ApplyFirst Premium!</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">You now have access to premium features including continuous job monitoring and priority support.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 20px;">Start by creating your first job alert to get personalized job matches delivered to your inbox.</p>
            """.trimIndent(),
            ctaText = "Create Job Alert",
            ctaUrl = "https://applyfirst.app"
        )
        val textBody = createSystemEmailText(
            title = "Welcome to Premium!",
            content = "Thank you for subscribing to ApplyFirst Premium! You now have access to premium features including continuous job monitoring and priority support. Start by creating your first job alert to get personalized job matches delivered to your inbox."
        )
        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    fun createNoResultsEmail(recipient: String, searchName: String, timePeriod: String): EmailContent {
        val subject = "No new jobs found for $searchName"
        val htmlBody = createSystemEmailHtml(
            title = "No new jobs found",
            content = """
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">We searched for new jobs matching your criteria for <strong>$searchName</strong> in the last $timePeriod, but didn't find any new matches.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">This could mean:</p>
                <ul style="font-size: 16px; line-height: 24px; margin-bottom: 16px; padding-left: 20px;">
                    <li>All available jobs were already sent to you</li>
                    <li>No new jobs were posted in this time period</li>
                    <li>New jobs didn't meet your compatibility requirements</li>
                </ul>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 20px;">Try adjusting your search criteria or check back later for new opportunities.</p>
            """.trimIndent(),
            ctaText = "Edit Job Alert",
            ctaUrl = "https://applyfirst.app"
        )
        val textBody = createSystemEmailText(
            title = "No new jobs found",
            content = "We searched for new jobs matching your criteria for $searchName in the last $timePeriod, but didn't find any new matches. This could mean all available jobs were already sent, no new jobs were posted, or new jobs didn't meet your compatibility requirements. Try adjusting your search criteria or check back later."
        )
        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    private fun createSystemEmailHtml(
        title: String,
        content: String,
        ctaText: String? = null,
        ctaUrl: String? = null
    ): String = buildString {
        appendLine("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")
        appendLine("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
        appendLine("<head>")
        appendLine("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />")
        appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>")
        appendLine("    <title>$title</title>")
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
        appendLine("                                    <td style=\"font-size: 24px; font-weight: bold; padding-bottom: 24px;\">$title</td>")
        appendLine("                                </tr>")
        appendLine("                            </table>")
        appendLine("                        </td>")
        appendLine("                    </tr>")
        appendLine("                    <!-- Content -->")
        appendLine("                    <tr>")
        appendLine("                        <td style=\"padding: 0 20px 20px 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border: 1px solid #e5e5e5; background-color: #ffffff;\">")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"padding: 20px;\">")
        appendLine("                                        $content")
        if (ctaText != null && ctaUrl != null) {
            appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top: 20px;\">")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"background-color: #000000; padding: 12px 24px;\">")
            appendLine("                                                    <a href=\"$ctaUrl\" style=\"color: #ffffff; text-decoration: none; font-size: 16px; font-weight: bold;\">$ctaText</a>")
            appendLine("                                                </td>")
            appendLine("                                            </tr>")
            appendLine("                                        </table>")
        }
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                            </table>")
        appendLine("                        </td>")
        appendLine("                    </tr>")
        appendLine("                    <!-- Footer -->")
        appendLine("                    <tr>")
        appendLine("                        <td style=\"padding: 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
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

    private fun createSystemEmailText(title: String, content: String): String {
        return buildString {
            appendLine(title)
            appendLine()
            appendLine("ApplyFirst - Your personalised Job Search AI Agent")
            appendLine()
            appendLine(content)
            appendLine()
            appendLine("---")
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


