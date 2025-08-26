package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.exception.UnauthorizedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpServletRequest
import java.util.*

@Service
class UserAuthService(
    private val firebaseAuthService: FirebaseAuthService
) {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserAuthService::class.java)
        private const val BEARER_PREFIX = "Bearer "
        private const val TEMP_USER_PREFIX = "temp-"
    }
    
    /**
     * Extracts user ID from the request. 
     * Requires a valid Authorization header with bearer token.
     * Throws exception if no valid token is present.
     */
    fun extractUserIdFromRequest(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw UnauthorizedException("Authorization header with bearer token is required")
        }
        
        // Extract user ID from bearer token
        return extractUserIdFromBearerToken(authHeader.substring(BEARER_PREFIX.length))
    }
    
    /**
     * Extracts user ID from bearer token using Firebase authentication.
     */
    private fun extractUserIdFromBearerToken(token: String): String {
        try {
            logger.info("Extracting user ID from Firebase bearer token: ${token.take(10)}...")
            
            val userId = parseJwtToken(token)
            
            logger.info("Successfully extracted user ID from Firebase token: $userId")
            return userId
            
        } catch (e: Exception) {
            logger.error("Failed to parse Firebase bearer token: ${e.message}")
            throw UnauthorizedException("Invalid bearer token", e)
        }
    }
    
    /**
     * Parses JWT token to extract user ID using FirebaseAuthService.
     */
    private fun parseJwtToken(token: String): String {
        val firebaseUser = firebaseAuthService.validateTokenAndExtractUser(token)
        return firebaseUser?.uid ?: throw IllegalArgumentException("Invalid JWT token or unable to extract user ID")
    }
    
    /**
     * Generates a temporary user ID for guest users.
     */
    private fun generateTemporaryUserId(): String {
        val tempId = "$TEMP_USER_PREFIX${UUID.randomUUID()}"
        logger.info("Generated temporary user ID for guest user: $tempId")
        return tempId
    }
    
    /**
     * Checks if a user ID is temporary (guest user).
     */
    fun isTemporaryUser(userId: String): Boolean {
        return userId.startsWith(TEMP_USER_PREFIX)
    }
    
    /**
     * Converts user ID to Long format, handling temporary users.
     * For temporary users, returns a hash-based Long ID.
     */
    fun userIdToLong(userId: String): Long {
        return if (isTemporaryUser(userId)) {
            // For temporary users, generate a consistent Long ID based on the UUID
            userId.hashCode().toLong().let { if (it < 0) -it else it }
        } else {
            // For authenticated users, try to parse as Long or use hash
            userId.toLongOrNull() ?: userId.hashCode().toLong().let { if (it < 0) -it else it }
        }
    }
}