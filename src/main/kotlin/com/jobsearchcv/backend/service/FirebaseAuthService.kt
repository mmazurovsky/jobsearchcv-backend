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
}

data class FirebaseUser(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val photoUrl: String?,
    val provider: String?
)