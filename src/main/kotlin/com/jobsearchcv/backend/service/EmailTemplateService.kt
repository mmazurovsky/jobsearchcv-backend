package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.TelegramMessages
import com.jobsearchcv.backend.domain.model.ScoredJobData
import com.jobsearchcv.backend.domain.model.EmailContent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EmailTemplateService(
    @Value("\${stripe.customer-portal-url}") private val customerPortalUrl: String,
    @Value("\${stripe.checkout-url}") private val stripeCheckoutUrl: String,
    private val urlService: UrlService
) {

    companion object {
        const val BUSINESS_NAME = "Antkowiak Services"
        const val BUSINESS_ADDRESS = "73262 Germany, Reichenbach an der Fils, Katherinenstr. 4"
    }

    private fun getEmailGroundText(): String {
        return "You're receiving this email because you have an active job alert created at ApplyFirst"
    }

    fun createJobNotificationEmail(
        recipient: String,
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String,
        specialMessage: String? = null,
        userId: String,
        isFreeTier: Boolean = false
    ): EmailContent {
        val subject = "New jobs: $searchName"

        val htmlBody = createHtmlEmail(searchName, jobs, alertId, specialMessage, userId, isFreeTier)
        val textBody = createPlainTextEmail(searchName, jobs, alertId, specialMessage, userId, isFreeTier)

        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    private fun createHtmlEmail(
        searchName: String,
        jobs: List<ScoredJobData>,
        alertId: String,
        specialMessage: String? = null,
        userId: String,
        isFreeTier: Boolean
    ): String = buildString {
        val highestScore = jobs.maxOfOrNull { it.compatibilityScore } ?: 0
        appendLine("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\")")
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
        appendLine("                                    <td style=\"font-size: 24px; font-weight: bold; padding-bottom: 24px;\">🎉 Found ${jobs.size} new jobs with highest compatibility score: $highestScore!</td>")
        appendLine("                                </tr>")
        if (specialMessage != null) {
            appendLine("                                <tr>")
            appendLine("                                    <td style=\"font-size: 16px; color: #059862; font-weight: bold; padding-bottom: 20px; background-color: #f8f9fa; padding: 12px; border-left: 4px solid #059862;\">$specialMessage</td>")
            appendLine("                                </tr>")
        }
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
                val techTags = job.techstack.map { tech ->
                    val tag = tech.replace(".", "").replace("/", "").replace(" ", "").lowercase()
                    "#$tag"
                }.joinToString("&nbsp;&nbsp;&nbsp;")
                appendLine("                                            <tr>")
                appendLine("                                                <td style=\"font-size: 12px; color: #666666; padding: 12px 0; line-height: 20px;\">$techTags</td>")
                appendLine("                                            </tr>")
            }
            if (job.tags.isNotEmpty()) {
                val tagPills = job.tags.joinToString("&nbsp;&nbsp;&nbsp;") { tag ->
                    tag.trim()
                }
                appendLine("                                            <tr>")
                appendLine("                                                <td style=\"font-size: 12px; color: #666666; padding-bottom: 12px; line-height: 20px;\">$tagPills</td>")
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

        // Add upgrade message for free tier users
        if (isFreeTier) {
            val upgradeUrl = "$stripeCheckoutUrl?client_reference_id=$userId"
            appendLine("                    <!-- Upgrade Message -->")
            appendLine("                    <tr>")
            appendLine("                        <td style=\"padding: 20px; padding-top: 0;\">")
            appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"background-color: #f8f9fa; border: 1px solid #e5e5e5; border-radius: 4px;\">")
            appendLine("                                <tr>")
            appendLine("                                    <td style=\"padding: 20px;\">")
            appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
            appendLine("                                            <tr>")
            appendLine("                                                <td style=\"font-size: 16px; color: #333333; line-height: 24px; padding-bottom: 16px;\">")
            appendLine("                                                    Users on the Free plan only receive job overviews once a month. Premium users receive alerts at their specified frequency. <strong>Upgrade to Premium to get your new job faster.</strong>")
            appendLine("                                                </td>")
            appendLine("                                            </tr>")
            appendLine("                                            <tr>")
            appendLine("                                                <td>")
            appendLine("                                                    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
            appendLine("                                                        <tr>")
            appendLine("                                                            <td style=\"background-color: #032DB3; padding: 12px 24px; border-radius: 3px;\">")
            appendLine("                                                                <a href=\"$upgradeUrl\" style=\"color: #ffffff; text-decoration: none; font-size: 16px; font-weight: bold;\">Upgrade to Premium</a>")
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
        appendLine("                                                    <a href=\"${urlService.getWebsiteUrl()}\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">ApplyFirst</a>")
        appendLine("                                                </td>")
        appendLine("                                                <td width=\"8\">&nbsp;</td>")
        appendLine("                                                <td style=\"background-color: #ffffff; border: 1px solid #e5e5e5; padding: 8px 16px;\">")
        appendLine("                                                    <a href=\"${urlService.getEditJobSearchUrl(alertId)}\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">Edit alert</a>")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        appendLine("                                        </table>")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"padding-bottom: 20px;\">")
        appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
        appendLine("                                            <tr>")
        appendLine("                                                <td style=\"background-color: #ffffff; border: 1px solid #e5e5e5; padding: 8px 16px;\">")
        appendLine("                                                    <a href=\"${urlService.getUnsubscribeUrl()}\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">Unsubscribe</a>")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        appendLine("                                        </table>")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"font-size: 12px; color: #666666; line-height: 18px;\">")
        appendLine("                                        ${getEmailGroundText()}<br/>")
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
        alertId: String,
        specialMessage: String? = null,
        userId: String,
        isFreeTier: Boolean = false
    ): String {
        val messageBody = TelegramMessages.getJobNotificationMessage(searchName, jobs, null)

        return buildString {
            val highestScore = jobs.maxOfOrNull { it.compatibilityScore } ?: 0
            appendLine("🎉 Found ${jobs.size} new jobs with highest compatibility score: $highestScore!")
            appendLine()
            if (specialMessage != null) {
                appendLine(specialMessage)
                appendLine()
            }
            appendLine("ApplyFirst - Your personalised Job Search AI Agent")
            appendLine()
            appendLine(messageBody)
            appendLine()

            // Add upgrade message for free tier users
            if (isFreeTier) {
                val upgradeUrl = "$stripeCheckoutUrl?client_reference_id=$userId"
                appendLine("---")
                appendLine()
                appendLine("Users on the Free plan only receive job overviews once a month. Premium users receive alerts at their specified frequency. Upgrade to Premium to get your new job faster.")
                appendLine()
                appendLine("Upgrade to Premium: $upgradeUrl")
                appendLine()
            }

            appendLine("---")
            appendLine()
            appendLine("ApplyFirst: ${urlService.getWebsiteUrl()}")
            appendLine("Edit alert: ${urlService.getEditJobSearchUrl(alertId)}")
            appendLine()
            appendLine("Unsubscribe: ${urlService.getUnsubscribeUrl()}")
            appendLine()
            appendLine(createPlainTextFooter())
        }
    }


    fun createTrialEndingEmail(recipient: String): EmailContent {
        val subject = "Your ApplyFirst trial ends in 7 days"
        val htmlBody = createSystemEmailHtml(
            title = "Your trial is ending soon",
            content = """
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Your 7-day ApplyFirst Premium trial will end in 7 days.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 16px;">Your subscription will automatically continue at $14/month to maintain premium features.</p>
                <p style="font-size: 16px; line-height: 24px; margin-bottom: 20px;">To cancel or manage your subscription, visit the customer portal.</p>
            """.trimIndent(),
            ctaText = "Manage Subscription",
            ctaUrl = customerPortalUrl
        )
        val textBody = createSystemEmailText(
            title = "Your trial is ending soon",
            content = "Your 7-day ApplyFirst Premium trial will end in 7 days. Your subscription will automatically continue at $14/month to maintain premium features. To cancel or manage your subscription, visit the customer portal."
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
        val subject = "Welcome to your 7-day ApplyFirst Premium trial! 🎉"
        val htmlBody = createWelcomeEmailHtml()
        val textBody = createWelcomeEmailText()
        return EmailContent(recipient, subject, htmlBody, textBody)
    }

    private fun createWelcomeEmailHtml(): String = buildString {
        appendLine("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">")
        appendLine("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
        appendLine("<head>")
        appendLine("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />")
        appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>")
        appendLine("    <title>Welcome to 7-day ApplyFirst Premium Trial</title>")
        appendLine("    <style type=\"text/css\">")
        appendLine("        /* Mobile responsive styles */")
        appendLine("        @media only screen and (max-width: 600px) {")
        appendLine("            table[class=\"container\"] {")
        appendLine("                width: 100% !important;")
        appendLine("                min-width: 320px !important;")
        appendLine("            }")
        appendLine("            td[class=\"mobile-padding\"] {")
        appendLine("                padding: 15px !important;")
        appendLine("            }")
        appendLine("            td[class=\"mobile-title\"] {")
        appendLine("                font-size: 20px !important;")
        appendLine("                line-height: 26px !important;")
        appendLine("            }")
        appendLine("            td[class=\"mobile-text\"] {")
        appendLine("                font-size: 14px !important;")
        appendLine("                line-height: 20px !important;")
        appendLine("            }")
        appendLine("            td[class=\"mobile-button\"] {")
        appendLine("                padding: 10px 18px !important;")
        appendLine("            }")
        appendLine("        }")
        appendLine("        /* Prevent iOS auto-linking */")
        appendLine("        .appleLinks a {")
        appendLine("            color: inherit !important;")
        appendLine("            text-decoration: none !important;")
        appendLine("        }")
        appendLine("    </style>")
        appendLine("</head>")
        appendLine("<body style=\"margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; color: #000000; background-color: #ffffff; -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%;\">")
        appendLine("    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"background-color: #ffffff;\">")
        appendLine("        <tr>")
        appendLine("            <td align=\"center\">")
        appendLine("                <table class=\"container\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"max-width: 600px; background-color: #ffffff;\">")
        
        // Header (mobile responsive)
        appendLine("                    <tr>")
        appendLine("                        <td class=\"mobile-padding\" style=\"padding: 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        appendLine("                                <tr>")
        appendLine("                                    <td class=\"mobile-text\" style=\"font-size: 14px; color: #666666; padding-bottom: 30px;\">Your personalised Job Search AI Agent</td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td class=\"mobile-title\" style=\"font-size: 24px; font-weight: bold; padding-bottom: 24px;\">🎉 Welcome to your 7-day Premium trial!</td>")
        appendLine("                                </tr>")
        appendLine("                            </table>")
        appendLine("                        </td>")
        appendLine("                    </tr>")
        
        // Main content card (mobile responsive)
        appendLine("                    <tr>")
        appendLine("                        <td class=\"mobile-padding\" style=\"padding: 0 20px 20px 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border: 1px solid #e5e5e5; background-color: #ffffff;\">")
        appendLine("                                <tr>")
        appendLine("                                    <td class=\"mobile-padding\" style=\"padding: 20px;\">")
        appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        
        // Trial info
        appendLine("                                            <tr>")
        appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 16px; line-height: 24px; color: #000000; padding-bottom: 20px;\">")
        appendLine("                                                    Your <strong>7-day free trial</strong> has started! You now have access to all Premium features with no charges until your trial ends.")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        
        // Premium features header
        appendLine("                                            <tr>")
        appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 16px; font-weight: bold; color: #000000; padding-bottom: 12px;\">")
        appendLine("                                                    What's included in Premium:")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        
        // Feature list (mobile responsive)
        val premiumFeatures = listOf(
            "🤖 AI-powered job matching and compatibility scoring",
            "🔄 Continuous real-time monitoring of job boards for jobs matching your job searches and preferences",
            "📧 Email alerts with your specified frequency set in your job searches",
        )
        
        premiumFeatures.forEach { feature ->
            appendLine("                                            <tr>")
            appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 14px; color: #666666; padding-bottom: 8px; line-height: 20px;\">$feature</td>")
            appendLine("                                            </tr>")
        }
        
        // Trial terms (mobile responsive)
        appendLine("                                            <tr>")
        appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 14px; color: #000000; padding: 20px 0 12px 0; border-top: 1px solid #e5e5e5;\">")
        appendLine("                                                    <strong>After your 7-day trial:</strong>")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        appendLine("                                            <tr>")
        appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 14px; color: #666666; padding-bottom: 8px;\">• Your subscription will automatically continue at <strong style=\"color: #059862;\">$14/month</strong></td>")
        appendLine("                                            </tr>")
        appendLine("                                            <tr>")
        appendLine("                                                <td class=\"mobile-text\" style=\"font-size: 14px; color: #666666; padding-bottom: 16px;\">• Cancel anytime before trial ends to avoid charges</td>")
        appendLine("                                            </tr>")
        
        // CTA button (mobile responsive)
        appendLine("                                            <tr>")
        appendLine("                                                <td style=\"padding-top: 12px;\">")
        appendLine("                                                    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        appendLine("                                                        <tr>")
        appendLine("                                                            <td align=\"center\">")
        appendLine("                                                                <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
        appendLine("                                                                    <tr>")
        appendLine("                                                                        <td class=\"mobile-button\" style=\"background-color: #000000; padding: 12px 24px; border-radius: 3px;\">")
        appendLine("                                                                            <a href=\"${urlService.getWebsiteUrl()}\" style=\"color: #ffffff; text-decoration: none; font-size: 16px; font-weight: bold; display: block;\">Start Your Job Search →</a>")
        appendLine("                                                                        </td>")
        appendLine("                                                                    </tr>")
        appendLine("                                                                </table>")
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
        
        // Footer (mobile responsive)
        appendLine("                    <tr>")
        appendLine("                        <td class=\"mobile-padding\" style=\"padding: 20px;\">")
        appendLine("                            <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
        appendLine("                                <tr>")
        appendLine("                                    <td style=\"padding-bottom: 20px; text-align: center;\">")
        appendLine("                                        <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin: 0 auto;\">")
        appendLine("                                            <tr>")
        appendLine("                                                <td style=\"background-color: #ffffff; border: 1px solid #e5e5e5; padding: 8px 16px; border-radius: 3px;\">")
        appendLine("                                                    <a href=\"$customerPortalUrl\" style=\"color: #666666; text-decoration: none; font-size: 14px;\">Manage Subscription</a>")
        appendLine("                                                </td>")
        appendLine("                                            </tr>")
        appendLine("                                        </table>")
        appendLine("                                    </td>")
        appendLine("                                </tr>")
        appendLine("                                <tr>")
        appendLine("                                    <td class=\"mobile-text appleLinks\" style=\"font-size: 12px; color: #666666; line-height: 18px; text-align: center;\">")
        appendLine("                                        ${getEmailGroundText()}<br/>")
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

    private fun createWelcomeEmailText(): String {
        return buildString {
            appendLine("🎉 Welcome to your 7-day ApplyFirst Premium trial!")
            appendLine()
            appendLine("Your 7-day free trial has started! You now have access to all Premium features with no charges until your trial ends.")
            appendLine()
            appendLine("What's included in Premium:")
            appendLine("• AI-powered job matching and compatibility scoring")
            appendLine("• Continuous real-time monitoring of job boards for jobs matching your job searches and preferences")
            appendLine("• Email alerts with your specified frequency set in your job searches")
            appendLine()
            appendLine("After your 7-day trial:")
            appendLine("• Your subscription will automatically continue at $14/month")
            appendLine("• Cancel anytime before trial ends to avoid charges")
            appendLine()
            appendLine("Start your job search: ${urlService.getWebsiteUrl()}")
            appendLine("Manage subscription: $customerPortalUrl")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(createPlainTextFooter())
        }
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
            ctaUrl = urlService.getWebsiteUrl()
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
        appendLine("                                        ${getEmailGroundText()}<br/>")
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
            appendLine(getEmailGroundText())
            appendLine(BUSINESS_NAME)
            appendLine(BUSINESS_ADDRESS)
        }
    }
}

