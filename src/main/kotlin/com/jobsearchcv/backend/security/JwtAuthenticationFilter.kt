package com.jobsearchcv.backend.security

import com.jobsearchcv.backend.service.SupabaseAuthService
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
    private val supabaseAuthService: SupabaseAuthService
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
        
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            val token = authHeader.substring(BEARER_PREFIX.length)
            
            try {
                val supabaseUser = supabaseAuthService.validateTokenAndExtractUser(token)
                if (supabaseUser != null) {
                    // Create authentication token with user ID as principal and user object as details
                    val authentication = UsernamePasswordAuthenticationToken(
                        supabaseUser.id, null, emptyList()
                    ).apply {
                        details = supabaseUser
                    }
                    SecurityContextHolder.getContext().authentication = authentication
                    log.debug("Set authentication for user: ${supabaseUser.id}, email: ${supabaseUser.email}")
                }
            } catch (e: Exception) {
                log.warn("Invalid JWT token: ${e.message}")
            }
        }
        
        filterChain.doFilter(request, response)
    }
}