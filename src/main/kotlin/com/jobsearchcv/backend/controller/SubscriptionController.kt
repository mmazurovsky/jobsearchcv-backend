package com.jobsearchcv.backend.controller

import com.jobsearchcv.backend.domain.model.SubscriptionStatusResponse
import com.jobsearchcv.backend.domain.model.StripeWebhookEvent
import com.jobsearchcv.backend.repository.StripeWebhookEventRepository
import com.jobsearchcv.backend.service.SubscriptionService
import com.jobsearchcv.backend.service.StripeService
import com.jobsearchcv.backend.service.UserAuthService
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscription", description = "Subscription management endpoints")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val stripeService: StripeService,
    private val userAuthService: UserAuthService,
    private val webhookEventRepository: StripeWebhookEventRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @GetMapping("/status")
    @Operation(summary = "Get current subscription status")
    fun getSubscriptionStatus(request: HttpServletRequest): ResponseEntity<SubscriptionStatusResponse> {
        val userId = userAuthService.extractUserIdFromRequest(request)
        // Force fresh fetch from Stripe for user-requested status (with rate limiting)
        val status = subscriptionService.getSubscriptionStatus(userId, forceRefresh = true)
        return ResponseEntity.ok(status)
    }
    
    @PostMapping("/webhook")
    @Operation(summary = "Handle Stripe webhook events")
    fun handleStripeWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String
    ): ResponseEntity<String> {
        // Step 1: Verify webhook signature
        val event = try {
            stripeService.constructWebhookEvent(payload, signature)
        } catch (e: Exception) {
            logger.error("Invalid webhook signature: ${e.message}")
            return ResponseEntity.badRequest().body("Invalid signature")
        }
        
        logger.info("Received Stripe webhook event: ${event.type} (${event.id})")
        
        // Step 2: Check for duplicate processing (idempotency)
        if (webhookEventRepository.existsByStripeEventId(event.id)) {
            logger.info("Event ${event.id} already processed, skipping")
            return ResponseEntity.ok("Already processed")
        }
        
        // Step 3: Process event with error handling
        val webhookRecord = try {
            processWebhookEvent(event)
            
            StripeWebhookEvent(
                stripeEventId = event.id,
                eventType = event.type,
                processedSuccessfully = true
            )
        } catch (e: Exception) {
            logger.error("Failed to process webhook event ${event.id}: ${e.message}", e)
            
            StripeWebhookEvent(
                stripeEventId = event.id,
                eventType = event.type,
                processedSuccessfully = false,
                errorMessage = e.message
            )
        }
        
        // Step 4: Save processing record
        webhookEventRepository.save(webhookRecord)
        
        return if (webhookRecord.processedSuccessfully) {
            ResponseEntity.ok("Received")
        } else {
            // Return 200 to prevent Stripe retries for business logic errors
            // Return 500 only for infrastructure issues you want Stripe to retry
            ResponseEntity.ok("Received with errors")
        }
    }
    
    private fun processWebhookEvent(event: com.stripe.model.Event) {
        when (event.type) {
            "checkout.session.completed" -> {
                val sessionOptional = event.dataObjectDeserializer.`object`
                if (!sessionOptional.isPresent) {
                    logger.error("Failed to deserialize checkout.session.completed event ${event.id}. API version: ${event.apiVersion}")
                    logger.error("Attempting manual deserialization from raw JSON")

                    // Try to manually deserialize from the raw JSON data
                    try {
                        val session = event.data.`object` as Session
                        val userId = session.clientReferenceId
                        if (userId != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                subscriptionService.handleCheckoutCompleted(userId, session)
                            }
                            logger.info("Checkout completed for user: $userId (manual deserialization)")
                        } else {
                            throw IllegalArgumentException("No client_reference_id in checkout session")
                        }
                        return
                    } catch (e: Exception) {
                        logger.error("Manual deserialization also failed: ${e.message}", e)
                        throw IllegalStateException("Unable to deserialize checkout session from event", e)
                    }
                }
                val session = sessionOptional.get() as Session
                val userId = session.clientReferenceId
                if (userId != null) {
                    // Use coroutine for async email sending - fire and forget
                    CoroutineScope(Dispatchers.IO).launch {
                        subscriptionService.handleCheckoutCompleted(userId, session)
                    }
                    logger.info("Checkout completed for user: $userId")
                } else {
                    throw IllegalArgumentException("No client_reference_id in checkout session")
                }
            }

            "customer.subscription.created" -> {
                val subscriptionOptional = event.dataObjectDeserializer.`object`
                if (!subscriptionOptional.isPresent) {
                    logger.error("Failed to deserialize customer.subscription.created event ${event.id}")
                    throw IllegalStateException("Unable to deserialize subscription from event")
                }
                val subscription = subscriptionOptional.get() as Subscription
                val customerId = subscription.customer ?: return

                // Check if we already have a record (from checkout.session.completed)
                val existing = subscriptionService.getSubscriptionByCustomerId(customerId)

                if (existing == null) {
                    // This is an admin-created subscription - create the mapping
                    logger.info("Admin-created subscription detected: ${subscription.id}")

                    // Fetch customer details from Stripe
                    val customer = stripeService.retrieveCustomer(customerId)
                    val userId = customer.metadata["userId"]
                        ?: throw IllegalArgumentException("No userId in customer metadata for customer: $customerId. When creating subscriptions via admin, ensure customer has userId in metadata.")
                    val email = customer.email
                        ?: throw IllegalArgumentException("No email for customer: $customerId")

                    // Create the subscription mapping with billing interval
                    CoroutineScope(Dispatchers.IO).launch {
                        subscriptionService.handleSubscriptionCreated(userId, customerId, email, subscription)
                    }
                    logger.info("Created subscription mapping for admin subscription: userId=$userId, customerId=$customerId")
                } else {
                    logger.info("Subscription created event ${event.id} - record already exists from checkout, skipping")
                }
            }

            "customer.subscription.updated" -> {
                val subscriptionOptional = event.dataObjectDeserializer.`object`
                if (!subscriptionOptional.isPresent) {
                    logger.error("Failed to deserialize customer.subscription.updated event ${event.id}")
                    throw IllegalStateException("Unable to deserialize subscription from event")
                }
                val subscription = subscriptionOptional.get() as Subscription
                subscriptionService.handleSubscriptionUpdated(subscription)
            }

            "customer.subscription.deleted" -> {
                val subscriptionOptional = event.dataObjectDeserializer.`object`
                if (!subscriptionOptional.isPresent) {
                    logger.error("Failed to deserialize customer.subscription.deleted event ${event.id}")
                    throw IllegalStateException("Unable to deserialize subscription from event")
                }
                val subscription = subscriptionOptional.get() as Subscription
                subscriptionService.handleSubscriptionDeleted(subscription)
            }

            "customer.subscription.trial_will_end" -> {
                val subscriptionOptional = event.dataObjectDeserializer.`object`
                if (!subscriptionOptional.isPresent) {
                    logger.error("Failed to deserialize customer.subscription.trial_will_end event ${event.id}")
                    throw IllegalStateException("Unable to deserialize subscription from event")
                }
                val subscription = subscriptionOptional.get() as Subscription
                // Use coroutine for async email sending - fire and forget
                CoroutineScope(Dispatchers.IO).launch {
                    subscriptionService.handleTrialWillEnd(subscription)
                }
            }

            "invoice.payment_succeeded" -> {
                logger.info("Payment succeeded for event ${event.id}")
                // Log successful payment - could track metrics here
            }

            "invoice.payment_failed" -> {
                val invoiceOptional = event.dataObjectDeserializer.`object`
                if (!invoiceOptional.isPresent) {
                    logger.error("Failed to deserialize invoice.payment_failed event ${event.id}")
                    throw IllegalStateException("Unable to deserialize invoice from event")
                }
                val invoice = invoiceOptional.get() as com.stripe.model.Invoice
                // Use coroutine for async email sending - fire and forget
                CoroutineScope(Dispatchers.IO).launch {
                    subscriptionService.handlePaymentFailed(invoice)
                }
            }

            else -> {
                logger.info("Unhandled webhook event type: ${event.type}")
            }
        }
    }
    
    // Implementation complete!
    // 
    // Frontend integration:
    // 1. Checkout: Redirect to ${STRIPE_PREMIUM_CHECKOUT_URL}?client_reference_id=${userId}
    // 2. Customer Portal: Redirect to ${STRIPE_CUSTOMER_PORTAL_URL}
    // 3. Check subscription status: GET /api/subscriptions/status
}