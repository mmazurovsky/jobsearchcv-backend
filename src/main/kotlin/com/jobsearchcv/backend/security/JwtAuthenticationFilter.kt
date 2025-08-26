package com.jobsearchcv.backend.security

import com.jobsearchcv.backend.service.FirebaseAuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val firebaseAuthService: FirebaseAuthService
) : OncePerRequestFilter() {
    
    private val log: Logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    
    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        log.debug("Processing request to ${request.requestURI}, Authorization header present: ${authHeader != null}")
        
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            val token = authHeader.substring(BEARER_PREFIX.length)
            log.debug("Extracted JWT token, length: ${token.length}")
            
            try {
                val firebaseUser = firebaseAuthService.validateTokenAndExtractUser(token)
                if (firebaseUser != null) {
                    // Create authentication token with user ID as principal and user object as details
                    val authentication = UsernamePasswordAuthenticationToken(
                        firebaseUser.uid, null, emptyList()
                    ).apply {
                        details = firebaseUser
                    }
                    SecurityContextHolder.getContext().authentication = authentication
                    log.debug("Set authentication for user: ${firebaseUser.uid}, email: ${firebaseUser.email}")
                } else {
                    log.debug("Token validation returned null user")
                }
            } catch (e: Exception) {
                log.warn("Invalid JWT token: ${e.message}")
            }
        } else {
            log.debug("No Authorization header or doesn't start with Bearer")
        }
        
        filterChain.doFilter(request, response)
    }
}