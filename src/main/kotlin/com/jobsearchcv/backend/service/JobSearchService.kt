package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.JobSearchRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JobSearchService(
    private val jobSearchRepository: JobSearchRepository,
    private val jobSearchScheduler: JobSearchScheduler,
    private val scraperJobService: ScraperJobService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchService::class.java)
    }
    

    
    fun getUserSearches(userId: String): List<JobSearchOut> {
        return jobSearchRepository.findByUserId(userId)
    }
    
    fun getSearchById(searchId: String): JobSearchOut? {
        return jobSearchRepository.findById(searchId)
    }
    
    suspend fun deleteJobSearch(userId: String, searchId: String): Boolean {
        val search = jobSearchRepository.findByIdAndUserId(searchId, userId)
        if (search != null) {
            jobSearchRepository.deleteById(searchId)
            jobSearchScheduler.removeJobSearch(searchId)
            logger.info("Deleted job search: {} for user: {}", searchId, userId)
            return true
        }
        return false
    }
    
    suspend fun initialize() {
        // Load all existing job searches and add them to the scheduler
        val allSearches = jobSearchRepository.findAll()
        logger.info("Loading {} existing job searches", allSearches.size)
        
        // Use new bulk method
        jobSearchScheduler.addInitialJobSearches(allSearches)
    }
} 