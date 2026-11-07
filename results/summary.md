# Benchmark results - consolidated

Auto-generated from the newest result file per scenario in `results/`. One row per scenario/target;
the aggregate values are the mean over the three repetitions (standard deviation in parentheses).
The raw per-repetition samples are in `summary.csv`.

> `errorRate` is k6's `http_req_failed`, i.e. the fraction of non-2xx HTTP responses. HTTP 409 counts
> as *failed* in k6, so for the scenarios that deliberately provoke stock conflicts (S3, S6) the rate
> is dominated by *expected* conflicts rather than faults.

## Run configurations

| Scenario | Title | Status | Clients | Duration (s) | Warm-up (s) | Reps | Dataset | Targets | Source file |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | Schema flexibility and the document model | COMPLETED | 50 | 30 | 30 | 3 | 5000 | 3 | s1-20261026-233718-96f67d00.json |
| S2 | Caching and low latency | COMPLETED | 50 | 30 | 30 | 3 | 5000 | 2 | s2-20261026-234233-1281803c.json |
| S3 | Write throughput under load | COMPLETED | 100 | 30 | 30 | 3 | 5000 | 3 | s3-20261027-100852-0f9637ef.json |
| S4 | Indexing and aggregations | COMPLETED | 20 | 30 | 30 | 3 | 5000 | 4 | s4-20261105-231941-f2cc7986.json (replaces invalidated `s4-20261027-111326-839e74e9.json`; see notes) |
| S4B | Index isolation (no join fanout) | COMPLETED | 20 | 30 | 10 | 3 | 5000 | 4 | s4b-20261106-122835-dcb29f10.json (`dcb29f10-47b0-4331-aab2-ce3c1a061dd4`, refined window) |
| S5 | Reactive vs blocking | COMPLETED | 2000 | 60 | 30 | 3 | 5000 | 2 | s5-20261027-091910-ca7acff2.json |
| S6 | Horizontal scaling and resilience | COMPLETED | 50 | 30 | 30 | 3 | 5000 | 3 | s6-20261027-111817-4d9b6391.json |
| S7 | Where the relational model wins | COMPLETED | 20 | 30 | 30 | 3 | 5000 | 2 | s7-20261027-092428-356c9566.json |

## Aggregated results (mean over repetitions)

| Scenario | Target | Resolved | Variant | WC | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Error % | CPU | Mem (MB) | k6 exit |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | nosql-blocking | nosql-blocking | - | - | 320.9 (67.3) | 25.0 | 88.5 | 136.2 | 0.0 (0.0) | 0.939 | 367.7 | 0 |
| S1 | relational-normalized | relational | normalized | - | 81.7 (16.5) | 360.3 | 1158.4 | 1469.0 | 0.0 (0.0) | 0.969 | 412.9 | 99 |
| S1 | relational-jsonb | relational-jsonb | jsonb | - | 117.8 (18.5) | 210.7 | 779.8 | 968.4 | 0.0 (0.0) | 0.973 | 576.5 | 0/99 |
| S2 | nosql-blocking | nosql-blocking | - | - | 1419.7 (66.8) | 6.0 | 71.0 | 94.4 | 0.0 (0.0) | 0.936 | 407.2 | 0 |
| S2 | relational | relational | - | - | 315.9 (38.1) | 113.7 | 253.8 | 326.5 | 0.0 (0.0) | 0.970 | 442.7 | 0 |
| S3 | mongo-w1 | nosql-blocking-w1 | - | w1 | 240.5 (4.5) | 202.9 | 804.3 | 1076.7 | 0.311 (0.139) | 0.223 | 402.3 | 99 |
| S3 | mongo-majority | nosql-blocking | - | majority | 709.9 (77.7) | 28.6 | 239.4 | 634.9 | 2.651 (1.251) | 0.607 | 407.8 | 0 |
| S3 | postgres | relational | - | - | 227.8 (6.1) | 347.8 | 581.0 | 745.8 | 0.0 (0.0) | 0.789 | 458.9 | 0 |
| S4 | mongo-indexed | nosql-blocking | - | - | 0.9 (0.2) | 18505.4 | 30000.8 | 30002.3 | 34.17 (13.68) | 0.027 | 189.6 | 99 |
| S4 | mongo-unindexed | nosql-blocking | - | - | 0.9 (0.1) | 28236.6 | 29999.7 | 30004.0 | 32.10 (10.49) | 0.027 | 191.9 | 99 |
| S4 | postgres-indexed | relational | - | - | 33.9 (1.8) | 496.2 | 851.4 | 2106.7 | 0.0 (0.0) | 0.235 | 412.3 | 0 |
| S4 | postgres-unindexed | relational | - | - | 33.7 (0.3) | 499.1 | 813.7 | 1935.9 | 0.0 (0.0) | 0.185 | 368.8 | 0 |
| S4B | mongo-indexed | nosql-blocking | - | - | 537.8 (16.9) | 2.8 | 5.4 | 27.0 | 0.0 (0.0) | 0.481 | 412.6 | 0 |
| S4B | mongo-unindexed | nosql-blocking | - | - | 378.1 (5.0) | 6.4 | 90.4 | 95.6 | 0.0 (0.0) | 0.212 | 394.4 | 0 |
| S4B | postgres-indexed | relational | - | - | 141.0 (36.8) | 57.5 | 359.5 | 428.7 | 0.0 (0.0) | 0.987 | 430.0 | 0 |
| S4B | postgres-unindexed | relational | - | - | 281.5 (19.0) | 5.3 | 102.5 | 152.1 | 0.0 (0.0) | 0.790 | 442.2 | 0 |
| S5 | nosql-blocking | nosql-blocking | - | - | 1061.2 (9.7) | 1664.1 | 2725.6 | 2790.3 | 0.0 (0.0) | 0.649 | 460.0 | 0 |
| S5 | nosql-reactive | nosql-reactive | - | - | 863.2 (42.6) | 1495.7 | 4274.9 | 6079.7 | 0.0 (0.0) | 0.880 | 539.7 | 99 |
| S6 | failover | failover | - | - | 443.7 (0.6) | 1.5 | 2.3 | 3.5 | 0.0 (0.0) | 0.248 | 390.5 | 0 |
| S6 | redis-down | redis-down | - | - | 142.7 (19.2) | 1.6 | 2002.1 | 4007.7 | 1.577 (0.233) | 0.122 | 307.7 | 0 |
| S6 | concurrency | concurrency | - | - | 992.2 (571.7) | 5.4 | 142.2 | 168.4 | 49.010 (0.0) | 0.484 | 308.6 | 0 |
| S7 | nosql-blocking | nosql-blocking | - | - | 1.5 (0.0) | 103.0 | 38263.9 | 38328.6 | 0.0 (0.0) | 0.089 | 387.1 | 99 |
| S7 | relational | relational | - | - | 7.4 (0.2) | 1929.6 | 4731.3 | 5807.1 | 0.0 (0.0) | 0.029 | 417.8 | 0 |

## Notes on the non-zero error rates and thresholds

* **S3** - every non-2xx response is a 409 raised by the stock check when two concurrent orders target
  the last unit of the same product. `order_unexpected_errors` and `cart_errors` are both zero (verified
  directly from the k6 summary) and `bulk_insert_ok` is 100%, so no write is actually lost.
* **S6 (concurrency)** - the *last item* script fires 100 simultaneous orders at a product with stock = 1,
  so exactly one 201 and 99 x 409 are the *expected* outcome (99 of 202 requests = 49.01%). The invariant
  still holds: `order_created` = 1, `order_rejected` = 99, `stock_after` = 0.
* **S5 - reaktivno naspram blokiraju?eg (obja?njenje ?kontradikcije?, 2000 klijenata, 60 s, 3 repeticije)** - o?ekivanje da reaktivni (WebFlux/non-blocking) stack podnese vi?e optere?enja nije potvr?eno u ovom mjerenju: `nosql-blocking` **1061.2?9.7 req/s, p95 2725.6 ms, 0% gre?aka, k6 exit 0** naspram `nosql-reactive` **863.2?42.6 req/s, p95 4274.9 ms, 0% gre?aka, k6 exit 99** (probijen p95 bud?et). Obja?njenje (teza): reaktivni model ne garantuje automatski bolju propusnost/latenciju ? zavisi od profila optere?enja i implementacije. Ovdje su mogu?i faktori vi?i overhead reaktivnog steka (Reactor scheduling/operatori, reaktivni Mongo/Redis klijent) i ve?a potro?nja resursa (CPU 0.88 naspram 0.65, memorija 540 naspram 460 MB), dok je blokiraju?i stack (servlet + upravljani thread pool) uz ovaj miks I/O operacija (Mongo+Redis, katalog/korpa/narud?ba) bio efikasniji i stabilniji. HTTP gre?aka nema, pa razlika nije posljedica otkaza ve? pona?anja pod optere?enjem; nalaz valja tuma?iti u kontekstu ove konfiguracije (isti resursni limiti, isti backend resursi), a ne kao op?tu tvrdnju protiv reaktivnog pristupa.
* **S6 (failover / redis-down)** - faults are injected on purpose; the small error rate is the measured
  outage window, not a defect.
* **`k6 exit 99`** means at least one declared k6 *threshold* failed; it is a **result, not a broken run**.
  In every case here the missed budget is a p95 latency target: S1 `catalog_read_ms` <500 ms /
  `catalog_query_ms` <1000 ms, S3 `order_create_ms` <1000 ms (mongo-w1 only), S4 `orders_summary_ms` /
  `top_products_ms` <15 s, S5 `catalog_read_ms` <2000 ms, S7 `monthly_summary_ms` <30 s. `K6Runner`
  keeps the sample regardless and stores the exit code in the JSON, so a missed budget stays visible
  instead of being silently discarded.
* **S4 - fix metodologije + konačan rezultat (2026-11-05/06)** - `s4-20261027-111326-839e74e9.json` je
  **nevažeći**. Uzrok: Mongo je čuvao `orders.createdAt` kao BSON `Date`, a report servisi su filtrirali
  sa `from.toString()` (String) → 0 pogodaka, a `$substr` nad `Date` je lomio mjesečni sažetak. Ispravljeno
  (`Date.from`, `DateOperators.DateToString`), prozor sužen na 2024-01-05–07 (~14% narudžbi) da indeks bude
  selektivan, `category-monthly-summary` (nefiltrirani full scan) prebačen u S7, dodat `idx_exp_order_created`,
  a runner sada upisuje **`explainPlans` (Q1–Q5, INDEX/COLLECTION_SCAN/SEQUENTIAL_SCAN)** po targetu u rezultat.
  S4 explain sada mjeri stvarni workload (Q1 katalog kategoria+sort cijena, Q2/Q3 date-filtrirani
  sales-by-category/top-products, Q4/Q5 kontrasti bez filtera); toggle upravlja i S4-relevantnim baseline
  indeksima.
  - **Planovi (dokaz, `s4-20261105-231941-f2cc7986.json`):** Mongo indexed Q1–Q3 = `IXSCAN` (INDEX),
    unindexed Q1–Q3 = `COLLSCAN`; Postgres indexed Q1–Q3 = `INDEX` (index/bitmap scan, uključujući `created_at`),
    unindexed Q1–Q3 = `SEQUENTIAL_SCAN`. Q4/Q5 skeniraju u oba režima (bez filtera) — očekivano.
  - **Performanse (20 klijenata, 3×30 s, warmup 30 s):** Postgres 33.9±1.8 req/s / p95 851 ms (indeksirano)
    naspram 33.7±0.3 / 814 ms (neindeksirano), 0% grešaka — **razlika zanemarljiva, unutar šuma**: uz 14%
    selektivnosti na 10k narudžbi seq scan nad `orders` je jeftin, a cijena join+group agregacije dominira, pa
    indeks (iako korišten, dokazano planom) ne daje mjerljiv end-to-end dobitak na ovoj skali. Mongo 0.9 req/s,
    p95 ~30 s, **32–34% grešaka** (timeouti) u oba režima — `$unwind+$lookup+$group` pipeline je preskup; uz
    ~17 uspješnih zahtjeva po repeticiji poređenje propusnosti nije pouzdano, iako planovi potvrđuju prelaz na
    COLLSCAN. **Zaključak:** S4 sad korektno mjeri i *dokazuje* korištenje indeksa; dramatičnu razliku performansi
    na ovom datasetu/obliku upita ne daje (osobito Mongo agregacije) — to je nalaz, ne kvar toggle-a. Veća
    selektivnost/volumen ili izolovaniji upit bi jasnije pokazao dobitak.
* **S4B - izolacija indeksa, bez join fanout-a (2026-11-06, refined `s4b-20261106-122835-dcb29f10.json`, `dcb29f10`)** - metodolo?ka dopuna S4 (koji ostaje kao kontekst). Refinisano za jasan/normalan signal: **visoko selektivan prozor 6 h** (2024-01-05 00:00?06:00Z, ~181/10k narud?bi = ~1.8%) i 2? `order-totals` po iteraciji (bez `$lookup`/product join-a), pa je filter po datumu (`createdAt`/`created_at`, Q6) dominantan korak. 4 targeta, 3?30 s, 20 klijenata, warmup 10 s.
  - **Planovi (dokaz):** indexed `Q1=INDEX Q6=INDEX Q7=COLLECTION_SCAN/SEQUENTIAL_SCAN`; unindexed `Q1/Q6/Q7` = scan. Toggle potvr?en.
  - **Mongo (nosql-blocking):** indexed **537.8?16.9 req/s, p95 5.4 ms, 0% gre?aka** naspram unindexed **378.1?5.0 req/s, p95 90.4 ms, 0%** ? **+42% propusnosti** i **~17? ni?i p95** sa indeksom. Normalan broj operacija i zna?ajna, stabilna prednost indeksiranja (za razliku od S4 join-heavy, 1?2 req/s/timeout).
  - **Postgres (relational):** indexed **141.0?36.8 req/s, p95 359.5 ms** (velika varijansa) naspram unindexed **281.5?19.0 req/s, p95 102.5 ms**, 0% gre?aka. Iako plan pokazuje **INDEX** na Q1/Q6, end-to-end je u ovom mjerenju obrnut/nestabilan na ovoj skali/selektivnosti ? nalaz da kori??enje indeksa ne garantuje br?i krajnji rezultat (selektivnost, tro?ak pristupa, overhead planera/agregacije); interpretirati uz plan i po potrebi ponoviti.
  - **Zaklju?ak:** S4B pouzdano demonstrira korist indeksa na Mongo strani (normalan ops + jasan gain); Postgres ostaje neodlu?an/obrnut uz varijansu. Raniji eksplorativni S4B: `s4b-20261106-101253-1a60fc02.json`.
* **S7 - aggregation cost** - the `monthly_summary_ms` p95 reaches ~38-40 s on MongoDB versus a few
  seconds on PostgreSQL. This is a genuine finding: the `$unwind` + `$lookup` pipeline over 10k orders is
  far slower than the equivalent relational join under 20 concurrent clients, which is exactly the
  trade-off S7 (complex joins) is meant to expose. (The full-scan `category-monthly-summary` remains the
  focus of S7.)
