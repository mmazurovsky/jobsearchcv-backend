package com.jobsearchcv.backend.service

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
@ConditionalOnProperty(name = ["firebase.enabled"], havingValue = "true", matchIfMissing = false)
@DependsOn("firebaseConfig")
class FirebaseJwtTokenService {

    private val log: Logger = LoggerFactory.getLogger(FirebaseJwtTokenService::class.java)
    
    @Value("\${firebase.project-id:applyfirst-b9c69}")
    private lateinit var firebaseProjectId: String
    
    private lateinit var firebaseAuth: FirebaseAuth
    
    @PostConstruct
    fun initialize() {
        try {
            firebaseAuth = FirebaseAuth.getInstance()
            log.info("Firebase Auth initialized for project: $firebaseProjectId")
        } catch (e: IllegalStateException) {
            log.error("FirebaseApp not initialized. Make sure FirebaseConfig runs before this service.", e)
            throw RuntimeException("Firebase not properly initialized", e)
        }
    }

    fun decodeToken(token: String): FirebaseToken? {
        return try {
            val cleanToken = token.removePrefix("Bearer ").trim()
            val firebaseToken = firebaseAuth.verifyIdToken(cleanToken)
            
            log.debug("Successfully verified token for user: ${firebaseToken.uid}")
            firebaseToken
        } catch (e: FirebaseAuthException) {
            when (e.authErrorCode) {
                AuthErrorCode.EXPIRED_ID_TOKEN -> {
                    log.debug("Firebase token is expired: ${e.message}")
                }
                AuthErrorCode.REVOKED_ID_TOKEN -> {
                    log.debug("Firebase token has been revoked: ${e.message}")
                }
                AuthErrorCode.INVALID_ID_TOKEN -> {
                    log.debug("Firebase token is invalid: ${e.message}")
                }
                else -> {
                    log.debug("Firebase auth error (${e.authErrorCode}): ${e.message}")
                }
            }
            null
        } catch (e: IllegalArgumentException) {
            log.debug("Invalid token format: ${e.message}")
            null
        } catch (e: Exception) {
            log.error("Unexpected error while verifying Firebase token", e)
            null
        }
    }

    fun extractUserFromToken(firebaseToken: FirebaseToken): FirebaseUser? {
        return try {
            val claims = firebaseToken.claims
            
            FirebaseUser(
                uid = firebaseToken.uid,
                email = firebaseToken.email,
                emailVerified = firebaseToken.isEmailVerified,
                displayName = firebaseToken.name,
                photoUrl = firebaseToken.picture,
                provider = claims["firebase"]?.let { 
                    (it as? Map<*, *>)?.get("sign_in_provider") as? String 
                }
            )
        } catch (e: Exception) {
            log.error("Error extracting user from Firebase token", e)
            null
        }
    }

    fun validateTokenAndExtractUser(token: String): FirebaseUser? {
        val firebaseToken = decodeToken(token) ?: return null
        return extractUserFromToken(firebaseToken)
    }

    fun isTokenValid(token: String): Boolean {
        return decodeToken(token) != null
    }

    fun getTokenExpiration(token: String): Date? {
        val firebaseToken = decodeToken(token) ?: return null
        return Date(firebaseToken.claims["exp"] as Long * 1000)
    }

    fun isTokenExpired(token: String): Boolean {
        val expiration = getTokenExpiration(token) ?: return true
        return expiration.before(Date())
    }
}