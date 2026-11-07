#!/usr/bin/env bash
#
# S6d recovery - start Redis again and wait until it answers PING, so the next repetition measures
# a warm recovery rather than a still-starting container. Configured as the redis-down target's
# recover-script.
set -euo pipefail

CONTAINER="${REDIS_CONTAINER:-redis}"

echo "start-redis: starting $CONTAINER"
docker start "$CONTAINER" >/dev/null

for attempt in $(seq 1 30); do
    if docker exec "$CONTAINER" redis-cli ping 2>/dev/null | grep -q PONG; then
        echo "start-redis: $CONTAINER is answering PING again"
        exit 0
    fi
    sleep 1
done

echo "start-redis: $CONTAINER did not become ready in time" >&2
exit 1
