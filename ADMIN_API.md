# Admin API Documentation

This document describes the admin-only endpoints for managing user premium subscriptions without requiring Stripe payments.

## Authentication

All admin endpoints require the `X-Admin-Secret` header with the admin secret configured in environment variables:
- **Local**: `ADMIN_SECRET=admin_secret_dev_key_2024`
- **Production**: `ADMIN_SECRET=admin_prod_secure_key_2024_xyz`

## Endpoints

### 1. Activate Premium Subscription
**POST** `/api/admin/subscriptions/{userId}/activate`

Grant premium access to a user for a specified duration.

**Headers:**
- `X-Admin-Secret`: Admin secret key

**Parameters:**
- `userId` (path): Target user ID
- `durationDays` (query, optional): Duration in days (default: 365)

**Example:**
```bash
curl -X POST "http://localhost:8080/api/admin/subscriptions/user123/activate?durationDays=30" \
  -H "X-Admin-Secret: admin_secret_dev_key_2024"
```

**Response:** `SubscriptionStatusResponse` with updated subscription status

### 2. Revoke Premium Subscription
**POST** `/api/admin/subscriptions/{userId}/revoke`

Remove premium access from a user immediately.

**Headers:**
- `X-Admin-Secret`: Admin secret key

**Parameters:**
- `userId` (path): Target user ID

**Example:**
```bash
curl -X POST "http://localhost:8080/api/admin/subscriptions/user123/revoke" \
  -H "X-Admin-Secret: admin_secret_dev_key_2024"
```

**Response:** `SubscriptionStatusResponse` with updated subscription status

### 3. Check User Subscription Status
**GET** `/api/admin/subscriptions/{userId}/status`

Get subscription status for any user (admin version of regular status endpoint).

**Headers:**
- `X-Admin-Secret`: Admin secret key

**Parameters:**
- `userId` (path): Target user ID

**Example:**
```bash
curl -X GET "http://localhost:8080/api/admin/subscriptions/user123/status" \
  -H "X-Admin-Secret: admin_secret_dev_key_2024"
```

**Response:** `SubscriptionStatusResponse` with current subscription status

## How It Works

### Business Logic Integration
- **No Business Logic Changes**: Uses existing `SubscriptionService.checkPremiumAccess()` method
- **Same Authorization**: `@RequiresPremium` annotation works normally
- **Automatic Scheduling**: Triggers `handleSubscriptionUpgrade()/handleSubscriptionDowngrade()` for job search rescheduling

### Database Records
Admin-activated premium subscriptions create `UserSubscription` records with:
- `tier: PREMIUM`
- `status: ACTIVE` 
- `currentPeriodEnd: now + durationDays`
- `stripeCustomerId: null` (no Stripe involvement)
- `stripeSubscriptionId: null`

### Audit Logging
All admin operations are logged to the `admin_audit_logs` collection with:
- Action type (ACTIVATE_PREMIUM, REVOKE_PREMIUM, CHECK_STATUS)
- Target user ID
- Operation details
- Success/failure status
- Timestamp

## Security Notes

1. **Secret Management**: Admin secrets should be stored securely and rotated regularly
2. **Access Control**: Only trusted administrators should have access to these secrets
3. **Audit Trail**: All admin operations are logged for compliance and debugging
4. **Error Handling**: Invalid secrets return 403 Forbidden status

## Troubleshooting

### Common Issues:
1. **403 Forbidden**: Check admin secret in request header
2. **User Not Found**: User ID may not exist in the system
3. **No Effect**: Check if user already has premium access

### Logs:
- Admin operations: Look for `AdminController` logs
- Audit trail: Check `admin_audit_logs` MongoDB collection
- Business logic: Regular `SubscriptionService` logs still apply