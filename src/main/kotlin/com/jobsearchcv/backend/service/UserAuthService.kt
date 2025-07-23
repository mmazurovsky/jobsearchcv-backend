package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.util.JwtUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpServletRequest
import java.util.*

@Service
class UserAuthService(
    private val jwtUtil: JwtUtil
) {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserAuthService::class.java)
        private const val BEARER_PREFIX = "Bearer "
        private const val TEMP_USER_PREFIX = "temp-"
    }
    
    /**
     * Extracts user ID from the request. 
     * First tries to get it from the Authorization header (bearer token).
     * If no bearer token is present, generates a temporary user ID.
     */
    fun extractUserIdFromRequest(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
        
        return if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            // Extract user ID from bearer token
            extractUserIdFromBearerToken(authHeader.substring(BEARER_PREFIX.length))
        } else {
            // Generate temporary user ID for guest users
            generateTemporaryUserId()
        }
    }
    
    /**
     * Extracts user ID from bearer token.
     * This is a simplified implementation - you should replace this with your actual JWT parsing logic.
     */
    private fun extractUserIdFromBearerToken(token: String): String {
        try {
            logger.info("Extracting user ID from bearer token: ${token.take(10)}...")
            
            // TODO: Replace this with actual JWT token parsing
            // For now, we'll assume the token format is valid and extract user ID
            // In a real implementation, you would:
            // 1. Verify the JWT signature
            // 2. Check token expiration
            // 3. Extract user ID from the payload
            
            // Placeholder implementation - replace with actual JWT parsing
            // This assumes you have a JWT library like jjwt-api
            val userId = parseJwtToken(token)
            
            logger.info("Successfully extracted user ID from token: $userId")
            return userId
            
        } catch (e: Exception) {
            logger.warn("Failed to parse bearer token, generating temporary user ID: ${e.message}")
            return generateTemporaryUserId()
        }
    }
    
    /**
     * Parses JWT token to extract user ID using JwtUtil.
     */
    private fun parseJwtToken(token: String): String {
        return if (jwtUtil.isTokenValid(token)) {
            jwtUtil.extractUserIdFromToken(token) ?: throw IllegalArgumentException("Unable to extract user ID from valid token")
        } else {
            throw IllegalArgumentException("Invalid JWT token")
        }
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