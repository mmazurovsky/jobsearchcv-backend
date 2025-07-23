package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.SentJobOut
import java.time.OffsetDateTime

interface SentJobRepositoryCustom {
    fun findByDestination(destination: String): List<SentJobOut>
    fun countByDestinationAndSentAtAfter(destination: String, sentAtAfter: OffsetDateTime): Long
}