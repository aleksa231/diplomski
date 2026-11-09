#!/usr/bin/env bash
#
# Restores the canonical benchmarking dataset, run once before every experiment.
#
# Accepts an optional DATASET_SIZE argument (default: 5000).
#
set -euo pipefail

DATASET_SIZE="${1:-5000}"
CONTAINER="${DATA_GENERATOR_CONTAINER:-data-generator}"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "reset-dataset: $CONTAINER does not exist; run 'docker compose up -d' first" >&2
    exit 1
fi

echo "reset-dataset: restoring the canonical dataset (size=$DATASET_SIZE) via $CONTAINER"

IMAGE=$(docker inspect "$CONTAINER" --format '{{.Config.Image}}')

docker rm -f "$CONTAINER" >/dev/null 2>&1 || true

docker run --rm --name "$CONTAINER" \
  --network projekatdiplomski_default \
  -e "DATASET_SIZE=$DATASET_SIZE" \
  "$IMAGE"

echo "reset-dataset: canonical dataset restored (size=$DATASET_SIZE)"
