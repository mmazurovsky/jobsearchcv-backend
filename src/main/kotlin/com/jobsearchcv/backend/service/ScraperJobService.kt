package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.SearchJobsParams
import com.jobsearchcv.backend.service.client.ScraperClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ScraperJobService(
    private val scraperClient: ScraperClient,
    @Value("\${CALLBACK_URL}") private val callbackUrl: String,
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ScraperJobService::class.java)
    }

    private val semaphore = Semaphore(4) // Limit to 4 concurrent jobs like main_project

    suspend fun triggerScraperJobAndLog(jobSearch: JobSearchOut) {
        semaphore.withPermit {
            // Build callback URL
            val callbackUrl = callbackUrl.trimEnd('/') + "/api/job-data-callback"

            val params =
                    SearchJobsParams(
                            keywords = jobSearch.jobTitle,
                            location = jobSearch.location,
                            jobTypes = jobSearch.jobTypes.map { it.label },
                            remoteTypes = jobSearch.remoteTypes.map { it.label },
                            timePeriod = jobSearch.timePeriod.displayName,
                            filterText = jobSearch.filterText,
                            callbackUrl = callbackUrl,
                            jobSearchId = jobSearch.id,
                            userId = jobSearch.userId
                    )

            try {
                val response = scraperClient.scrapeJobs(params)
                val logDataWithStatus =
                        jobSearch.toLogString() +
                                ("callback_url" to callbackUrl) +
                                ("status_code" to response.statusCode)

                if (!response.isSuccessful) {
                    val logDataWithResponse = logDataWithStatus + ("response_text" to response.body)
                    logger.error("Failed to trigger scraper job: {}", logDataWithResponse)
                }
            } catch (e: Exception) {
                logger.error("Exception triggering scraper job: {}", jobSearch.toLogString(), e)
            }
        }
    }
}
