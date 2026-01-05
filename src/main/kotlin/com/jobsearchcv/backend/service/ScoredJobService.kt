package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.ScoredJobOut
import com.jobsearchcv.backend.domain.model.ScoredJobResponse
import com.jobsearchcv.backend.repository.ScoredJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ScoredJobService(
    private val scoredJobRepository: ScoredJobRepository
) {
    private val logger = LoggerFactory.getLogger(ScoredJobService::class.java)

    fun getRecentScoredJobs(
        userId: String,
        minutesBack: Long,
        status: String?,
        jobSearchId: String?
    ): List<ScoredJobResponse> {
        logger.info("Fetching scored jobs: userId=$userId, minutesBack=$minutesBack, status=$status, jobSearchId=$jobSearchId")

        val sentAtAfter = OffsetDateTime.now().minusMinutes(minutesBack)

        val scoredJobs = when {
            // Filter by jobSearchId and status
            !jobSearchId.isNullOrBlank() && !status.isNullOrBlank() -> {
                scoredJobRepository.findByUserIdAndJobSearchIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                    userId, jobSearchId, status, sentAtAfter
                )
            }
            // Filter by jobSearchId only
            !jobSearchId.isNullOrBlank() -> {
                scoredJobRepository.findByUserIdAndJobSearchIdAndSentAtAfterOrderBySentAtDesc(
                    userId, jobSearchId, sentAtAfter
                )
            }
            // Filter by status only
            !status.isNullOrBlank() -> {
                scoredJobRepository.findByUserIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                    userId, status, sentAtAfter
                )
            }
            // No filters
            else -> {
                scoredJobRepository.findByUserIdAndSentAtAfterOrderBySentAtDesc(userId, sentAtAfter)
            }
        }

        logger.info("Found ${scoredJobs.size} scored jobs for userId=$userId")

        return scoredJobs.map { it.toScoredJobResponse() }
    }

    private fun ScoredJobOut.toScoredJobResponse(): ScoredJobResponse {
        return ScoredJobResponse(
            id = this.id,
            userId = this.userId,
            jobSearchId = this.jobSearchId,
            internalId = this.internalId,
            title = this.title,
            company = this.company,
            location = this.location,
            applicants = this.applicants,
            techstack = this.techstack,
            tags = this.tags,
            salary = this.salary,
            compatibilityScore = this.compatibilityScore,
            status = this.status,
            destination = this.destination,
            savedAt = this.savedAt
        )
    }
}
