#!/bin/bash

# Run the Spring Boot backend with local profile
# This script sets up common local environment variables and runs the application
#
# Spring Boot will automatically load .env.local via application.yml:
#   spring.config.import: optional:file:.env.local[.properties]

set -e  # Exit on error

echo "🚀 Starting job_search_cv_backend with local profile..."

# Check if .env.local exists
if [ -f .env.local ]; then
    echo "✅ Found .env.local - Spring Boot will load it automatically"
else
    echo "⚠️  Warning: .env.local file not found!"
    echo "   Create .env.local with your configuration variables:"
    echo "   - MONGO_USER, MONGO_PASSWORD, MONGO_HOST, MONGO_DB"
    echo "   - FIREBASE_CREDENTIALS_PATH (or individual Firebase vars)"
    echo "   - OPENROUTER_API_KEY (for AI features)"
    echo "   - RESEND_API_KEY, RESEND_FROM_EMAIL (for email)"
    echo "   - STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET (for payments)"
    echo ""
    echo "   Note: For multi-line values like FIREBASE_PRIVATE_KEY,"
    echo "   it's easier to use FIREBASE_CREDENTIALS_PATH instead."
    echo ""
fi

# Set environment variables that override .env.local defaults for local dev
export SPRING_PROFILES_ACTIVE=local
export SENTRY_DSN=""
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://localhost:4200}"
export WEBSITE_URL="${WEBSITE_URL:-http://localhost:3000}"
export SUPPORT_EMAIL="${SUPPORT_EMAIL:-support@applyfirst.app}"
export FIREBASE_ENABLED="${FIREBASE_ENABLED:-true}"

# Run the application
echo "🏃 Running ./gradlew bootRun..."
echo ""

./gradlew bootRun --args='--spring.profiles.active=local'
