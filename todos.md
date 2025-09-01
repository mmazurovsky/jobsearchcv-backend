# Backend TODOs

## 🔥 High Priority (Critical Issues & Security)

### Stripe Payment Integration (HIGH RISK)
- [ ] **CRITICAL: Stripe Integration Testing**
  - Comprehensive testing of webhook endpoints and payment flows
  - Test subscription management and status transitions
  - Verify email notifications work correctly
  - Test webhook signature verification and idempotency
- [ ] **CRITICAL: Fix email notification architecture** - `SubscriptionService.kt:225,237`
  - Current runBlocking in webhook context can cause timeouts
  - Implement proper async email queue instead of runBlocking
  - Ensure webhook processing stays under 10 second Stripe timeout
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
- [ ] **HIGH: Missing Dockerfile**
  - docker-compose.yml references Dockerfile but none exists
  - Required for deployment and production setup

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
- [ ] **MEDIUM: Add subscription tier restrictions to existing services**
  - Update `JobSearchCreationService` for premium-only continuous monitoring
  - Update `CVProcessingService` to limit analysis depth for free users
  - Update `IncomingJobsProcessingService` for tier-based filtering
  - Update `ResendEmailService` to limit email frequency for free users

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
- [x] Stripe dependency added to build.gradle.kts
- [x] Domain models created (UserSubscription, SubscriptionStatus, etc.)
- [x] SubscriptionRepository with MongoDB indexes
- [x] StripeService with webhook signature verification
- [x] SubscriptionService with status management
- [x] SubscriptionController with status and webhook endpoints
- [x] SecurityConfig updated for webhook endpoint
- [x] Webhook event tracking for idempotency
- [x] Subscription sync service for webhook failure recovery
- [x] Proactive Stripe customer creation on destination creation
- [x] No grace period implementation (immediate access loss)

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