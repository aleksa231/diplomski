#!/usr/bin/env bash
#
# Restores the canonical benchmarking dataset, run once before every experiment.
#
# Every scenario has to start from the same data or the comparison between targets is meaningless,
# and the write-heavy scenarios grow the catalog far beyond the seeded 5000 products (S3 bulk-inserts
# products at several hundred per second). MongoDB has no secondary index on the catalog - only
# `_id` - so a larger collection turns every S1 filtered query into a full collection scan with an
# in-memory sort, which saturates mongod and pushes request latency past the k6 timeouts. The same
# applies to the relational targets: their tables accumulate the S3 inserts.
#
# `data-generator` is the one-shot seeder created by `docker compose up`; it seeds products/orders
# into MongoDB and both PostgreSQL variants, and resets (drops/recreates) by default. Re-running the
# same container therefore restores exactly the canonical dataset. It is invoked from the runner
# container through the mounted Docker socket, like the S6 failure-injection scripts.
set -euo pipefail

CONTAINER="${DATA_GENERATOR_CONTAINER:-data-generator}"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "reset-dataset: $CONTAINER does not exist; run 'docker compose up -d' first" >&2
    exit 1
fi

echo "reset-dataset: restoring the canonical dataset via $CONTAINER"
docker start -a "$CONTAINER"
echo "reset-dataset: canonical dataset restored"
