package com.jobsearchcv.backend.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FirebaseAuthService(
    private val firebaseJwtTokenService: FirebaseJwtTokenService?
) {
    
    private val log: Logger = LoggerFactory.getLogger(FirebaseAuthService::class.java)
    
    fun validateTokenAndExtractUser(token: String): FirebaseUser? {
        if (firebaseJwtTokenService == null) {
            log.debug("Firebase JWT Token Service not available - authentication disabled")
            return null
        }
        return try {
            firebaseJwtTokenService.validateTokenAndExtractUser(token)
        } catch (e: Exception) {
            log.debug("JWT validation failed: ${e.message}")
            null
        }
    }
    
    fun isTokenValid(token: String): Boolean {
        return firebaseJwtTokenService?.isTokenValid(token) ?: false
    }
    
    fun isTokenExpired(token: String): Boolean {
        return firebaseJwtTokenService?.isTokenExpired(token) ?: true
    }

    /**
     * Retrieves user email from Firebase by user ID
     * @param uid Firebase user ID
     * @return User's email address or null if not found/Firebase not enabled
     */
    fun getUserEmail(uid: String): String? {
        return firebaseJwtTokenService?.getUserEmail(uid)
    }

    /**
     * Retrieves full user record from Firebase by user ID
     * @param uid Firebase user ID
     * @return FirebaseUser object or null if not found/Firebase not enabled
     */
    fun getUserById(uid: String): FirebaseUser? {
        return firebaseJwtTokenService?.getUserById(uid)
    }
}

data class FirebaseUser(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val photoUrl: String?,
    val provider: String?
)