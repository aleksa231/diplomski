#!/usr/bin/env bash
#
# Stop the Redis container for local maintenance. Catalog reads then fall back to the database
# (with a warning and a fallback metric) and cart operations fail fast with 503.
set -euo pipefail

CONTAINER="${REDIS_CONTAINER:-redis}"

echo "stop-redis: zaustavljam $CONTAINER"
docker stop "$CONTAINER" >/dev/null
echo "stop-redis: $CONTAINER zaustavljen"
