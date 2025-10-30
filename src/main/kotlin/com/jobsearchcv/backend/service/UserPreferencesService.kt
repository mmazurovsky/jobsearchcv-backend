package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.UserPreferences
import com.jobsearchcv.backend.repository.UserPreferencesRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class UserPreferencesService(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserPreferencesService::class.java)
    }

    /**
     * Get user preferences, creating default if not exists
     * @param userId The user's ID
     * @return User preferences (existing or newly created default)
     */
    suspend fun getUserPreferences(userId: String): UserPreferences {
        logger.info("Getting preferences for user: $userId")

        val existing = userPreferencesRepository.findByUserId(userId)
        if (existing != null) {
            logger.info("Found existing preferences for user: $userId")
            return existing
        }

        // Create default preferences
        logger.info("No preferences found for user: $userId, creating default")
        val defaultPreferences = UserPreferences.createDefault(userId)
        return userPreferencesRepository.save(defaultPreferences).also {
            logger.info("Created default preferences for user: $userId")
        }
    }

    /**
     * Update marketing newsletter subscription
     * @param userId The user's ID
     * @param isSubscribed New subscription status
     * @return Updated user preferences
     */
    suspend fun updateMarketingSubscription(userId: String, isSubscribed: Boolean): UserPreferences {
        logger.info("Updating marketing subscription for user: $userId to $isSubscribed")

        val existing = userPreferencesRepository.findByUserId(userId)

        val updated = if (existing != null) {
            // Update existing preferences
            existing.copy(
                isMarketingSubscribed = isSubscribed,
                updatedAt = OffsetDateTime.now()
            )
        } else {
            // Create new preferences with provided value
            UserPreferences(
                userId = userId,
                isMarketingSubscribed = isSubscribed,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now()
            )
        }

        return userPreferencesRepository.save(updated).also {
            logger.info("Updated marketing subscription for user: $userId to $isSubscribed")
        }
    }

    /**
     * Check if user is subscribed to marketing newsletter
     * @param userId The user's ID
     * @return true if subscribed, false otherwise
     */
    suspend fun isMarketingSubscribed(userId: String): Boolean {
        val preferences = userPreferencesRepository.findByUserId(userId)
        return preferences?.isMarketingSubscribed ?: false
    }
}
