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
        summary = "Add a new destination",
        description = "Creates a new notification destination for the authenticated user."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Destination created successfully"),
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

            logger.info("Adding destination for user: $userId, channel: ${request.channel}")

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
            
            // Check if destination already exists
            val existingDestination = destinationRepository.findByUserIdAndChannelAndChannelValue(
                userId, 
                channel.value, 
                request.channelValue
            )
            
            // Check if user had any destinations before this operation
            val userDestinationsBefore = destinationRepository.findByUserId(userId)
            val hadDestinationsBefore = userDestinationsBefore.isNotEmpty()
            
            val savedDestination = if (existingDestination != null) {
                logger.info("Destination already exists for user: $userId, updating createdAt")
                // Update the existing destination with new createdAt
                val updatedDestination = existingDestination.copy(createdAt = java.time.OffsetDateTime.now())
                destinationRepository.save(updatedDestination)
            } else {
                // Create new destination
                val destination = Destination.createNew(
                    userId = userId,
                    channel = channel,
                    channelValue = request.channelValue
                )
                // Save to database
                destinationRepository.save(destination)
            }
            
            // Create Stripe customer when user creates first email destination
            // This ensures we have customer ID for future subscription management
            if (existingDestination == null && channel == Channel.EMAIL) {
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
            
            // If user didn't have destinations before and this is a new destination,
            // schedule all their approved job searches
            if (!hadDestinationsBefore && existingDestination == null) {
                try {
                    logger.info("User $userId added first destination, scheduling approved job searches")
                    runBlocking {
                        subscriptionAwareSchedulingService.scheduleAllApprovedSubscribedSearchesForUser(userId)
                    }
                    
                    // If this is the first EMAIL destination, also trigger monthly overview
                    if (channel == Channel.EMAIL) {
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
                    logger.error("Failed to schedule approved job searches for user $userId after adding first destination", e)
                    // Don't fail the destination creation if scheduling fails
                }
            }
            
            val messageText = if (existingDestination != null) {
                "Destination updated successfully"
            } else {
                "Destination added successfully"
            }
            
            logger.info("Successfully processed destination: id=${savedDestination.id}, userId=$userId")
            
            return@runBlocking ResponseEntity.ok(DestinationResponse(
                success = true,
                message = messageText,
                destination = savedDestination
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to add destination", e)
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

    @DeleteMapping("/{destinationId}")
    @Operation(
        summary = "Delete a destination",
        description = "Deletes a notification destination. Only the owner can delete their destinations."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Destination deleted successfully"),
        ApiResponse(responseCode = "403", description = "Forbidden - not the owner"),
        ApiResponse(responseCode = "404", description = "Destination not found"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    )
    fun deleteDestination(
        @Parameter(description = "Destination ID") @PathVariable destinationId: String,
        @Parameter(hidden = true) authentication: Authentication
    ): ResponseEntity<Void> = runBlocking {
        try {
            val userId = authentication.principal as String

            logger.info("Deleting destination: id=$destinationId, userId=$userId")
            
            // Check if destination exists and belongs to user
            val destination = destinationRepository.findById(destinationId).orElse(null)
            
            if (destination == null) {
                logger.warn("Destination not found: id=$destinationId")
                return@runBlocking ResponseEntity.notFound().build()
            }
            
            if (destination.userId != userId) {
                logger.warn("User $userId attempted to delete destination belonging to another user")
                return@runBlocking ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            
            // Delete destination
            destinationRepository.deleteById(destinationId)
            
            logger.info("Successfully deleted destination: id=$destinationId")
            return@runBlocking ResponseEntity.noContent().build()
            
        } catch (e: Exception) {
            logger.error("Failed to delete destination: id=$destinationId", e)
            return@runBlocking ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}

// Request/Response DTOs
@Schema(description = "Request to add a new destination")
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