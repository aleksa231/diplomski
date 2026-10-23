# NoSQL vs Relational in Spring Boot: Empirical Benchmark Platform

An empirical benchmarking system comparing NoSQL (**MongoDB 7.0 + Redis 7.2**) in blocking and reactive paradigms against a Relational reference (**PostgreSQL 16**) in normalized EAV and JSONB variants. All services adhere to a single REST contract ([contract/openapi.yaml](contract/openapi.yaml)).

---

## 1. Quick Start (One Command)

From a clean checkout:

```bash
docker compose up --build
```

Everything starts automatically:
1. 3-node MongoDB replica set (`mongo1`, `mongo2`, `mongo3`) initialized via `mongo-init` into `rs0`.
2. Redis 7.2 and PostgreSQL 16.
3. API services (`nosql-blocking`, `nosql-reactive`, `relational`), `stub-service`, and `experiment-runner`.
4. Automated database seeding (10,000 products deterministic dataset).
5. Observability stack (Prometheus, Grafana, exporters, cAdvisor).
6. React Console served on Port 3000 via Nginx.

To stop and remove volumes:
```bash
docker compose down -v
# or
make down
```

---

## 2. Port Table

| Service | Port | Description |
| :--- | :--- | :--- |
| **frontend** | `3000` | React benchmark console & reverse proxy (no CORS) |
| **grafana** | `3001` | Pre-configured dashboards (user: `admin`, pass: `admin`) |
| **prometheus** | `9090` | Prometheus metrics scrape server |
| **nosql-blocking** | `8081` | Spring MVC + MongoDB 7 + Redis 7 |
| **nosql-reactive** | `8082` | Spring WebFlux + Reactive MongoDB + Reactive Redis |
| **relational** | `8083` | Spring MVC + JPA + PostgreSQL 16 (+ Redis cache) |
| **experiment-runner** | `8090` | REST test runner, k6 orchestrator, JSON exporter |
| **stub-service** | `8099` | Downstream service with configurable latency for S5 |
| **cadvisor** | `8085` | Container metrics exporter |
| **mongodb (rs0)** | `27017` / `27018` / `27019` | Replica set primary and secondary nodes |
| **redis** | `6379` | In-memory key-value cache and cart store |
| **postgres** | `5432` | Relational database (normalized and JSONB schemas) |

---

## 3. Container Resource Limits & Hardware

To guarantee benchmark honesty, all equivalent services and database tiers are assigned identical compute quotas via Docker Compose resource limits:

| Category | CPU Limit | Memory Limit | JVM Heap Settings |
| :--- | :--- | :--- | :--- |
| **Databases** (`mongo1-3`, `postgres`, `redis`) | 2.0 vCPU | 2048 MB | N/A |
| **API Services** (`nosql-blocking`, `nosql-reactive`, `relational`) | 2.0 vCPU | 1024 MB | `-Xms512m -Xmx768m -XX:+UseZGC -XX:+ZGenerational` |
| **Support Services** (`stub-service`, `experiment-runner`) | 1.0–2.0 vCPU | 512–1024 MB | `-Xms256m -Xmx512m` |
| **Frontend & Exporters** | 0.5–1.0 vCPU | 256–512 MB | N/A |

### Host Hardware Baseline
- **OS**: Windows 10/11 x64 (WSL2 Docker Engine)
- **CPUs**: 8+ physical cores
- **RAM**: 16 GB+
- **Disk**: NVMe SSD

---

## 4. Running Experiments

### Via the React Console
1. Open `http://localhost:3000` in your browser.
2. Navigate to **Experiment Console**.
3. Select Scenario (`S1` through `S7`), dataset size, client concurrency, duration, and target backends.
4. Click **Start Run**. Real-time logs and progress are streamed live.
5. Inspect results in the **Results** page with side-by-side charts, p95 latency, throughput, and JSON export.

### Via Make Targets (Direct CLI)
```bash
make scenario-S1   # S1: Schema flexibility & document read/filter
make scenario-S2   # S2: Caching, cart latency & popularity ranking
make scenario-S3   # S3: Write throughput (bulk insert & order creation)
make scenario-S4   # S4: Indexing & aggregation query plans
make scenario-S5   # S5: Reactive WebFlux vs Blocking MVC with slow downstream
make scenario-S6   # S6: Replica failover, Redis outage & concurrency correctness
make scenario-S7   # S7: Relational joins vs Document aggregation (S7 relational win)
```

### Direct k6 Execution
```bash
k6 run -e TARGET_URL=http://localhost:8081 loadtests/s1/catalog-read.js
```

---

## 5. Sharded Profile & Failure Injection

### Sharded MongoDB Cluster
To launch the sharded topology (2 config servers, 2 shard replica sets, and a `mongos` router):
```bash
docker compose -f docker-compose.yml -f docker-compose.sharded.yml up -d --build
```
This enables sharding on `benchmark.products` using a hashed shard key on `_id`.

### Failure Scripts
- **Kill MongoDB Primary**:
  ```bash
  bash scripts/kill-mongo-primary.sh
  ```
  Finds the active primary in replica set `rs0` and pauses/stops it, measuring election time and recovery under load.
- **Toggle Redis Under Load**:
  ```bash
  bash scripts/toggle-redis.sh
  ```
  Stops Redis and verifies that catalog reads fall back gracefully while cart returns 503 fast without thread starvation.

---

## 6. Assumptions & Known Limitations

1. **Local Docker Environment**: Single-host Docker Compose shares CPU and disk I/O. Container limits enforce isolation, but absolute latency numbers will reflect local machine capabilities rather than dedicated cloud clusters.
2. **Docker Socket Mount**: The `experiment-runner` container mounts `/var/run/docker.sock` to orchestrate failure injection scenarios (`kill-mongo-primary.sh`, `toggle-redis.sh`) for the automated demo. This is strictly intended for local benchmarking demonstrations.
3. **No Auth / Users**: By design, authentication, user sessions, and payment gateways are excluded to isolate database and architectural performance.
4. **Relational Schema**: Both normalized (EAV with explicit foreign keys) and JSONB schemas exist within the same PostgreSQL database, switchable via runtime configuration `app.relational.variant`.
