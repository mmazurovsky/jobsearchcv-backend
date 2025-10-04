package com.jobsearchcv.backend

import com.jobsearchcv.backend.domain.model.*

/**
 * Centralized class for all application messages and string templates.
 * Contains static constants and dynamic methods for generating user-facing text.
 */
object TelegramMessages {

    // ================== STATIC CONSTANTS ==================

    // === Commands ===
    const val CMD_CANCEL = "/cancel"
    const val CMD_START = "/start"
    const val CMD_HELP = "/help"
    const val CMD_CREATE_ALERT = "/create_alert"
    const val CMD_LIST_ALERTS = "/list_alerts"
    const val CMD_EDIT_ALERT = "/edit_alert"
    const val CMD_DELETE_ALERT = "/delete_alert"
    const val CMD_SEARCH_NOW = "/search_now"
    const val CMD_PRIVACY = "/privacy"
    const val CMD_SUPPORT = "/support"

    const val CANCEL_MESSAGE = "❌ Operation cancelled."
    const val USE_CANCEL_TO_ABORT = "Use $CMD_CANCEL to abort this operation."

    // === Error Messages ===
    const val ERROR_GENERAL =
        "❌ An error occurred while processing your request. Please try again or use $CMD_CANCEL to abort."
    const val ERROR_RETRIEVAL = "❌ Error retrieving your job alerts. Please try again later."
    const val ERROR_PROCESSING = "❌ Error processing your request. Please try again later."
    const val ERROR_ALERT_CREATION_FAILED =
        "❌ Failed to create job alert. Please try again later or contact support."
    const val ERROR_IMMEDIATE_SEARCH_CREATION_FAILED =
        "❌ Failed to start job search. Please try again later or contact support."
    const val ERROR_UPDATE_FAILED =
        "❌ Failed to update job alert. Please try again later or contact support."
    const val ERROR_DELETION_FAILED = "❌ Failed to delete alert(s). Please try again later."
    const val ERROR_NO_PENDING_ALERT =
        "❌ No pending job alert found. Please start over with $CMD_CREATE_ALERT"
    const val ERROR_NO_PENDING_SEARCH =
        "❌ No pending job search found. Please start over with $CMD_SEARCH_NOW"
    const val ERROR_DISPLAY_HELP = "❌ Error displaying help. Please try again later."
    const val ERROR_DISPLAY_WELCOME = "❌ Error displaying welcome message. Please try again later."
    const val ERROR_DISPLAY_MENU = "❌ Error displaying menu. Please try again later."
    const val ERROR_DISPLAY_PRIVACY = "❌ Error displaying privacy policy. Please try again later."
    const val ERROR_SUPPORT_SAVE = "❌ Error saving your support request. Please try again later."
    const val ERROR_SUPPORT_INVALID_MESSAGE = "❌ Please send a text message only (no media files)."

    // === Success Messages ===
    const val SUCCESS_PARSED = "<b>Job search can be started!</b>"
    const val SUCCESS_ALERT_PARSED = "<b>Job alert can be created!</b>"
    const val SUCCESS_UPDATED_PARSED = "<b>Job search can be updated!</b>"

//    const val SERVICE_SHORT_DESCRIPTION = "Your personalised Job Search AI Agent"

    const val BOT_SHORT_DESCRIPTION =
        "I'm your personal Job Search Agent. I find fresh jobs before anyone else"
    val BOT_DESCRIPTION = buildString {
        appendLine("Does this sound familiar?")
        appendLine()
        appendLine("Applying after 100+ other candidates")
        appendLine("Wasting time scrolling through irrelevant jobs")
        appendLine("Opening jobs just to see it is not a match")
        appendLine()
        appendLine("I’ve been there. It’s exhausting, demotivating, and unfair.")
        appendLine("That’s why I built this agent - to get an edge.")
        appendLine("Start now to get your advantage")
    }


    const val HEADER_HELP = "📖 <b>Job Alerts Bot - Help</b>"
    const val HEADER_PRIVACY_POLICY = "🔒 <b>Privacy Policy</b>"
    const val HEADER_CREATE_ALERT = "🔔 <b>Creating a new job alert</b>"
    const val HEADER_IMMEDIATE_SEARCH = "🔍 <b>Let's start an immediate job search</b>"
    const val HEADER_DELETE_ALERT = "🗑️ <b>Delete Job Alert</b>"
    const val HEADER_EDIT_ALERT = "✏️ <b>Edit Job Alert</b>"
    const val HEADER_YOUR_ALERTS = "📋 <b>Your Active Job Alerts</b>"
    const val HEADER_DELETE_CONFIRMATION = "🗑️ <b>Delete Alert Confirmation</b>"
    const val HEADER_INVALID_ALERT_ID = "❌ <b>Invalid Alert ID</b>"
    const val HEADER_INVALID_ALERT_IDS = "❌ <b>Invalid Alert ID(s)</b>"
    const val HEADER_JOB_SEARCH_DETAILS = "🔍 <b>Job Search Details:</b>"
    const val HEADER_EDITING_ALERT = "✏️ <b>Editing Alert:</b>"
    const val HEADER_CURRENT_ALERT_DETAILS = "<b>Current Alert Details:</b>"
    const val HEADER_AVAILABLE_ACTIONS = "<b>Possible Actions:</b>"
    const val HEADER_REQUIRED_FIELDS = "<b>Required Fields:</b>"
    const val HEADER_OPTIONAL_FIELDS = "<b>Optional Fields:</b>"
    const val HEADER_EXAMPLES = "<b>Examples:</b>"
    const val HEADER_EXAMPLE_DESCRIPTIONS = "<b>Example Descriptions:</b>"
    const val HEADER_SUPPORT = "\uD83D\uDC68\u200D⚕\uFE0F <b>Contact Support</b>"

    // === Structured Approach Template ===
    const val STRUCTURED_APPROACH_HEADER = "Let's try again. Please provide:"

    // === Common Instructions ===
    const val INSTRUCTION_IS_CORRECT = "<b>Is this correct?</b>"
    const val INSTRUCTION_RETRY_DESCRIPTION = "Please try again with a clearer description:"
    const val INSTRUCTION_PROVIDE_VALID_ID =
        "Please provide a valid alert ID or use $CMD_LIST_ALERTS to see your alerts."
    const val INSTRUCTION_USE_LIST_ALERTS =
        "Use $CMD_LIST_ALERTS to see your alerts or $CMD_CANCEL to abort."

    // === Notes ===
    const val NOTE_RECURRING_ALERT =
        "💡 This will create a recurring alert that searches for jobs automatically with specified frequency!"
    const val NOTE_DESCRIBE_IN_FULL =
        "💡 Please, describe your job search in full in your next message!"
    val NOTE_ONE_TIME_SEARCH = buildString {
        "💡 This is a one-time search that will start executing immediately and return results in few minutes."
        "💡 This search will always return jobs published in less than ${TimePeriod.getOneTimeSearchPeriod().displayName}."
    }

    const val NOTE_RESULTS_NOTIFICATION =
        "🔔 You'll receive notifications when new jobs matching your criteria are found."
    const val NOTE_CANNOT_UNDO = "⚠️ <b>Warning:</b> This action cannot be undone!"
    const val NOTE_SEARCH_RUNNING =
        "⏳ Your job search is now running. Results will be sent to you once the search is complete in few minutes."
    const val NOTE_UPDATED_ALERT_ACTIVE =
        "🔔 Your updated alert is now active and will search for jobs with the new criteria."

    const val SEARCH_NOW_DESC = "Find jobs posted in last week"
    const val CREATE_ALERT_DESC = "Create Alert for new jobs"
    const val LIST_ALERTS_DESC = "View alerts"
    const val EDIT_ALERT_DESC = "Edit alert"
    const val DELETE_ALERT_DESC = "Delete alert"
    const val HELP_DESC = "Detailed help"
    const val PRIVACY_DESC = "Privacy policy and data usage"
    const val SUPPORT_DESC = "Contact support"

    // === Menu Items ===
    const val MENU_SEARCH_NOW = "$CMD_SEARCH_NOW - $SEARCH_NOW_DESC"
    const val MENU_CREATE_ALERT = "$CMD_CREATE_ALERT - $CREATE_ALERT_DESC"
    const val MENU_LIST_ALERTS = "$CMD_LIST_ALERTS - $LIST_ALERTS_DESC"
    const val MENU_EDIT_ALERT = "$CMD_EDIT_ALERT - $EDIT_ALERT_DESC"
    const val MENU_DELETE_ALERT = "$CMD_DELETE_ALERT - $DELETE_ALERT_DESC"
    const val MENU_HELP = "$CMD_HELP - $HELP_DESC"
    const val MENU_PRIVACY = "$CMD_PRIVACY - $PRIVACY_DESC"
    const val MENU_SUPPORT = "$CMD_SUPPORT - $SUPPORT_DESC"


    // === Loading Messages ===
    const val ANALYZING_DESCRIPTION = "Analyzing your job alert description..."
    const val ANALYZING_SEARCH = "Analyzing your job search description..."
    const val ANALYZING_UPDATE = "Analyzing your updated job search..."
    const val CREATING_ALERT = "<b>Creating your job alert...</b>"
    const val UPDATING_ALERT = "<b>Updating your job alert...</b>"
    const val STARTING_SEARCH = "<b>Starting your job search...</b>"

    // ================== DYNAMIC METHODS ==================

    fun getStartWithButtonsMessage(): String = buildString {
        appendLine("Recruiters are more likely to hire candidates who apply early.")
        appendLine("This agent monitors the job market and notifies you about new opportunities ASAP.")
        appendLine("Let's find your next job!")
        appendLine()
    }

    fun getHelpMessage(): String = buildString {
        appendLine(HEADER_HELP)
        appendLine()
        appendLine("<b>Job Alert Management:</b>")
        appendLine("$CMD_CREATE_ALERT - Create a new job search alert")
        appendLine("$CMD_LIST_ALERTS - View all your active job alerts")
        appendLine("$CMD_EDIT_ALERT - Modify an existing job alert")
        appendLine("$CMD_DELETE_ALERT - Remove a job alert")
        appendLine("<b>Other Commands:</b>")
        appendLine("$CMD_START - Welcome message")
        appendLine("$CMD_HELP - Show this help message")
        appendLine("$CMD_SUPPORT - Contact support")
        appendLine("$CMD_CANCEL - Cancel current operation")
        appendLine()
        appendLine("<b>Job Search Format:</b>")
        appendLine("When creating or editing alerts, you can describe your job requirements in natural language:")
        appendLine("\"Looking for Senior Python Developer job in Berlin, remote only, no requirement to speak German, description should not have keyword startup\"")
        appendLine()
        appendLine("<b>Need More Help?</b>")
        appendLine("If you encounter any issues or need assistance, feel free to contact support $CMD_SUPPORT")
    }

    // === Job Alert Creation Messages ===
    fun getCreateAlertInstructions(): String = buildString {
        appendLine(HEADER_CREATE_ALERT)
        appendLine()
        append(getJobSearchFormattingInstructions())
        appendLine()
        appendLine(NOTE_RECURRING_ALERT)
        appendLine(NOTE_DESCRIBE_IN_FULL)
    }


    // === Confirmation Messages ===
    fun getAlertCreationConfirmation(jobSearch: JobSearchIn): String = buildString {
        appendLine(SUCCESS_ALERT_PARSED)
        appendLine()
        append(jobSearch.toHumanReadableString())
        appendLine()
        appendLine(INSTRUCTION_IS_CORRECT)
        appendLine("Reply <b>yes</b> to create the alert")
        appendLine("Reply <b>no</b> to modify your alert")
        appendLine("Use $CMD_CANCEL to abort")
    }

    fun getSearchConfirmation(jobSearch: JobSearchIn): String = buildString {
        appendLine(SUCCESS_PARSED)
        appendLine()
        append(jobSearch.toHumanReadableString())
        appendLine()
        appendLine(INSTRUCTION_IS_CORRECT)
        appendLine("Reply <b>yes</b> to proceed with the search")
        appendLine("Reply <b>no</b> to modify your search")
        appendLine("Use $CMD_CANCEL to abort")
    }

    fun getEditConfirmation(alertId: String, jobSearch: JobSearchIn): String = buildString {
        appendLine(SUCCESS_UPDATED_PARSED)
        appendLine()
        appendLine("<b>Alert ID:</b> $alertId")
        appendLine()
        append(jobSearch.toHumanReadableString())
        appendLine()
        appendLine(INSTRUCTION_IS_CORRECT)
        appendLine("Reply <b>yes</b> to save the changes")
        appendLine("Reply <b>no</b> to modify your criteria")
        appendLine("Use $CMD_CANCEL to abort")
    }

    // === Success Messages ===
    fun getAlertCreatedSuccess(alertId: String, jobSearch: JobSearchIn): String = buildString {
        appendLine("✅ <b>Job alert created successfully!</b>")
        appendLine()
        appendLine("\uD83C\uDD94 <b>Alert ID:</b> $alertId")
        appendLine("<b>Searching for:</b> ${jobSearch.jobTitle}")
        appendLine("<b>Location:</b> ${jobSearch.location}")
        appendLine("<b>Frequency:</b> ${jobSearch.timePeriod.displayName}")
        appendLine("<b>Filter:</b> ${jobSearch.filterText}")
        appendLine()
        appendLine(NOTE_RESULTS_NOTIFICATION)
        appendLine()
        appendLine("Use $CMD_LIST_ALERTS to see all your alerts or $CMD_START for other options.")
    }

    fun getSearchInitiatedSuccess(searchId: String, jobSearch: JobSearchIn): String = buildString {
        appendLine("✅ <b>Job search initiated successfully!</b>")
        appendLine()
        appendLine("📋 <b>Search ID:</b> $searchId")
        appendLine("🔍 <b>Searching for:</b> ${jobSearch.jobTitle}")
        appendLine("📍 <b>Location:</b> ${jobSearch.location}")
        appendLine()
        appendLine(NOTE_SEARCH_RUNNING)
        appendLine()
        appendLine("Use $CMD_START to access other options.")
    }

    fun getAlertUpdatedSuccess(alertId: String, jobSearch: JobSearchIn): String = buildString {
        appendLine("✅ <b>Job alert updated successfully!</b>")
        appendLine()
        appendLine("📋 <b>Alert ID:</b> $alertId")
        appendLine("🔍 <b>Searching for:</b> ${jobSearch.jobTitle}")
        appendLine("📍 <b>Location:</b> ${jobSearch.location}")
        appendLine("⏰ <b>Frequency:</b> ${jobSearch.timePeriod.displayName}")
        appendLine()
        appendLine(NOTE_UPDATED_ALERT_ACTIVE)
        appendLine()
        appendLine("Use $CMD_LIST_ALERTS to see all your alerts or $CMD_START for other options.")
    }

    // === Delete Alert Messages ===
    fun getNoAlertsToDeleteMessage(): String = buildString {
        appendLine(HEADER_DELETE_ALERT)
        appendLine()
        appendLine("You don't have any active job alerts to delete.")
        appendLine()
        appendLine("<b>Get started:</b>")
        appendLine("$CMD_CREATE_ALERT - Create your first job alert")
        appendLine("$CMD_HELP - See all available commands")
    }

    fun getSelectAlertToDeleteMessage(userSearches: List<JobSearchOut>): String = buildString {
        appendLine(HEADER_DELETE_ALERT)
        appendLine()
        appendLine("Which alert(s) would you like to delete? Please provide the alert ID(s).")
        appendLine()
        appendLine("<b>Your Active Job Alerts:</b>")
        appendLine()

        userSearches.forEach { jobSearch ->
            append(jobSearch.toMessage())
            appendLine()
            appendLine()
        }

        appendLine(HEADER_EXAMPLES)
        appendLine("<b>123</b> - Delete alert with ID 123")
        appendLine("<b>123,456</b> - Delete alerts with IDs 123 and 456")
        appendLine()
        appendLine(USE_CANCEL_TO_ABORT)
    }

    fun getDeleteConfirmationMessage(validAlertIds: List<String>): String = buildString {
        appendLine(HEADER_DELETE_CONFIRMATION)
        appendLine()
        if (validAlertIds.size == 1) {
            appendLine("Are you sure you want to delete alert: <b>${validAlertIds[0]}</b>?")
        } else {
            appendLine("Are you sure you want to delete these ${validAlertIds.size} alerts?")
            validAlertIds.forEach { appendLine("<b>$it</b>") }
        }
        appendLine()
        appendLine(NOTE_CANNOT_UNDO)
        appendLine()
        appendLine("Reply <b>yes</b> to confirm deletion")
        appendLine("Reply <b>no</b> to cancel")
        appendLine("Use $CMD_CANCEL to abort this operation")
    }

    fun getInvalidAlertIdsMessage(
        invalidAlertIds: List<String>,
        validAlertIds: List<String>
    ): String = buildString {
        appendLine(HEADER_INVALID_ALERT_IDS)
        appendLine()
        appendLine("The following alert ID(s) don't exist or don't belong to you:")
        invalidAlertIds.forEach { appendLine(it) }
        appendLine()
        if (validAlertIds.isNotEmpty()) {
            appendLine("Valid alert ID(s): ${validAlertIds.joinToString(", ")}")
            appendLine()
            appendLine("Please provide only valid alert IDs or use $CMD_CANCEL to abort.")
        } else {
            appendLine("Please provide valid alert ID(s) or use $CMD_LIST_ALERTS to see your alerts.")
        }
    }

    fun getDeletionResultMessage(deletedIds: List<String>, failedIds: List<String>): String =
        buildString {
            if (deletedIds.isNotEmpty()) {
                if (deletedIds.size == 1) {
                    appendLine("✅ <b>Alert ${deletedIds[0]} has been deleted successfully.</b>")
                } else {
                    appendLine("✅ <b>${deletedIds.size} alerts have been deleted successfully:</b>")
                    deletedIds.forEach { appendLine(it) }
                }
            }

            if (failedIds.isNotEmpty()) {
                appendLine()
                appendLine("❌ <b>Failed to delete the following alert(s):</b>")
                failedIds.forEach { appendLine(it) }
                appendLine("Please try again later or contact support.")
            }
        }

    // === Edit Alert Messages ===
    fun getNoAlertsToEditMessage(): String = buildString {
        appendLine(HEADER_EDIT_ALERT)
        appendLine()
        appendLine("You don't have any active job alerts to edit.")
        appendLine()
        appendLine("<b>Get started:</b>")
        appendLine("$CMD_CREATE_ALERT - Create your first job alert")
        appendLine("$CMD_HELP - See all available commands")
    }

    fun getSelectAlertToEditMessage(userSearches: List<JobSearchOut>): String = buildString {
        appendLine(HEADER_EDIT_ALERT)
        appendLine()
        appendLine("Which alert would you like to edit? Please provide the alert ID.")
        appendLine()
        appendLine("<b>Your Active Job Alerts:</b>")
        appendLine()

        userSearches.forEach { jobSearch ->
            append(jobSearch.toMessage())
            appendLine()
            appendLine()
        }

        appendLine("<b>Example:</b> <b>123</b> (just the ID number)")
        appendLine()
        appendLine(USE_CANCEL_TO_ABORT)
    }

    fun getInvalidAlertIdMessage(alertId: String): String = buildString {
        appendLine(HEADER_INVALID_ALERT_ID)
        appendLine()
        appendLine("Alert ID $alertId doesn't exist or doesn't belong to you.")
        appendLine()
        appendLine(INSTRUCTION_PROVIDE_VALID_ID)
    }

//    fun getEditAlertDetailsMessage(alertId: String, existingAlert: JobSearchOut): String =
//        buildString {
//            appendLine("$HEADER_EDITING_ALERT $alertId</b>")
//            appendLine()
//            appendLine(HEADER_CURRENT_ALERT_DETAILS)
//            appendLine()
//            append(existingAlert.toMessage())
//            appendLine()
//            appendLine()
//            appendLine("Please provide the new job search criteria:")
//            appendLine()
//            append(JobSearchIn.getFormattingInstructions())
//            appendLine()
//            appendLine(USE_CANCEL_TO_ABORT)
//        }

    // === List Alerts Messages ===
    fun getNoActiveAlertsMessage(): String = buildString {
        appendLine(HEADER_YOUR_ALERTS)
        appendLine()
        appendLine("You don't have any active job alerts yet.")
        appendLine()
        appendLine("<b>Get started:</b>")
        appendLine("$CMD_CREATE_ALERT - Create your first job alert")
        appendLine("$CMD_HELP - See all available commands")
        appendLine()
        appendLine("Ready to find your next opportunity? 🚀")
    }

    fun getActiveAlertsMessage(userSearches: List<JobSearchOut>): String = buildString {
        appendLine("$HEADER_YOUR_ALERTS (${userSearches.size} total)")
        appendLine()

        userSearches.forEach { jobSearch ->
            append(jobSearch.toMessage())
            appendLine()
            appendLine()
        }

        appendLine(HEADER_AVAILABLE_ACTIONS)
        appendLine(MENU_EDIT_ALERT)
        appendLine(MENU_DELETE_ALERT)
    }

    // === Retry and Error Messages ===
    fun getRetryJobSearchMessage(): String = buildString {
        appendLine("📝 <b>Let's modify your job search.</b>")
        appendLine()
        append(getJobSearchFormattingInstructions())
    }

    fun getRetryJobAlertMessage(): String = buildString {
        appendLine("📝 <b>Let's modify your job alert.</b>")
        appendLine()
        append(getJobSearchFormattingInstructions())
    }

    fun getStructuredApproachMessage(): String = buildString {
        appendLine("❌ <b>I'm having trouble understanding your job search description.</b>")
        appendLine()
        appendLine(STRUCTURED_APPROACH_HEADER)
        appendLine()
        append(getJobSearchFields())
        appendLine()
        appendLine("Use $CMD_CANCEL if you want to stop.")
    }

    // === Common Confirmation Responses ===
    fun getConfirmationInstruction(actionType: String): String = when (actionType) {
        "create" -> "Please respond with <b>yes</b> to create the alert, <b>no</b> to edit description again, or $CMD_CANCEL to abort."
        "delete" -> "Please respond with <b>yes</b> to delete the alert(s), <b>no</b> to cancel, or $CMD_CANCEL to abort."
        "edit" -> "Please respond with <b>yes</b> to save the changes, <b>no</b> to modify changes again, or $CMD_CANCEL to abort."
        "search" -> "Please respond with <b>yes</b> to proceed, <b>no</b> to modify description again, or $CMD_CANCEL to abort."
        else -> "Please respond with <b>yes</b> to proceed, <b>no</b> to cancel, or $CMD_CANCEL to abort."
    }

    // === Job Search Display Messages ===
    fun getJobSearchDetails(jobSearch: JobSearchIn): String = buildString {
        appendLine("🔍 <b>Job Search Details:</b>")
        appendLine("📍 <b>Job Title:</b> ${jobSearch.jobTitle}")
        appendLine("🌍 <b>Location:</b> ${jobSearch.location}")
        appendLine("💼 <b>Job Types:</b> ${jobSearch.jobTypes.joinToString(", ") { it.label }}")
        appendLine("🏠 <b>Remote Types:</b> ${jobSearch.remoteTypes.joinToString(", ") { it.label }}")
        appendLine("⏰ <b>Time Period:</b> ${jobSearch.timePeriod.displayName}")
        if (!jobSearch.filterText.isNullOrBlank()) {
            appendLine("🔍 <b>Filter Text:</b> ${jobSearch.filterText}")
        }
    }

    fun getJobSearchFields(): StringBuilder {
        return StringBuilder()
            .appendLine("<b>1. Job Title without details.</b> Example: Senior Python Software Engineer")
            .appendLine()
            .appendLine("<b>2. Location</b> Example: New York, United States, Germany")
            .appendLine()
            .appendLine(
                "<b>3. Job Types.</b> Available options: ${
                    JobType.getAllLabels().joinToString(", ")
                }"
            )
            .appendLine()
            .appendLine(
                "<b>4. Remote Types.</b> Available options: ${
                    RemoteType.getAllLabels().joinToString(", ")
                }"
            )
            .appendLine()
            .appendLine("<b>5. Filter Prompt.</b> Very specific requirements and exclusions in natural language")
            .appendLine("Examples for filter prompt: providing visa sponsorship, no relocation required, without requirement to speak German, salary starting at 100000$, employer should not be a startup, should not require Angular knowledge")
            .appendLine()
    }


    fun getJobSearchFormattingInstructions(): String = buildString {
        appendLine("<b>Please provide your job search criteria in natural language.</b>")
        appendLine()
        append(getJobSearchFields())
        appendLine(
            "<b>6. Search Frequency.</b> Available options: ${
                TimePeriod.getRecommendedLabels().joinToString(", ")
            }"
        )
        appendLine()
        appendLine("<b>Example of a complete search:</b>")
        appendLine("Senior Python Engineer in San Francisco, full-time, remote, check new jobs every 20 minutes, no startups, no requirement to speak any language other than English, no requirement to know Angular")
    }

    fun getStaticJobDescription(job: ProcessedJobData): String {
        return buildString {
            appendLine("📋 ${job.title}")
            appendLine("🏢 ${job.company}")
            appendLine("📍 ${job.location}")
            if (job.salary != null && job.salary.isNotBlank()) {
                appendLine("\uD83D\uDCB5 ${job.salary}")
            }
            appendLine(
                "\uD83C\uDFF7\uFE0F ${
                    job.techstack.joinToString(", ") {
                        "#${
                            it.replace(
                                ".",
                                ""
                            ).replace("/", "").replace(" ", "").lowercase()
                        }"
                    }
                }"
            )
            appendLine("🔗 ${job.link}")
        }
    }

    fun getStartWithJobMessage(job: ProcessedJobData): String {
        return buildString {
            appendLine("👋 Hi there! Here is the job you are interested in. You can apply using the provided link.")
            appendLine()
            appendLine(getStaticJobDescription(job))
            appendLine()
            appendLine("Do you want me to collect more jobs like this? Create your personalized alert and get notifications with similar fresh jobs in this private chat.")
        }
    }

    fun getJobNotificationMessage(
        searchName: String,
        newJobs: List<ScoredJobData>,
        suffix: StringBuilder?,
    ): String =
        buildString {
            appendLine(
                "🎉 Found ${newJobs.size} new jobs for $searchName!"
            )
            appendLine()
            newJobs.forEach { job ->
                appendLine("Compatibility: ${job.compatibilityScore ?: "N/A"}")
                appendLine("📋 ${job.title}")
                appendLine("🏢 ${job.company}")
                appendLine("📍 ${job.location}")
                if (job.salary != null && job.salary.isNotBlank()) {
                    appendLine("\uD83D\uDCB5 ${job.salary}")
                }
                if (job.applicants.isNotBlank()) {
                    appendLine("\uD83D\uDE4B\u200D♂\uFE0F ${job.applicants}")
                }
                appendLine("⌛ ${job.createdAgo}")
                appendLine(
                    "\uD83C\uDFF7\uFE0F ${
                        job.techstack.joinToString(", ") {
                            "#${
                                it.replace(
                                    ".",
                                    ""
                                ).replace("/", "").replace(" ", "").lowercase()
                            }"
                        }
                    }"
                )
                appendLine("🔗 ${job.link}")
                appendLine()
            }
            if (suffix != null) {
                append(suffix)
            }
        }

// === Edit Alert Messages (from EditSearchService) ===

    fun getInvalidAlertIdForEditMessage(alertId: String): String = buildString {
        appendLine("❌ <b>Invalid Alert ID</b>")
        appendLine()
        appendLine("Alert ID $alertId doesn't exist or doesn't belong to you.")
        appendLine()
        appendLine("Please provide a valid alert ID or use $CMD_LIST_ALERTS to see your alerts.")
    }

    fun getEditAlertDetailsWithCurrentMessage(
        alertId: String,
        existingAlert: JobSearchOut
    ): String = buildString {
        appendLine("✏️ <b>Editing Alert: $alertId</b>")
        appendLine()
        appendLine("<b>Current Alert Details:</b>")
        appendLine()
        append(existingAlert.toMessage())

        appendLine()
        append(getJobSearchFormattingInstructions())
        appendLine()
        appendLine("⚠\uFE0F <b>Please provide the full edited job search description including all parameters: the ones that change and the ones that stay the same</b>")
        appendLine()
        appendLine("Use $CMD_CANCEL to abort this operation.")
    }

    fun getEditConfirmationMessage(alertId: String, jobSearch: JobSearchIn): String =
        buildString {
            appendLine("<b>This job search can be updated!</b>")
            appendLine()
            appendLine("<b>Alert ID:</b> $alertId")
            appendLine()
            append(getJobSearchDetails(jobSearch))
            appendLine()
            appendLine("⏰ <b>Alert Frequency:</b> ${jobSearch.timePeriod.displayName}")
            appendLine()
            appendLine("<b>Is this correct?</b>")
            appendLine("Reply <b>yes</b> to save the changes")
            appendLine("Reply <b>no</b> to modify your criteria")
            appendLine("Use $CMD_CANCEL to abort")
        }

    fun getEditSuccessMessage(alertId: String, updatedJobSearch: JobSearchOut): String =
        buildString {
            appendLine("✅ <b>Job alert updated successfully!</b>")
            appendLine()
            appendLine("📋 <b>Alert ID:</b> $alertId")
            appendLine("🔍 <b>Searching for:</b> ${updatedJobSearch.jobTitle}")
            appendLine("📍 <b>Location:</b> ${updatedJobSearch.location}")
            appendLine("⏰ <b>Frequency:</b> ${updatedJobSearch.timePeriod.displayName}")
            appendLine()
            appendLine("🔔 Your updated alert is now active and will search for jobs with the new criteria.")
            appendLine()
            appendLine("Use $CMD_LIST_ALERTS to see all your alerts or $CMD_START for other options.")
        }

    fun getEditRetryMessage(): String = buildString {
        appendLine("📝 <b>Let's modify your job search criteria.</b>")
        appendLine()
        append(getJobSearchFormattingInstructions())
    }

    fun getEditMaxAttemptsMessage(): String = buildString {
        appendLine("❌ <b>Unable to parse your job search criteria after multiple attempts.</b>")
        appendLine()
        appendLine("Please ensure you follow the format guidelines:")
        appendLine()
        append(getJobSearchFormattingInstructions())
        appendLine()
        appendLine("Use $CMD_CANCEL to abort this operation or try again later.")
    }
// === Support Messages ===

    fun getSupportInitialMessage(): String = buildString {
        appendLine(HEADER_SUPPORT)
        appendLine()
        appendLine("We're here to help! Please describe your issue or question in detail.")
        appendLine()
        appendLine("📝 <b>Guidelines:</b>")
        appendLine("Send one clear text message describing your issue")
        appendLine("Include the issue description and all relevant details (actions before issue appeared, error messages, expected behavior, etc.)")
        appendLine("Do not send media files (audios, images, videos, documents), we can't process it yet")
        appendLine()
        appendLine("💡 <b>Tip:</b> The more details you provide, the better we can assist you!")
        appendLine()
        appendLine("Use $CMD_CANCEL to abort this support request.")
    }

    fun getSupportConfirmationMessage(): String = buildString {
        appendLine("✅ <b>Support Request Received!</b>")
        appendLine()
        appendLine("Thank you for contacting our support team. We have received your message and will review it as soon as possible.")
        appendLine()
        appendLine("<b>What happens next:</b>")
        appendLine("Our team will review your request")
        appendLine("If we need additional information, we'll contact you via this same bot")
        appendLine()
        appendLine("Thank you for using our service! Use $CMD_START to return to the main menu.")
    }
}