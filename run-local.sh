#!/bin/bash

# Run the Spring Boot backend with specified profile (local or prod)
# Usage: ./run-local.sh [local|prod]
#
# Spring Boot will automatically load environment files based on profile:
#   - local profile → .env.local
#   - prod profile → .env.prod
# (configured via application.yml spring.config.import)

set -e  # Exit on error

# Get environment argument (default to 'local')
ENV=${1:-local}

# Validate environment argument
if [[ "$ENV" != "local" && "$ENV" != "prod" ]]; then
    echo "❌ Error: Invalid environment '$ENV'"
    echo "   Usage: ./run-local.sh [local|prod]"
    echo "   Example: ./run-local.sh local"
    exit 1
fi

echo "🚀 Starting job_search_cv_backend with $ENV profile..."

# Determine which .env file to check
ENV_FILE=".env.$ENV"

# Check if environment file exists
if [ -f "$ENV_FILE" ]; then
    echo "✅ Found $ENV_FILE - Spring Boot will load it automatically"
else
    echo "⚠️  Warning: $ENV_FILE file not found!"
    echo "   Create $ENV_FILE with your configuration variables:"
    echo "   - MONGO_USER, MONGO_PASSWORD, MONGO_HOST, MONGO_DB"
    echo "   - FIREBASE_CREDENTIALS_PATH (or individual Firebase vars)"
    echo "   - OPENROUTER_API_KEY (for AI features)"
    echo "   - RESEND_API_KEY, RESEND_FROM_EMAIL (for email)"
    echo "   - STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET (for payments)"
    echo "   - XCOM_API_URL, XCOM_API_KEY (for X.com integration)"
    echo ""
    echo "   Note: For multi-line values like FIREBASE_PRIVATE_KEY,"
    echo "   it's easier to use FIREBASE_CREDENTIALS_PATH instead."
    echo ""
fi

# Set Spring profile
export SPRING_PROFILES_ACTIVE=$ENV

# Set environment variable overrides (applied to all environments)
export SENTRY_DSN=""
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://localhost:4200}"
export WEBSITE_URL="${WEBSITE_URL:-http://localhost:3000}"
export SUPPORT_EMAIL="${SUPPORT_EMAIL:-support@applyfirst.app}"
export FIREBASE_ENABLED="${FIREBASE_ENABLED:-true}"

# Run the application
echo "🏃 Running ./gradlew bootRun with profile: $ENV"
echo ""

./gradlew bootRun --args="--spring.profiles.active=$ENV"
