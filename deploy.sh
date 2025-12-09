#!/bin/bash
set -e

ENV=prod

export ENV
export DOCKER_BUILDKIT=1

# Support --no-cache flag to force clean build (bypasses all Docker caching)
# Usage: ./deploy.sh --no-cache
NO_CACHE_FLAG=""
if [ "$1" == "--no-cache" ]; then
    NO_CACHE_FLAG="--no-cache"
    echo "⚠️  Building with --no-cache (clean build, slower but ensures fresh build)"
fi

echo "Building and pushing job-search-cv-backend from monorepo root..."

# Build with cache from the latest image
docker compose build --build-arg BUILDKIT_INLINE_CACHE=1 $NO_CACHE_FLAG job-search-cv-backend

# Push the image to the registry
docker compose push job-search-cv-backend

echo "$ENV job-search-cv-backend build and push completed successfully."
