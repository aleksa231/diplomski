/*
 * S5 - Reactive vs blocking.
 *
 * What it measures: the same REST contract served by two stacks (Spring MVC with a thread pool
 * versus Spring WebFlux) under a client ramp. The workload is intentionally I/O-bound to expose
 * thread starvation in the blocking stack:
 *
 *   - 10% catalog traffic (GET /api/products/{id}, GET /api/products?...).
 *     Kept small so the DB is not the bottleneck; this traffic stays fast on both stacks.
 *   - 90% GET /api/products/{id}/with-recommendations?delayMs=<500|1500>. This call blocks on
 *     `stub-service` for 500 ms or 1,500 ms. With 90% of the load going through the slow path
 *     AND the Tomcat thread pool capped at 50 (applied automatically by the runner for S5),
 *     the blocking stack runs out of threads quickly while WebFlux's event loop keeps accepting
 *     new connections. The two delays are reported as separate trends.
 *
 * Thread-pool cap: the experiment-runner temporarily sets maxThreads=50 on Tomcat-based targets
 * (nosql-blocking, relational) via POST /api/admin/threads before each S5 measurement and
 * restores 200 threads afterwards. The reactive target (nosql-reactive / Netty) ignores the
 * call -- its event loop is unaffected by the limit.
 *
 * The ramp starts at START_CLIENTS (default 2.5% of the peak, i.e. 50 when the peak is 2,000 as
 * the spec asks for) and rises to CLIENTS over the first 30% of the run.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO, START_CLIENTS (optional).
 *
 * Good result: recommendation latencies diverge sharply -- the blocking stack's p95 explodes once
 * its 50 threads are exhausted; the reactive stack's p95 stays proportional to the stub delay.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S5';
const CLIENTS = Number(__ENV.CLIENTS || 200);
const DURATION_SEC = Number(__ENV.DURATION_SEC || 30);
const START_CLIENTS = Number(__ENV.START_CLIENTS || Math.max(1, Math.round(CLIENTS * 0.025)));

// Ramp up / hold / ramp down, sized from the requested duration.
const RAMP_UP = Math.max(1, Math.round(DURATION_SEC * 0.3));
const HOLD = Math.max(1, Math.round(DURATION_SEC * 0.5));
const RAMP_DOWN = Math.max(1, DURATION_SEC - RAMP_UP - HOLD);

// Client timeouts are deliberately longer than any latency the system can physically produce here.
// A request that the client abandons never becomes a sample: it is dropped from the trend and
// counted as a failed check, which both hides the real latency and depresses the reported
// throughput. When the timeout is short enough to fire, every afflicted metric is an artefact
// (e.g. a p95 of exactly 15000 ms) rather than a measurement.
const READ_BY_ID_TIMEOUT = '30s';
const FILTERED_QUERY_TIMEOUT = '60s';
const RECOMMENDATIONS_TIMEOUT = '60s';

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];
const ATTR_FILTERS = [
  'attr.ramGb=16',
  'attr.fiveG=true',
  'attr.color=black',
  'attr.organic=true',
  'attr.size=M',
];
const SORTS = ['price,asc', 'price,desc', 'name,asc'];

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: START_CLIENTS,
      gracefulRampDown: '5s',
      stages: [
        { duration: RAMP_UP + 's', target: CLIENTS },
        { duration: HOLD + 's', target: CLIENTS },
        { duration: RAMP_DOWN + 's', target: 0 },
      ],
    },
  },
  thresholds: {
    // Under peak load the blocking stack may saturate; thresholds are informative, not pass/fail gates.
    checks: [{ threshold: 'rate>0.05', abortOnFail: false }],
    catalog_read_ms: [{ threshold: 'p(95)<2000', abortOnFail: false }],
    catalog_query_ms: [{ threshold: 'p(95)<3000', abortOnFail: false }],
    recommendations_500ms: [{ threshold: 'p(95)<5000', abortOnFail: false }],
    recommendations_1500ms: [{ threshold: 'p(95)<8000', abortOnFail: false }],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const byIdTrend = new Trend('catalog_read_ms', true);
const queryTrend = new Trend('catalog_query_ms', true);
const recsFast = new Trend('recommendations_500ms', true);
const recsSlow = new Trend('recommendations_1500ms', true);
const notFound = new Counter('product_not_found');
const okRate = new Rate('s5_ok');

// Deterministic per-VU RNG so the request mix is reproducible across runs.
function makeRng(seed) {
  let s = seed >>> 0;
  return function () {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
function pick(arr, rng) {
  return arr[Math.floor(rng() * arr.length)];
}

function buildQuery(rng) {
  const parts = [];
  if (rng() < 0.5) parts.push('category=' + encodeURIComponent(pick(CATEGORIES, rng)));
  if (rng() < 0.6) parts.push(pick(ATTR_FILTERS, rng));
  parts.push('page=' + Math.floor(rng() * 3));
  parts.push('size=' + (10 + Math.floor(rng() * 3) * 10));
  if (rng() < 0.5) parts.push('sort=' + encodeURIComponent(pick(SORTS, rng)));
  return parts.join('&');
}

export function setup() {
  const poolSize = Number(__ENV.DATASET_SIZE || 5000);
  return { ids: loadProductIds(BASE, poolSize) };
}

export default function (data) {
  const rng = makeRng(5000 + __VU * 2654435761 + __ITER);
  const ids = data.ids;

  if (rng() < 0.1) {
    if (rng() < 0.5) {
      group('read_by_id', function () {
        const res = http.get(`${BASE}/api/products/${pick(ids, rng)}`, {
          timeout: READ_BY_ID_TIMEOUT,
          tags: { op: 'read_by_id' },
        });
        okRate.add(check(res, { 'GET /api/products/{id} -> 200': (r) => r.status === 200 }));
        if (res.status === 404) notFound.add(1);
        if (res.status === 200) byIdTrend.add(res.timings.duration);
      });
    } else {
      group('filtered_query', function () {
        const res = http.get(`${BASE}/api/products?${buildQuery(rng)}`, {
          timeout: FILTERED_QUERY_TIMEOUT,
          tags: { op: 'query_filter' },
        });
        okRate.add(check(res, { 'GET /api/products?... -> 200': (r) => r.status === 200 }));
        if (res.status === 200) queryTrend.add(res.timings.duration);
      });
    }
  } else {
    // I/O-bound workload (90% of requests): exposes thread starvation in the blocking stack.
    const delay = rng() < 0.5 ? 500 : 1500;
    group('with_recommendations', function () {
      const res = http.get(`${BASE}/api/products/${pick(ids, rng)}/with-recommendations?delayMs=${delay}`, {
        timeout: RECOMMENDATIONS_TIMEOUT,
        tags: { op: 'with_recommendations', stub_delay_ms: String(delay) },
      });
      okRate.add(check(res, { 'GET .../with-recommendations -> 200': (r) => r.status === 200 }));
      if (res.status === 200) {
        if (delay === 500) recsFast.add(res.timings.duration);
        else recsSlow.add(res.timings.duration);
      }
    });
  }

  sleep(0.05 + rng() * 0.1);
}
