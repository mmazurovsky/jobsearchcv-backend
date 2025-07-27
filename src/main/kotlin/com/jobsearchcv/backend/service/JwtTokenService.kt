package com.jobsearchcv.backend.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtTokenService {

    private val log: Logger = LoggerFactory.getLogger(JwtTokenService::class.java)

    @Value("\${supabase.secret}")
    private lateinit var jwtSecret: String

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    /**
     * Decodes and validates a JWT token, returning the claims if valid
     */
    fun decodeToken(token: String): Claims? {
        return try {
            val cleanToken = token.removePrefix("Bearer ").trim()
            
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(cleanToken)
                .payload
        } catch (e: ExpiredJwtException) {
            log.debug("JWT token is expired: ${e.message}")
            null
        } catch (e: UnsupportedJwtException) {
            log.debug("JWT token is unsupported: ${e.message}")
            null
        } catch (e: MalformedJwtException) {
            log.debug("JWT token is malformed: ${e.message}")
            null
        } catch (e: SignatureException) {
            log.debug("JWT signature validation failed: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            log.debug("JWT token is invalid: ${e.message}")
            null
        } catch (e: Exception) {
            log.error("Unexpected error while decoding JWT token", e)
            null
        }
    }

    /**
     * Extracts user information from JWT claims
     */
    fun extractUserFromClaims(claims: Claims): SupabaseUser? {
        return try {
            // Supabase JWT structure typically includes:
            // - sub: user ID
            // - email: user email
            // - role: user role (authenticated, anon, etc.)
            // - app_metadata: additional metadata
            
            val userId = claims.subject ?: return null
            val email = claims.get("email", String::class.java)
            val role = claims.get("role", String::class.java) ?: "authenticated"
            
            SupabaseUser(
                id = userId,
                email = email,
                role = role
            )
        } catch (e: Exception) {
            log.error("Error extracting user from JWT claims", e)
            null
        }
    }

    /**
     * Validates token and extracts user in one operation
     */
    fun validateTokenAndExtractUser(token: String): SupabaseUser? {
        val claims = decodeToken(token) ?: return null
        return extractUserFromClaims(claims)
    }

    /**
     * Checks if token is valid without extracting user info
     */
    fun isTokenValid(token: String): Boolean {
        return decodeToken(token) != null
    }

    /**
     * Gets token expiration time
     */
    fun getTokenExpiration(token: String): Date? {
        return decodeToken(token)?.expiration
    }

    /**
     * Checks if token is expired
     */
    fun isTokenExpired(token: String): Boolean {
        val expiration = getTokenExpiration(token) ?: return true
        return expiration.before(Date())
    }
}