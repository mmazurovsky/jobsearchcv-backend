package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.AdminAuthService
import com.jobsearchcv.backend.service.AdminAuditService
import com.jobsearchcv.backend.service.SubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for managing subscriptions")
class AdminController(
    private val adminAuthService: AdminAuthService,
    private val subscriptionService: SubscriptionService,
    private val adminAuditService: AdminAuditService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @PostMapping("/subscriptions/{userId}/activate")
    @Operation(summary = "Grant premium access to user (admin only)")
    fun activatePremiumSubscription(
        @PathVariable userId: String,
        @RequestHeader("X-Admin-Secret") adminSecret: String,
        @RequestParam(defaultValue = "365") durationDays: Long
    ): ResponseEntity<SubscriptionStatusResponse> {
        
        try {
            // Validate admin secret
            adminAuthService.requireAdminSecret(adminSecret)
            
            logger.info("Admin activating premium subscription for user: {} for {} days", userId, durationDays)
            
            // Check current premium status BEFORE making changes
            val hadPremium = subscriptionService.checkPremiumAccess(userId)
            
            // Create premium subscription that bypasses Stripe
            val subscription = UserSubscription(
                userId = userId,
                tier = SubscriptionTier.PREMIUM,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = OffsetDateTime.now().plusDays(durationDays),
                trialEnd = null,
                stripeCustomerId = null, // No Stripe involvement
                stripeSubscriptionId = null
            )
            
            // Save the subscription (will update existing or create new)
            subscriptionService.createOrUpdateSubscription(subscription)
            
            // Trigger subscription upgrade logic if user didn't have premium before
            if (!hadPremium) {
                logger.info("User {} did not have premium access, triggering upgrade logic", userId)
                subscriptionService.handleSubscriptionUpgrade(userId)
            } else {
                logger.info("User {} already had premium access, no upgrade logic needed", userId)
            }
            
            // Return current subscription status
            val status = subscriptionService.getSubscriptionStatus(userId)
            
            // Log successful admin action
            adminAuditService.logAdminAction(
                action = "ACTIVATE_PREMIUM",
                targetUserId = userId,
                details = "Duration: $durationDays days, expires: ${subscription.currentPeriodEnd}",
                success = true
            )
            
            logger.info("Successfully activated premium subscription for user: {} until {}", 
                userId, subscription.currentPeriodEnd)
            
            return ResponseEntity.ok(status)
            
        } catch (e: IllegalArgumentException) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "ACTIVATE_PREMIUM",
                targetUserId = userId,
                details = "Duration: $durationDays days",
                success = false,
                errorMessage = e.message
            )
            
            logger.error("Admin activation failed: {}", e.message)
            return ResponseEntity.status(403).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
                )
            )
        } catch (e: Exception) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "ACTIVATE_PREMIUM",
                targetUserId = userId,
                details = "Duration: $durationDays days",
                success = false,
                errorMessage = e.message
            )
            
            logger.error("Error activating premium subscription for user: {}", userId, e)
            return ResponseEntity.status(500).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
                )
            )
        }
    }
    
    @PostMapping("/subscriptions/{userId}/revoke")
    @Operation(summary = "Revoke premium access from user (admin only)")
    fun revokePremiumSubscription(
        @PathVariable userId: String,
        @RequestHeader("X-Admin-Secret") adminSecret: String
    ): ResponseEntity<SubscriptionStatusResponse> {
        
        try {
            // Validate admin secret
            adminAuthService.requireAdminSecret(adminSecret)
            
            logger.info("Admin revoking premium subscription for user: {}", userId)
            
            val existingSubscription = subscriptionService.getSubscription(userId)
            val hadPremium = existingSubscription?.let { 
                subscriptionService.checkPremiumAccess(it.userId) 
            } ?: false
            
            if (existingSubscription != null) {
                // Cancel the subscription
                val canceledSubscription = existingSubscription.copy(
                    status = SubscriptionStatus.CANCELED,
                    updatedAt = OffsetDateTime.now()
                )
                
                subscriptionService.createOrUpdateSubscription(canceledSubscription)
                
                // Trigger subscription downgrade logic if user had premium access
                if (hadPremium) {
                    subscriptionService.handleSubscriptionDowngrade(userId)
                }
                
                logger.info("Successfully revoked premium subscription for user: {}", userId)
            } else {
                logger.info("No subscription found for user: {}, creating FREE subscription", userId)
                
                // Create a FREE subscription record
                val freeSubscription = UserSubscription(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodEnd = null,
                    trialEnd = null,
                    stripeCustomerId = null,
                    stripeSubscriptionId = null
                )
                
                subscriptionService.createOrUpdateSubscription(freeSubscription)
            }
            
            // Log successful admin action
            adminAuditService.logAdminAction(
                action = "REVOKE_PREMIUM",
                targetUserId = userId,
                details = if (existingSubscription != null) "Revoked existing subscription" else "Created FREE subscription",
                success = true
            )
            
            // Return current subscription status
            val status = subscriptionService.getSubscriptionStatus(userId)
            return ResponseEntity.ok(status)
            
        } catch (e: IllegalArgumentException) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "REVOKE_PREMIUM",
                targetUserId = userId,
                details = null,
                success = false,
                errorMessage = e.message
            )
            
            logger.error("Admin revocation failed: {}", e.message)
            return ResponseEntity.status(403).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
                )
            )
        } catch (e: Exception) {
            // Log failed admin action
            adminAuditService.logAdminAction(
                action = "REVOKE_PREMIUM",
                targetUserId = userId,
                details = null,
                success = false,
                errorMessage = e.message
            )
            
            logger.error("Error revoking premium subscription for user: {}", userId, e)
            return ResponseEntity.status(500).body(
                SubscriptionStatusResponse(
                    userId = userId,
                    tier = SubscriptionTier.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
                )
            )
        }
    }
    
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
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
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
                    currentPeriodEnd = null,
                    trialEnd = null,
                    hasPremiumAccess = false
                )
            )
        }
    }
}