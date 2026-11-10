#!/usr/bin/env bash
#
# S6c recovery - bring back every MongoDB node that kill-mongo-primary.sh stopped and wait until
# the replica set has a writable primary again. Configured as the failover target's recover-script
# so that a repetition always starts from a healthy three-node replica set.
set -uo pipefail

NODES="${MONGO_NODES:-mongo1 mongo2 mongo3}"

for node in $NODES; do
    state=$(docker inspect -f '{{.State.Running}}' "$node" 2>/dev/null || echo "missing")
    if [ "$state" = "true" ]; then
        continue
    fi
    if [ "$state" = "missing" ]; then
        echo "start-mongo-nodes: kontejner $node ne postoji, preskačem" >&2
        continue
    fi
    echo "start-mongo-nodes: pokrećem $node"
    docker start "$node" >/dev/null
done

# A restarted member has to rejoin and catch up before it can be elected, so poll for the primary.
for attempt in $(seq 1 60); do
    for node in $NODES; do
        writable=$(docker exec "$node" mongosh --quiet --eval 'db.hello().isWritablePrimary' 2>/dev/null | tr -d '[:space:]')
        if [ "$writable" = "true" ]; then
            echo "start-mongo-nodes: replica set ponovo ima primarni čvor ($node)"
            exit 0
        fi
    done
    sleep 2
done

echo "start-mongo-nodes: nema upisivog primarnog čvora ni nakon 120s" >&2
exit 1
