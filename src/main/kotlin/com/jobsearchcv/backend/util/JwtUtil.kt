package com.jobsearchcv.backend.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class JwtUtil {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JwtUtil::class.java)
    }
    
    /**
     * Extracts user ID from JWT token.
     * This is a placeholder implementation. In production, you should use a proper JWT library
     * like jjwt-api and implement proper token validation, signature verification, and expiration checks.
     */
    fun extractUserIdFromToken(token: String): String? {
        return try {
            // TODO: Replace with actual JWT parsing
            // Example using jjwt library:
            // val claims = Jwts.parserBuilder()
            //     .setSigningKey(secretKey)
            //     .build()
            //     .parseClaimsJws(token)
            //     .body
            // return claims.subject ?: claims.get("user_id") as String?
            
            // For now, we assume the token is from Supabase and extract the 'sub' claim
            // This is a mock implementation - DO NOT use in production
            if (token.isNotBlank()) {
                // Mock: return a consistent user ID based on token hash
                "user-${token.hashCode().let { if (it < 0) -it else it }}"
            } else {
                null
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse JWT token: ${e.message}")
            null
        }
    }
    
    /**
     * Validates if the JWT token is valid (not expired, proper signature, etc.)
     * This is a placeholder implementation.
     */
    fun isTokenValid(token: String): Boolean {
        return try {
            // TODO: Implement actual token validation
            // For now, just check if token is not empty
            token.isNotBlank() && token.length > 10
        } catch (e: Exception) {
            logger.warn("Token validation failed: ${e.message}")
            false
        }
    }
}