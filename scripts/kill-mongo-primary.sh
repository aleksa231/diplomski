#!/usr/bin/env bash
#
# S6c - stop the current MongoDB primary of the benchmark replica set.
#
# Runs inside the experiment-runner container, which has /var/run/docker.sock mounted and the
# docker CLI installed (see experiment-runner/Dockerfile). mongosh is not present in the runner,
# so the primary is detected by executing mongosh inside the mongo containers themselves.
#
# Override the candidate list with MONGO_NODES="/mongo1 mongo2 mongo3" when using a different
# topology. The matching start-mongo-nodes.sh script brings the stopped node back after the run.
set -uo pipefail

NODES="${MONGO_NODES:-mongo1 mongo2 mongo3}"

primary=""
for node in $NODES; do
    running=$(docker inspect -f '{{.State.Running}}' "$node" 2>/dev/null || echo "false")
    if [ "$running" != "true" ]; then
        continue
    fi
    writable=$(docker exec "$node" mongosh --quiet --eval 'db.hello().isWritablePrimary' 2>/dev/null | tr -d '[:space:]')
    if [ "$writable" = "true" ]; then
        primary="$node"
        break
    fi
done

if [ -z "$primary" ]; then
    echo "kill-mongo-primary: nema upisivog primarnog čvora među: $NODES" >&2
    exit 1
fi

echo "kill-mongo-primary: zaustavljam primarni čvor $primary"
docker stop "$primary" >/dev/null
echo "kill-mongo-primary: $primary zaustavljen; preostali čvorovi će izabrati novi primarni"
