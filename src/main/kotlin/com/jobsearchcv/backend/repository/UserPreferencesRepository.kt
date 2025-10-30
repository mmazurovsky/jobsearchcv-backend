package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.UserPreferences
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserPreferencesRepository : CoroutineCrudRepository<UserPreferences, String> {
    /**
     * Find user preferences by user ID
     * @param userId The user's ID
     * @return The user's preferences or null if not found
     */
    suspend fun findByUserId(userId: String): UserPreferences?
}
