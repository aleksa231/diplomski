#!/usr/bin/env bash
#
# Start Redis again and wait until it answers PING, so the next use starts with a warm container.
set -euo pipefail

CONTAINER="${REDIS_CONTAINER:-redis}"

echo "start-redis: pokrećem $CONTAINER"
docker start "$CONTAINER" >/dev/null

for attempt in $(seq 1 30); do
    if docker exec "$CONTAINER" redis-cli ping 2>/dev/null | grep -q PONG; then
        echo "start-redis: $CONTAINER ponovo odgovara na PING"
        exit 0
    fi
    sleep 1
done

echo "start-redis: $CONTAINER nije postao spreman na vrijeme" >&2
exit 1
