#!/usr/bin/env bash
#
# S6d - take Redis down while the load is running. Catalog reads must then fall back to the
# database (with a warning and a fallback metric) and cart operations must fail fast with 503
# instead of hanging.
set -euo pipefail

CONTAINER="${REDIS_CONTAINER:-redis}"

echo "stop-redis: stopping $CONTAINER"
docker stop "$CONTAINER" >/dev/null
echo "stop-redis: $CONTAINER stopped"
