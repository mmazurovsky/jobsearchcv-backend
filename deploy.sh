#!/bin/bash
set -e

ENV=prod

export ENV
export DOCKER_BUILDKIT=1

echo "Building and pushing job-search-cv-backend from monorepo root..."

# Build with cache from the latest image
docker compose build --build-arg BUILDKIT_INLINE_CACHE=1 job-search-cv-backend

# Push the image to the registry
docker compose push job-search-cv-backend

echo "$ENV job-search-cv-backend build and push completed successfully."
