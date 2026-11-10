#!/usr/bin/env bash
# Vraća kanonski benchmark dataset; pokreće se jednom prije svakog eksperimenta.
# Prima opcionu veličinu dataseta kao prvi argument (podrazumijevano 5000).
set -euo pipefail

DATASET_SIZE="${1:-5000}"
CONTAINER="${DATA_GENERATOR_CONTAINER:-data-generator}"
IMAGE="${DATA_GENERATOR_IMAGE:-projekatdiplomski-data-generator}"
NETWORK="${DATA_GENERATOR_NETWORK:-projekatdiplomski_default}"

# Ako kontejner iz docker compose-a još postoji, preuzmi iz njega tačnu sliku i mrežu;
# inače koristi podrazumijevane vrijednosti (kontejner se uklanja poslije svakog reseta).
if docker inspect "$CONTAINER" >/dev/null 2>&1; then
    IMAGE=$(docker inspect "$CONTAINER" --format '{{.Config.Image}}')
    NETWORK=$(docker inspect "$CONTAINER" --format '{{.HostConfig.NetworkMode}}')
fi

echo "reset-dataset: vraćam kanonski dataset (veličina=$DATASET_SIZE) preko $CONTAINER ($IMAGE)"

docker rm -f "$CONTAINER" >/dev/null 2>&1 || true

docker run --rm --name "$CONTAINER" \
  --network "$NETWORK" \
  -e "DATASET_SIZE=$DATASET_SIZE" \
  -e "SPRING_DATA_MONGODB_URI=mongodb://mongo1:27017,mongo2:27017,mongo3:27017/benchmark?replicaSet=rs0" \
  -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/benchmark" \
  -e "SPRING_DATASOURCE_USERNAME=benchmark" \
  -e "SPRING_DATASOURCE_PASSWORD=benchmark" \
  "$IMAGE"

echo "reset-dataset: kanonski dataset vraćen (veličina=$DATASET_SIZE)"
