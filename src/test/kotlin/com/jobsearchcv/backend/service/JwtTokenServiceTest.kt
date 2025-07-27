package com.jobsearchcv.backend.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.util.*

class JwtTokenServiceTest {

    private lateinit var jwtTokenService: JwtTokenService
    private val testSecret = "test-jwt-secret-key-for-testing-purposes-only"
    private val secretKey = Keys.hmacShaKeyFor(testSecret.toByteArray())

    @BeforeEach
    fun setup() {
        jwtTokenService = JwtTokenService()
        ReflectionTestUtils.setField(jwtTokenService, "jwtSecret", testSecret)
    }

    @Test
    fun `should decode valid JWT token`() {
        // Create a test JWT token
        val userId = "test-user-123"
        val email = "test@example.com"
        val role = "authenticated"
        
        val token = Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .signWith(secretKey)
            .compact()

        // Test decoding
        val claims = jwtTokenService.decodeToken(token)
        assertNotNull(claims)
        assertEquals(userId, claims?.subject)
        assertEquals(email, claims?.get("email", String::class.java))
        assertEquals(role, claims?.get("role", String::class.java))
    }

    @Test
    fun `should extract user from valid token`() {
        // Create a test JWT token
        val userId = "test-user-456"
        val email = "user@example.com"
        val role = "authenticated"
        
        val token = Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 3600000))
            .signWith(secretKey)
            .compact()

        // Test user extraction
        val user = jwtTokenService.validateTokenAndExtractUser(token)
        assertNotNull(user)
        assertEquals(userId, user?.id)
        assertEquals(email, user?.email)
        assertEquals(role, user?.role)
    }

    @Test
    fun `should handle Bearer prefix in token`() {
        val userId = "test-user-789"
        val token = Jwts.builder()
            .subject(userId)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 3600000))
            .signWith(secretKey)
            .compact()

        // Test with Bearer prefix
        val bearerToken = "Bearer $token"
        val claims = jwtTokenService.decodeToken(bearerToken)
        assertNotNull(claims)
        assertEquals(userId, claims?.subject)
    }

    @Test
    fun `should return null for expired token`() {
        val token = Jwts.builder()
            .subject("test-user")
            .issuedAt(Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
            .expiration(Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
            .signWith(secretKey)
            .compact()

        val claims = jwtTokenService.decodeToken(token)
        assertNull(claims)
        assertFalse(jwtTokenService.isTokenValid(token))
        assertTrue(jwtTokenService.isTokenExpired(token))
    }

    @Test
    fun `should return null for invalid token`() {
        val invalidToken = "invalid.jwt.token"
        
        val claims = jwtTokenService.decodeToken(invalidToken)
        assertNull(claims)
        assertFalse(jwtTokenService.isTokenValid(invalidToken))
    }

    @Test
    fun `should return null for token with wrong signature`() {
        val wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-that-doesnt-match".toByteArray())
        val token = Jwts.builder()
            .subject("test-user")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 3600000))
            .signWith(wrongKey)
            .compact()

        val claims = jwtTokenService.decodeToken(token)
        assertNull(claims)
    }
}