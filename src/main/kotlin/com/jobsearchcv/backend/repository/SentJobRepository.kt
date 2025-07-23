package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.SentJobOut
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
 
@Repository
interface SentJobRepository : MongoRepository<SentJobOut, String>, SentJobRepositoryCustom {
    fun findByUserId(userId: String): List<SentJobOut>
    fun existsByUserIdAndJobUrl(userId: String, jobUrl: String): Boolean
} 