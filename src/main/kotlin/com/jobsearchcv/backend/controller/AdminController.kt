package com.jobsearchcv.backend.controller

import com.google.gson.JsonObject
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.AdminAuthService
import com.jobsearchcv.backend.service.AdminAuditService
import com.jobsearchcv.backend.service.SubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for checking subscription status")
class AdminController(
    private val adminAuthService: AdminAuthService,
    private val subscriptionService: SubscriptionService,
    private val adminAuditService: AdminAuditService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Admin override endpoints have been removed.
     * To grant/revoke premium access, admins should use the Stripe Dashboard directly.
     * This ensures Stripe is the single source of truth for subscription data.
     */

    @GetMapping("/subscriptions/{userId}/status")
    @Operation(summary = "Get subscription status for any user (admin only)")
    fun getAnyUserSubscriptionStatus(
        @PathVariable userId: String,
        @RequestHeader("X-Admin-Secret") adminSecret: String
    ): ResponseEntity<SubscriptionStatusResponse> {
        
        try {
            // Validate admin secret
            adminAuthService.requireAdminSecret(adminSecret)
            
            val status = subscriptionService.getSubscriptionStatus(userId)
            
            // Log successful admin action
            adminAuditService.logAdminAction(
                action = "CHECK_STATUS",
                targetUserId = userId,
                details = "Tier: ${status.tier}, Status: ${status.status}, Premium: ${status.hasPremiumAccess}",
                success = true
            )
            
            return ResponseEntity.ok(status)
            
        } catch (e: IllegalArgumentException) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "CHECK_STATUS",
                targetUserId = userId,
                details = null,
                success = false,
                errorMessage = e.message
            )
            
            logger.error("Admin status check failed: {}", e.message)
            return ResponseEntity.status(403).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    hasPremiumAccess = false,
                    cachedAt = Instant.now()
                )
            )
        } catch (e: Exception) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "CHECK_STATUS",
                targetUserId = userId,
                details = null,
                success = false,
                errorMessage = e.message
            )

            logger.error("Error checking subscription status for user: {}", userId, e)
            return ResponseEntity.status(500).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    hasPremiumAccess = false,
                    cachedAt = Instant.now()
                )
            )
        }
    }
}