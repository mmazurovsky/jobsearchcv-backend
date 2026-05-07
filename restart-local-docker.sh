#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "Stopping containers..."
docker compose -f docker-compose.local.yml down

echo "Starting containers..."
docker compose -f docker-compose.local.yml up -d --build

echo ""
echo "Container status:"
docker compose -f docker-compose.local.yml ps
