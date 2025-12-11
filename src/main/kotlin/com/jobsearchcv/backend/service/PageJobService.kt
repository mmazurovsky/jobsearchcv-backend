package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.config.CacheConfig
import com.jobsearchcv.backend.domain.model.PageJobResponse
import com.jobsearchcv.backend.repository.ProcessedJobRepository
import com.jobsearchcv.backend.repository.SentJobRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class PageJobService(
    private val sentJobRepository: SentJobRepository,
    private val processedJobRepository: ProcessedJobRepository
) {
    private val logger = LoggerFactory.getLogger(PageJobService::class.java)

    /**
     * Get page jobs for a user from the last 1440 minutes (24 hours)
     * Cached for 10 minutes to reduce database load for repeated requests.
     *
     * @param userId User ID to fetch jobs for
     * @param minutesBack Number of minutes to look back (default: 1440 = 24 hours)
     * @param tags Optional list of tags to filter by (e.g., ["entry-level", "remote"])
     * @param techstack Optional list of techstack items to filter by (e.g., ["Kotlin", "Spring Boot"])
     * @return List of jobs sorted by sent_at descending
     */
    @Cacheable(
        value = [CacheConfig.PAGE_JOBS_CACHE],
        key = "#userId + '_' + #minutesBack + '_' + (#tags != null ? #tags.toString() : 'all') + '_' + (#techstack != null ? #techstack.toString() : 'all')"
    )
    fun getPageJobsForUser(userId: String, minutesBack: Long = 1440, tags: List<String>? = null, techstack: List<String>? = null): List<PageJobResponse> {
        logger.info("Fetching page jobs for userId=$userId from last $minutesBack minutes, tags=$tags, techstack=$techstack")

        // 1. Get sent jobs from last N minutes
        val sentAtAfter = OffsetDateTime.now().minusMinutes(minutesBack)
        val sentJobs = sentJobRepository.findByUserIdAndSentAtAfter(userId, sentAtAfter)

        if (sentJobs.isEmpty()) {
            logger.info("No sent jobs found for userId=$userId in last $minutesBack minutes")
            return emptyList()
        }

        logger.info("Found ${sentJobs.size} sent jobs for userId=$userId")

        // 2. Extract internal IDs (filter out nulls)
        val internalIds = sentJobs.mapNotNull { it.internalId }.toSet()

        if (internalIds.isEmpty()) {
            logger.warn("No internal IDs found in sent jobs for userId=$userId")
            return emptyList()
        }

        // 3. Fetch processed jobs by internal IDs, with optional tags/techstack filtering at database level
        val processedJobs = if (!tags.isNullOrEmpty() || !techstack.isNullOrEmpty()) {
            processedJobRepository.findByInternalIdsWithFilters(internalIds, tags, techstack).also { filtered ->
                logger.info("Found ${filtered.size} processed jobs matching tags=$tags, techstack=$techstack for ${internalIds.size} internal IDs")
            }
        } else {
            processedJobRepository.findByInternalIds(internalIds).also { all ->
                logger.info("Found ${all.size} processed jobs for ${internalIds.size} internal IDs")
            }
        }

        // 4. Create a map for quick lookup: internalId -> ProcessedJobData
        val processedJobMap = processedJobs.associateBy { it.internalId }

        // 5. Build response preserving sentAt order (already sorted by sent_at desc)
        val responses = sentJobs.mapNotNull { sentJob ->
            val internalId = sentJob.internalId ?: return@mapNotNull null
            val processedJob = processedJobMap[internalId] ?: return@mapNotNull null

            PageJobResponse(
                internalId = processedJob.internalId,
                title = processedJob.title,
                company = processedJob.company,
                location = processedJob.location,
                techstack = processedJob.techstack,
                tags = processedJob.tags,
                salary = processedJob.salary,
                processedAt = processedJob.processedAt,
            )
        }

        logger.info("Returning ${responses.size} page jobs for userId=$userId")
        return responses
    }
}
