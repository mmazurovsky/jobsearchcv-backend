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
        val status = subscriptionService.getSubscriptionStatus(userId)
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
                val session = event.dataObjectDeserializer.`object`.get() as Session
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
                val subscription = event.dataObjectDeserializer.`object`.get() as Subscription
                subscriptionService.handleSubscriptionCreated(subscription)
            }
            
            "customer.subscription.updated" -> {
                val subscription = event.dataObjectDeserializer.`object`.get() as Subscription
                subscriptionService.handleSubscriptionUpdated(subscription)
            }
            
            "customer.subscription.deleted" -> {
                val subscription = event.dataObjectDeserializer.`object`.get() as Subscription
                subscriptionService.handleSubscriptionDeleted(subscription)
            }
            
            "customer.subscription.trial_will_end" -> {
                val subscription = event.dataObjectDeserializer.`object`.get() as Subscription
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
                val invoice = event.dataObjectDeserializer.`object`.get() as com.stripe.model.Invoice
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