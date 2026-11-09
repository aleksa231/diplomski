# KlikMarket — uporedno ispitivanje NoSQL i relacionih baza (Spring Boot)

## Kako pokrenuti (brzi vodi?)

### Preduslovi
- **Docker Desktop** (sa Docker Compose) ? [preuzmi](https://www.docker.com/products/docker-desktop/)
- **Git** ? [preuzmi](https://git-scm.com/)
- Minimum: 8 GB RAM, 4 CPU jezgra, 20 GB slobodnog prostora

### Koraci

1. **Kloniraj repozitorijum:**
   ```bash
   git clone https://github.com/aleksa231/diplomski.git
   cd diplomski
   ```

2. **Pokreni sve servise:**
   ```bash
   docker compose up --build
   ```
   Ovo automatski:
   - Pokre?e MongoDB replica set (3 ?vora), Redis, PostgreSQL
   - Pokre?e backend servise (nosql-blocking, nosql-reactive, relational)
   - Pokre?e experiment-runner i stub-service
   - Popunjava bazu sa 5.000 proizvoda i 10.000 narud?bi (deterministi?ki)
   - Pokre?e Prometheus, Grafana i cAdvisor
   - Pokre?e frontend (KlikMarket) na portu 3000

3. **Otvori aplikaciju:**
   - **KlikMarket (prodavnica):** http://localhost:3000
   - **Grafana (monitoring):** http://localhost:3001 (admin/admin)
   - **Prometheus:** http://localhost:9090
   - **Eksperimentalna konzola:** http://localhost:3000/#/admin

4. **Zaustavi:**
   ```bash
   docker compose down -v
   ```

### Funkcionalnosti aplikacije
- **KlikMarket** ? online prodavnica sa katalogom, korpa, narud?be, recenzije
- **Eksperimentalna konzola** ? pokretanje i uporedba benchmark scenarija (S1?S7)
- **Istorija transakcija** ? pregled svih narud?bi sa filterima i paginacijom
- **Light/Dark mode** ? dugme u navigaciji za prebacivanje tema
- **Srpski (ijekavski)** interfejs, tamna slate tema

### Napomene
- Prvo pokretanje mo?e trajati 5?10 minuta (build Docker image-ova).
- Dataset se resetuje na svaki run eksperimenta (o?ekivano pona?anje).
- Za lokalni razvoj frontenda: `cd frontend && npm install && npm run dev`

---


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
4. Automated database seeding (deterministic dataset: 5,000 products, 10,000 orders; configurable via `DATASET_SIZE`).
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
make scenario-S5   # S5: Reactive WebFlux vs Blocking MVC with slow downstream
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
The `scripts/` directory holds the failure-injection helpers that the `experiment-runner` fires
mid-run for S6. They drive the Docker socket that is mounted into the runner, and every one of them
can also be run by hand from the host or from inside the runner container:

- **MongoDB primary failover** (S6c):
  ```bash
  bash scripts/kill-mongo-primary.sh    # finds the current primary in rs0 and stops that node
  bash scripts/start-mongo-nodes.sh     # starts stopped members again and waits for a writable primary
  ```
  The candidate nodes default to `mongo1 mongo2 mongo3` and can be overridden with `MONGO_NODES`.
- **Redis outage** (S6d):
  ```bash
  bash scripts/stop-redis.sh            # stops the Redis container
  bash scripts/start-redis.sh           # starts it again and waits for PING to answer
  ```
  The target container defaults to `redis` and can be overridden with `REDIS_CONTAINER`.
  While Redis is down, catalog reads must fall back to the database and cart operations must fail
  fast with 503 (no thread starvation); `start-redis.sh` documents the recovery time.

The `failover` and `redis-down` targets in
[experiment-runner/src/main/resources/application.yml](experiment-runner/src/main/resources/application.yml)
wire the injection and recovery scripts to the corresponding run.

---

## 6. Assumptions & Known Limitations

1. **Local Docker Environment**: Single-host Docker Compose shares CPU and disk I/O. Container limits enforce isolation, but absolute latency numbers will reflect local machine capabilities rather than dedicated cloud clusters.
2. **Docker Socket Mount**: The `experiment-runner` container mounts `/var/run/docker.sock` to orchestrate failure injection scenarios (`kill-mongo-primary.sh`, `start-mongo-nodes.sh`, `stop-redis.sh`, `start-redis.sh`) for the automated demo. This is strictly intended for local benchmarking demonstrations.
3. **No Auth / Users**: By design, authentication, user sessions, and payment gateways are excluded to isolate database and architectural performance.
4. **Relational Schema**: Both normalized (EAV with explicit foreign keys) and JSONB schemas exist within the same PostgreSQL database, switchable via runtime configuration `app.relational.variant`.
