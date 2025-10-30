package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.Channel
import com.jobsearchcv.backend.domain.model.Destination
import com.jobsearchcv.backend.repository.DestinationRepository
import com.jobsearchcv.backend.service.SubscriptionAwareSchedulingService
import com.jobsearchcv.backend.service.SubscriptionService
import com.jobsearchcv.backend.service.MonthlyOverviewService
import com.jobsearchcv.backend.service.FirebaseUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
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
@RequestMapping("/api/destinations")
@Tag(name = "Destinations", description = "Manage user notification destinations")
@SecurityRequirement(name = "bearerAuth")
class DestinationController(
    private val destinationRepository: DestinationRepository,
    private val subscriptionAwareSchedulingService: SubscriptionAwareSchedulingService,
    private val subscriptionService: SubscriptionService,
    private val monthlyOverviewService: MonthlyOverviewService
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DestinationController::class.java)
    }

    @PostMapping
    @Operation(
        summary = "Set user destination",
        description = "Sets the notification destination for the authenticated user. Only one destination per user is allowed. Any existing destinations will be replaced."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Destination set successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request or channel"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun addDestination(
        @RequestBody request: AddDestinationRequest,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<DestinationResponse> = runBlocking {
        try {
            // Extract user ID from authentication
            val userId = authentication.principal as String

            // Get user details from authentication
            val userDetails = authentication.details as? FirebaseUser

            logger.info("Setting destination for user: $userId, channel: ${request.channel}")

            // Validate channel
            val channel = try {
                Channel.fromString(request.channel)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid channel: ${request.channel}")
                return@runBlocking ResponseEntity.badRequest()
                    .body(DestinationResponse(
                        success = false,
                        message = "Invalid channel. Allowed values: ${Channel.values().map { it.value }}",
                        destination = null
                    ))
            }

            // Get existing destinations
            val existingDestinations = destinationRepository.findByUserId(userId)
            val hadDestinationsBefore = existingDestinations.isNotEmpty()

            // Check if the new destination is the same as existing one
            val existingDestination = existingDestinations.find {
                it.channel == channel.value && it.channelValue == request.channelValue
            }

            if (existingDestination != null) {
                // Same destination already exists, just return it
                logger.info("Destination already exists for user: $userId, returning existing destination")
                return@runBlocking ResponseEntity.ok(DestinationResponse(
                    success = true,
                    message = "Destination already set",
                    destination = existingDestination
                ))
            }

            // Delete ALL existing destinations for this user (enforce single destination per user)
            if (existingDestinations.isNotEmpty()) {
                logger.info("Deleting ${existingDestinations.size} existing destination(s) for user: $userId")
                existingDestinations.forEach { dest ->
                    destinationRepository.deleteById(dest.id)
                }
            }

            // Create new destination
            val destination = Destination.createNew(
                userId = userId,
                channel = channel,
                channelValue = request.channelValue
            )
            val savedDestination = destinationRepository.save(destination)

            // Create Stripe customer when user creates first email destination
            // This ensures we have customer ID for future subscription management
            if (!hadDestinationsBefore && channel == Channel.EMAIL) {
                try {
                    subscriptionService.ensureStripeCustomer(
                        userId = userId,
                        email = request.channelValue, // Email from destination
                        name = userDetails?.displayName
                    )
                    logger.info("Created Stripe customer for user: $userId")
                } catch (e: Exception) {
                    logger.error("Failed to create Stripe customer for user: $userId", e)
                    // Don't fail destination creation if Stripe customer creation fails
                }
            }

            // Always reschedule all approved job searches when destination changes
            // This ensures all searches use the new destination
            try {
                logger.info("Destination changed for user $userId, rescheduling approved job searches")
                runBlocking {
                    subscriptionAwareSchedulingService.scheduleAllApprovedSubscribedSearchesForUser(userId)
                }

                // If this is the first EMAIL destination, also trigger monthly overview
                if (!hadDestinationsBefore && channel == Channel.EMAIL) {
                    logger.info("User $userId added first email destination, triggering monthly overview")
                    try {
                        runBlocking {
                            monthlyOverviewService.triggerMonthlyOverviewForUser(userId)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to trigger monthly overview for user $userId", e)
                        // Don't fail the destination creation if monthly overview fails
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to reschedule approved job searches for user $userId after destination change", e)
                // Don't fail the destination creation if scheduling fails
            }

            val messageText = if (hadDestinationsBefore) {
                "Destination updated successfully"
            } else {
                "Destination set successfully"
            }

            logger.info("Successfully set destination: id=${savedDestination.id}, userId=$userId")

            return@runBlocking ResponseEntity.ok(DestinationResponse(
                success = true,
                message = messageText,
                destination = savedDestination
            ))

        } catch (e: Exception) {
            logger.error("Failed to set destination", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DestinationResponse(
                    success = false,
                    message = "Internal server error: ${e.message}",
                    destination = null
                ))
        }
    }

    @GetMapping
    @Operation(
        summary = "Get user destinations",
        description = "Retrieves all notification destinations for the authenticated user"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Destinations retrieved successfully"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun getUserDestinations(
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<List<Destination>> = runBlocking {
        try {
            val userId = authentication.principal as String
            logger.info("Getting destinations for user: $userId")
            
            val destinations = destinationRepository.findByUserId(userId)
            
            logger.info("Found ${destinations.size} destinations for user: $userId")
            return@runBlocking ResponseEntity.ok(destinations)
            
        } catch (e: Exception) {
            logger.error("Failed to get destinations", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

}

// Request/Response DTOs
@Schema(description = "Request to set/update destination")
data class AddDestinationRequest(
    @Schema(description = "Channel type (email, telegram)", example = "email", required = true)
    val channel: String,
    @Schema(description = "Channel value (email address, telegram chat ID)", example = "user@example.com", required = true)
    val channelValue: String
)

@Schema(description = "Response for destination operations")
data class DestinationResponse(
    @Schema(description = "Operation success status", example = "true")
    val success: Boolean,
    @Schema(description = "Response message", example = "Destination added successfully")
    val message: String,
    @Schema(description = "Created or existing destination object")
    val destination: Destination?
)