package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.PreferenceOperationResponse
import com.jobsearchcv.backend.domain.model.UpdateMarketingSubscriptionRequest
import com.jobsearchcv.backend.domain.model.UserPreferences
import com.jobsearchcv.backend.service.UserPreferencesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user-preferences")
@Tag(name = "User Preferences", description = "Manage user preferences and settings")
@SecurityRequirement(name = "bearerAuth")
class UserPreferencesController(
    private val userPreferencesService: UserPreferencesService
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserPreferencesController::class.java)
    }

    @GetMapping
    @Operation(
        summary = "Get user preferences",
        description = "Retrieves the authenticated user's preferences. Creates default preferences if none exist."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun getUserPreferences(
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<UserPreferences> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("GET /api/user-preferences - user: $userId")

            val preferences = userPreferencesService.getUserPreferences(userId)

            logger.info("Successfully retrieved preferences for user: $userId")
            return@runBlocking ResponseEntity.ok(preferences)

        } catch (e: Exception) {
            logger.error("Failed to get user preferences", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PatchMapping("/marketing-subscription")
    @Operation(
        summary = "Update marketing newsletter subscription",
        description = "Updates the user's marketing newsletter subscription status"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Subscription updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun updateMarketingSubscription(
        @RequestBody request: UpdateMarketingSubscriptionRequest,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<PreferenceOperationResponse> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("PATCH /api/user-preferences/marketing-subscription - user: $userId, isSubscribed: ${request.isSubscribed}")

            val updatedPreferences = userPreferencesService.updateMarketingSubscription(
                userId = userId,
                isSubscribed = request.isSubscribed
            )

            logger.info("Successfully updated marketing subscription for user: $userId to ${request.isSubscribed}")

            return@runBlocking ResponseEntity.ok(PreferenceOperationResponse(
                success = true,
                message = "Marketing subscription updated successfully",
                preferences = updatedPreferences
            ))

        } catch (e: Exception) {
            logger.error("Failed to update marketing subscription", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PreferenceOperationResponse(
                    success = false,
                    message = "Internal server error: ${e.message}",
                    preferences = null
                ))
        }
    }
}
