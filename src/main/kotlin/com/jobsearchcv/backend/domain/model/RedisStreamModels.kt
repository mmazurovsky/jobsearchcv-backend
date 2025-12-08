package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Message published to job-search:requests stream.
 * Maps to SearchJobsParams but with additional metadata for prioritization and retry tracking.
 */
data class JobSearchRequestMessage(
    @JsonProperty("job_search_id")
    val jobSearchId: String,

    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("keywords")
    val keywords: String,

    @JsonProperty("location")
    val location: String,

    @JsonProperty("time_period")
    val timePeriod: String,

    @JsonProperty("job_types")
    val jobTypes: List<String>,

    @JsonProperty("remote_types")
    val remoteTypes: List<String>,

    @JsonProperty("filter_text")
    val filterText: String? = null,

    @JsonProperty("callback_url")
    val callbackUrl: String,

    @JsonProperty("search_name")
    val searchName: String? = null,

    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @JsonProperty("priority")
    val priority: Int = 5, // 1-10, higher = more urgent (admin jobs = 10, regular = 5)

    @JsonProperty("retry_count")
    val retryCount: Int = 0
) {
    companion object {
        /**
         * Creates request message from SearchJobsParams
         *
         * @param params The search parameters
         * @param priority Priority level (1-10, default 5)
         * @return JobSearchRequestMessage ready to publish
         */
        fun fromSearchJobsParams(params: SearchJobsParams, priority: Int = 5): JobSearchRequestMessage {
            return JobSearchRequestMessage(
                jobSearchId = params.jobSearchId ?: "",
                userId = params.userId ?: "",
                keywords = params.keywords,
                location = params.location,
                timePeriod = params.timePeriod,
                jobTypes = params.jobTypes,
                remoteTypes = params.remoteTypes,
                filterText = params.filterText,
                callbackUrl = params.callbackUrl,
                searchName = params.searchName,
                priority = priority
            )
        }
    }
}

/**
 * Message received from job-search:results stream.
 * Sent by Python scraper after job scraping completes.
 */
data class JobSearchResultMessage(
    @JsonProperty("job_search_id")
    val jobSearchId: String,

    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("jobs")
    val jobs: List<ScrapedJobData>,

    @JsonProperty("search_name")
    val searchName: String? = null,

    @JsonProperty("status")
    val status: String, // "success", "partial_success", "error"

    @JsonProperty("total_processed")
    val totalProcessed: Int,

    @JsonProperty("success_count")
    val successCount: Int,

    @JsonProperty("failed_count")
    val failedCount: Int,

    @JsonProperty("error_message")
    val errorMessage: String? = null,

    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Redis Stream metadata for retry/DLQ handling
 */
data class StreamMessageMetadata(
    val messageId: String,
    val stream: String,
    val consumerGroup: String,
    val consumer: String,
    val deliveryCount: Int,
    val firstDeliveryTime: Long,
    val lastDeliveryTime: Long
)
