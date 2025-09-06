# Backend TODOs

## 🔥 High Priority (Critical Issues & Configuration)

most important next step:
After user provides their first destination we need to trigger immediate search execution

### Missing Integration 
- [x] **COMPLETED: Welcome email sending in checkout completion**
  - Added `sendWelcomeEmail(userId)` call to `SubscriptionService.handleCheckoutCompleted()`
  - Updated `createWelcomeEmail()` method with proper trial messaging and premium benefits
  - Follows job search email template conventions with mobile responsiveness
  - Uses premium benefits from frontend subscription_benefits.dart
- [x] **COMPLETED: Subscription-aware job search scheduling**
  - Created `SubscriptionAwareSchedulingService` with subscription-based time period logic
  - Updated `JobSearchScheduler` integration points for startup, new searches, and updates
  - Added subscription change handlers in `SubscriptionService` for upgrade/downgrade scenarios
  - Free users automatically get monthly scheduling regardless of saved time period
  - Premium users get their originally saved time periods
  - Automatic rescheduling when subscription status changes


### Stripe Payment Integration (HIGH RISK)
- [ ] **CRITICAL: Stripe Integration Testing**
  - Comprehensive testing of webhook endpoints and payment flows
  - Test subscription management and status transitions
  - Verify email notifications work correctly
  - Test webhook signature verification and idempotency
- [x] **COMPLETED: Fix email notification architecture** 
  - Replaced runBlocking with AsyncEmailService fire-and-forget pattern
  - Updated SubscriptionService webhook handlers to use async email sending
  - Updated SubscriptionController to use coroutines for suspend function calls
  - Updated IncomingJobsProcessingService to use async email sending
  - All email sending now uses fire-and-forget coroutines, preventing webhook timeouts
  - **Architecture Protection**: Made ResendEmailService internal within AsyncEmailService file
  - **Architecture Protection**: Made JobSearchScheduler internal within SubscriptionAwareSchedulingService file
  - All external code now uses proper facade services with subscription-aware logic
- [ ] **HIGH: Implement webhook event cleanup** - `SubscriptionSyncService.kt:50`
  - Clean up webhook events older than 30 days
  - Prevent database bloat
- [ ] **HIGH: Webhook Security Hardening**
  - Add rate limiting to Stripe webhook endpoint
  - Implement request size limits for webhook payloads
  - Add monitoring for suspicious webhook activity

### Data Model Issues  
- [ ] **LOW: Remove unused internal_id field** - `JobProcessingModels.kt:116` 
  - Evaluate if internal_id is needed in ProcessedJobData

### Testing & Quality Assurance
- [ ] **CRITICAL: Improve test coverage**
  - Only 2 test files out of 73 Kotlin source files (~2.7% coverage)
  - Add tests for Stripe integration, subscription logic, webhook handling
  - Add integration tests for email notifications

### Job Processing TODOs
- [ ] **MEDIUM: Create monthly job search summaries**
  - Generate monthly digest emails with job search statistics
  - Include search performance metrics and recommendations
  - Send alongside regular time-period based searches
- [ ] **MEDIUM: Display time period and location in job results emails**
  - Add search time period info to job notification email headers
  - Include location information in email templates
  - Help users understand which alert triggered the email
- [ ] **LOW: Implement X.com job posting** - `IncomingJobsProcessingService.kt:169`
  - Use X scraper to post jobs
- [ ] **LOW: Add support for non-email channels** - `IncomingJobsProcessingService.kt:243`
  - Handle telegram and other notification channels

### Error Handling & Resilience
- [ ] **MEDIUM: Add circuit breakers for external APIs**
  - Implement circuit breakers for DeepSeek, OpenRouter, Stripe API calls
  - Add retry logic for failed webhook processing
  - Implement fallback mechanisms for email delivery failures
- [ ] **MEDIUM: Environment configuration validation**
  - Validate required environment variables at startup
  - Implement graceful degradation when optional services unavailable

## 💰 Subscription System (Medium Priority)

### Payment Features  
- [x] **COMPLETED: Core Stripe integration infrastructure**
  - All domain models, repositories, services, and controllers implemented
  - Webhook handling with idempotency and error recovery
  - Email templates for all subscription lifecycle events
  - @RequiresPremium annotation created for access control

### Customer Portal Integration  
- [ ] **MEDIUM: Add customer portal links to email notifications**
  - Replace placeholder STRIPE_CUSTOMER_PORTAL_URL in email templates
  - Test customer portal access flow

### Subscription Analytics & Monitoring
- [ ] **MEDIUM: Add performance monitoring**
  - Implement application performance metrics collection
  - Add database query performance monitoring
  - Set up memory/CPU usage alerts
- [ ] **MEDIUM: Add business logic monitoring**
  - Monitor job processing pipeline health
  - Track failed email deliveries with alerts
  - Implement user engagement metrics tracking
- [ ] Add conversion rate tracking (free → premium)
- [ ] Add churn rate metrics (canceled subscriptions) 
- [ ] Add failed payment rate monitoring
- [ ] Add webhook processing failure alerts
- [ ] Create subscription dashboard/metrics endpoint
- [ ] Integrate with existing logging/monitoring system
- [ ] Add Sentry alerts for critical subscription events

**Implementation approach:**
```kotlin
@Service
class SubscriptionMetricsService {
    fun recordCheckoutStarted(userId: String)
    fun recordCheckoutCompleted(userId: String) 
    fun recordSubscriptionCanceled(userId: String)
    fun recordPaymentFailed(userId: String)
    fun getConversionRate(): Double
    fun getChurnRate(): Double
}
```

### Feature Usage Tracking
- [ ] Track which premium features are used most
- [ ] Measure feature adoption by subscription tier
- [ ] Track continuous monitoring usage
- [ ] Track email alert frequency by user
- [ ] Measure CV analysis depth usage
- [ ] Create usage reports for product decisions

**Implementation approach:**
```kotlin
@Service
class FeatureUsageTracker {
    fun trackFeatureUsage(userId: String, feature: PremiumFeature, metadata: Map<String, Any>)
    fun getUsageStats(feature: PremiumFeature, timeRange: TimeRange): FeatureUsageStats
    fun getUserUsageProfile(userId: String): UserUsageProfile
}

enum class PremiumFeature {
    CONTINUOUS_MONITORING,
    EMAIL_ALERTS, 
    DEEP_CV_ANALYSIS,
    PRIORITY_SUPPORT
}
```

## ⚠️ High Risk / Questionable Points

### Stripe Integration Risks
- [ ] **CRITICAL: Email notification suspension issue**
  - ResendEmailService is suspend function, webhook handlers are not suspend
  - Could cause webhook timeouts or failures
  - **Solution**: Create async email queue or non-suspend email service
  
- [ ] **HIGH: Webhook idempotency edge cases**
  - Currently checks existence by event ID only
  - What if event exists but processing failed?
  - **Solution**: Add retry logic for failed events

- [ ] **MEDIUM: Customer portal URL management**
  - Customer portal URLs are dynamic per customer
  - Currently using placeholder in emails
  - **Solution**: Store portal URL in config or generate dynamically

- [ ] **MEDIUM: Subscription status drift**
  - 6-hour sync interval might miss critical status changes
  - **Solution**: Reduce sync frequency or add real-time status checks

### Business Logic Risks
- [ ] **HIGH: Free user migration impact**
  - All existing users become FREE tier immediately
  - Potential user experience disruption
  - **Solution**: Communication strategy + gradual rollout

- [ ] **MEDIUM: Premium feature access during status transitions**
  - Race conditions between webhook processing and feature access checks
  - **Solution**: Add database-level constraints or locks

- [ ] **MEDIUM: Webhook endpoint security**
  - Only signature verification, no rate limiting
  - Potential for webhook spam/DoS
  - **Solution**: Add rate limiting to webhook endpoint

## 🔧 Completed Items

### ✅ Stripe Integration Foundation (COMPLETE)
- [x] Stripe dependency added to build.gradle.kts  
- [x] Stripe configuration in application.yml (secret-key, webhook-secret, customer-portal-url)
- [x] Domain models created (UserSubscription, SubscriptionStatus, etc.)
- [x] SubscriptionRepository with MongoDB indexes
- [x] StripeService with webhook signature verification
- [x] SubscriptionService with status management and premium access checking
- [x] SubscriptionController with status and webhook endpoints
- [x] SecurityConfig updated to allow webhook endpoint
- [x] Webhook event tracking (StripeWebhookEvent) for idempotency
- [x] Subscription sync service for webhook failure recovery
- [x] Proactive Stripe customer creation on destination creation
- [x] No grace period implementation (immediate access loss)
- [x] Complete email sending implementation in webhook handlers:
  - [x] Trial ending reminder email (handleTrialWillEnd → sendTrialEndingEmail)
  - [x] Payment failed notification email (handlePaymentFailed → sendPaymentFailedEmail)
  - [x] Welcome email on checkout completion (handleCheckoutCompleted → sendWelcomeEmail)
  - [x] Mobile-responsive email templates for all subscription lifecycle events
  - [x] Error handling and logging for email delivery
- [x] Subscription-aware job search scheduling system:
  - [x] `SubscriptionAwareSchedulingService` with free/premium time period logic
  - [x] Integration with `JobSearchScheduler` for startup, creation, and updates
  - [x] Subscription upgrade/downgrade handlers with automatic rescheduling
  - [x] Configuration setup to handle circular dependencies
  - [x] Free tier: Monthly scheduling override, Premium tier: Original time periods

### Development & Documentation
- [ ] **LOW: Development setup documentation**
  - Create comprehensive local development setup guide
  - Add troubleshooting guide for common development issues
  - Document environment variable requirements
- [ ] **LOW: API documentation improvements**
  - Add request/response examples to OpenAPI docs
  - Create integration testing examples for new developers
  - Document webhook testing procedures

## 🚀 Future Enhancements (Low Priority)
- [ ] Multiple subscription tiers (Basic, Pro, Enterprise)
- [ ] Annual billing discounts
- [ ] Referral program
- [ ] Student discounts
- [ ] Team/organization subscriptions
- [ ] Usage-based pricing options
- [ ] Geographic pricing adjustments
- [ ] Subscription pause/resume functionality
- [ ] Automated churn reduction emails
- [ ] Advanced subscription analytics dashboard
- [ ] A/B testing for pricing strategies
- [ ] Invoice customization and branding
- [ ] Multi-currency support
- [ ] Tax calculation for international users