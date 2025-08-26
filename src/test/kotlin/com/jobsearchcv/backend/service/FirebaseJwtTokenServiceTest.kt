package com.jobsearchcv.backend.service

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class FirebaseJwtTokenServiceTest {

    @Mock
    private lateinit var firebaseAuth: FirebaseAuth

    @Mock
    private lateinit var firebaseToken: FirebaseToken

    private lateinit var firebaseJwtTokenService: FirebaseJwtTokenService

    @BeforeEach
    fun setup() {
        firebaseJwtTokenService = FirebaseJwtTokenService()
        
        val firebaseAuthField = FirebaseJwtTokenService::class.java.getDeclaredField("firebaseAuth")
        firebaseAuthField.isAccessible = true
        firebaseAuthField.set(firebaseJwtTokenService, firebaseAuth)
        
        val projectIdField = FirebaseJwtTokenService::class.java.getDeclaredField("firebaseProjectId")
        projectIdField.isAccessible = true
        projectIdField.set(firebaseJwtTokenService, "test-project-id")
    }

    @Test
    fun `decodeToken should return FirebaseToken when valid token is provided`() {
        val token = "valid.token.here"
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.uid).thenReturn("test-uid")

        val result = firebaseJwtTokenService.decodeToken(token)

        assertNotNull(result)
        assertEquals(firebaseToken, result)
        verify(firebaseAuth).verifyIdToken(token)
    }

    @Test
    fun `decodeToken should strip Bearer prefix from token`() {
        val token = "valid.token.here"
        val bearerToken = "Bearer $token"
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.uid).thenReturn("test-uid")

        val result = firebaseJwtTokenService.decodeToken(bearerToken)

        assertNotNull(result)
        assertEquals(firebaseToken, result)
        verify(firebaseAuth).verifyIdToken(token)
    }

    @Test
    fun `decodeToken should return null when token is expired`() {
        val token = "expired.token.here"
        val exception = mock(FirebaseAuthException::class.java)
        whenever(exception.authErrorCode).thenReturn(AuthErrorCode.EXPIRED_ID_TOKEN)
        whenever(firebaseAuth.verifyIdToken(token)).thenThrow(exception)

        val result = firebaseJwtTokenService.decodeToken(token)

        assertNull(result)
        verify(firebaseAuth).verifyIdToken(token)
    }

    @Test
    fun `decodeToken should return null when token is invalid`() {
        val token = "invalid.token.here"
        val exception = mock(FirebaseAuthException::class.java)
        whenever(exception.authErrorCode).thenReturn(AuthErrorCode.INVALID_ID_TOKEN)
        whenever(firebaseAuth.verifyIdToken(token)).thenThrow(exception)

        val result = firebaseJwtTokenService.decodeToken(token)

        assertNull(result)
        verify(firebaseAuth).verifyIdToken(token)
    }

    @Test
    fun `decodeToken should return null when token is revoked`() {
        val token = "revoked.token.here"
        val exception = mock(FirebaseAuthException::class.java)
        whenever(exception.authErrorCode).thenReturn(AuthErrorCode.REVOKED_ID_TOKEN)
        whenever(firebaseAuth.verifyIdToken(token)).thenThrow(exception)

        val result = firebaseJwtTokenService.decodeToken(token)

        assertNull(result)
        verify(firebaseAuth).verifyIdToken(token)
    }

    @Test
    fun `extractUserFromToken should return FirebaseUser when valid token is provided`() {
        val claims = mapOf(
            "firebase" to mapOf("sign_in_provider" to "google.com")
        )
        
        whenever(firebaseToken.uid).thenReturn("test-uid")
        whenever(firebaseToken.email).thenReturn("test@example.com")
        whenever(firebaseToken.isEmailVerified).thenReturn(true)
        whenever(firebaseToken.name).thenReturn("Test User")
        whenever(firebaseToken.picture).thenReturn("https://example.com/photo.jpg")
        whenever(firebaseToken.claims).thenReturn(claims)

        val result = firebaseJwtTokenService.extractUserFromToken(firebaseToken)

        assertNotNull(result)
        assertEquals("test-uid", result?.uid)
        assertEquals("test@example.com", result?.email)
        assertEquals(true, result?.emailVerified)
        assertEquals("Test User", result?.displayName)
        assertEquals("https://example.com/photo.jpg", result?.photoUrl)
        assertEquals("google.com", result?.provider)
    }

    @Test
    fun `validateTokenAndExtractUser should return FirebaseUser when valid token is provided`() {
        val token = "valid.token.here"
        val claims = mapOf(
            "firebase" to mapOf("sign_in_provider" to "google.com")
        )
        
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.uid).thenReturn("test-uid")
        whenever(firebaseToken.email).thenReturn("test@example.com")
        whenever(firebaseToken.isEmailVerified).thenReturn(true)
        whenever(firebaseToken.name).thenReturn("Test User")
        whenever(firebaseToken.picture).thenReturn("https://example.com/photo.jpg")
        whenever(firebaseToken.claims).thenReturn(claims)

        val result = firebaseJwtTokenService.validateTokenAndExtractUser(token)

        assertNotNull(result)
        assertEquals("test-uid", result?.uid)
        assertEquals("test@example.com", result?.email)
    }

    @Test
    fun `validateTokenAndExtractUser should return null when invalid token is provided`() {
        val token = "invalid.token.here"
        val exception = mock(FirebaseAuthException::class.java)
        whenever(exception.authErrorCode).thenReturn(AuthErrorCode.INVALID_ID_TOKEN)
        whenever(firebaseAuth.verifyIdToken(token)).thenThrow(exception)

        val result = firebaseJwtTokenService.validateTokenAndExtractUser(token)

        assertNull(result)
    }

    @Test
    fun `isTokenValid should return true for valid token`() {
        val token = "valid.token.here"
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)

        val result = firebaseJwtTokenService.isTokenValid(token)

        assertTrue(result)
    }

    @Test
    fun `isTokenValid should return false for invalid token`() {
        val token = "invalid.token.here"
        val exception = mock(FirebaseAuthException::class.java)
        whenever(exception.authErrorCode).thenReturn(AuthErrorCode.INVALID_ID_TOKEN)
        whenever(firebaseAuth.verifyIdToken(token)).thenThrow(exception)

        val result = firebaseJwtTokenService.isTokenValid(token)

        assertFalse(result)
    }

    @Test
    fun `getTokenExpiration should return expiration date for valid token`() {
        val token = "valid.token.here"
        val expTime = System.currentTimeMillis() / 1000 + 3600
        val claims = mapOf("exp" to expTime)
        
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.claims).thenReturn(claims)

        val result = firebaseJwtTokenService.getTokenExpiration(token)

        assertNotNull(result)
        assertEquals(Date(expTime * 1000), result)
    }

    @Test
    fun `isTokenExpired should return true for expired token`() {
        val token = "expired.token.here"
        val expTime = System.currentTimeMillis() / 1000 - 3600
        val claims = mapOf("exp" to expTime)
        
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.claims).thenReturn(claims)

        val result = firebaseJwtTokenService.isTokenExpired(token)

        assertTrue(result)
    }

    @Test
    fun `isTokenExpired should return false for non-expired token`() {
        val token = "valid.token.here"
        val expTime = System.currentTimeMillis() / 1000 + 3600
        val claims = mapOf("exp" to expTime)
        
        whenever(firebaseAuth.verifyIdToken(token)).thenReturn(firebaseToken)
        whenever(firebaseToken.claims).thenReturn(claims)

        val result = firebaseJwtTokenService.isTokenExpired(token)

        assertFalse(result)
    }
}