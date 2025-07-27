package com.jobsearchcv.backend.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SupabaseAuthService(
    private val jwtTokenService: JwtTokenService
) {
    
    private val log: Logger = LoggerFactory.getLogger(SupabaseAuthService::class.java)
    
    /**
     * Validates Supabase JWT token and extracts user information
     */
    fun validateTokenAndExtractUser(token: String): SupabaseUser? {
        return try {
            jwtTokenService.validateTokenAndExtractUser(token)
        } catch (e: Exception) {
            log.debug("JWT validation failed: ${e.message}")
            null
        }
    }
    
    /**
     * Checks if token is valid without extracting user info
     */
    fun isTokenValid(token: String): Boolean {
        return jwtTokenService.isTokenValid(token)
    }
    
    /**
     * Checks if token is expired
     */
    fun isTokenExpired(token: String): Boolean {
        return jwtTokenService.isTokenExpired(token)
    }
}

/**
 * Represents a Supabase authenticated user
 */
data class SupabaseUser(
    val id: String,
    val email: String?,
    val role: String?
)