/*
 * S4 - Indexing & aggregation query plans.
 *
 * What it measures: the report endpoints that a date filter is able to narrow, run a few times per
 * VU so that an indexed run and an unindexed run are directly comparable.
 *   - GET /api/reports/sales-by-category?from=&to=      (sales summary per category)
 *   - GET /api/reports/top-products?from=&to=&limit=10  (join + group + sort over the same window)
 *   - GET /api/products?category=&page=&size=&sort=     (catalog list query under the same indexes)
 * Additionally each VU fetches the query plan ONCE at startup via
 *   - GET /api/admin/explain?query=Q1..Q5   (the runner toggles the indexes before the run)
 *
 * REPORT_FROM/REPORT_TO are deliberately a NARROW window inside the generated dataset
 * (2024-01-01 .. 2024-01-14, one order every 120 s). With the old window (2024..2026) every order
 * matched the filter, so the createdAt index selected 100% of the collection and the indexed and
 * unindexed runs measured the same scan. Two days select ~14% of the orders, which is what makes the
 * index worth having - and therefore observable.
 *
 * The join-heavy /api/reports/category-monthly-summary is intentionally NOT part of S4: it has no
 * filter and always scans every order, so it dominated the p95 and hid the index effect. It belongs
 * to S7 ("where the relational model wins"), which measures it on its own.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO.
 *
 * Good result: with indexes enabled the report latencies drop sharply vs. the unindexed run;
 * explain plans show IXSCAN / index scans instead of COLLSCAN / seq scans.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S4';
// 14% of the seeded orders (2024-01-01 + i*120s, 10k orders -> last one on 2024-01-14).
const REPORT_FROM = __ENV.REPORT_FROM || '2024-01-05T00:00:00Z';
const REPORT_TO = __ENV.REPORT_TO || '2024-01-05T06:00:00Z';
const EXPLAIN_QUERIES = ['Q1', 'Q2', 'Q3', 'Q4', 'Q5'];
// Q2 is the only explain query built on a MongoDB `$text` search. A $text query cannot produce a
// plan at all without a text index, so in the `mongo-unindexed` variant the service answers HTTP 500
// ("text index required"). That is the expected outcome of the unindexed run and is itself a finding
// (indexes are a functional prerequisite for text search, not only a latency optimisation) - it is
// recorded in `explain_unavailable`, kept out of the measured error rate, and must NOT be confused
// with a failure of the report endpoints that this scenario compares.
const TEXT_INDEX_QUERY = 'Q2';
const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.90'],
    orders_summary_ms: ['p(95)<15000'],
    top_products_ms: ['p(95)<15000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const ordersSummaryMs = new Trend('orders_summary_ms', true);
const topProductsMs = new Trend('top_products_ms', true);
const catalogQueryMs = new Trend('catalog_query_ms', true);
const explainMs = new Trend('explain_ms', true);

const explainCalls = new Counter('explain_calls');
const explainUnavailable = new Counter('explain_unavailable');
const reportErrors = new Counter('report_errors');
const reportOk = new Rate('report_ok');

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

// Per-VU flag: k6 gives every VU its own module scope, so this runs once per VU.
let explained = false;

export function setup() {
  const poolSize = Math.min(Math.max(Number(__ENV.DATASET_SIZE || 200), 20), 500);
  const ids = [];
  try {
    const res = http.get(`${BASE}/api/products?size=${Math.min(poolSize, 100)}`, { timeout: '10s' });
    if (res.status === 200) {
      const body = res.json();
      const content = body && body.content ? body.content : [];
      for (const p of content) {
        if (p && p.id) ids.push(p.id);
      }
    }
  } catch (e) {
    // ignore - synthetic fallback below keeps setup from crashing
  }
  if (ids.length === 0) {
    for (let i = 0; i < poolSize; i++) ids.push(`synthetic-${i}`);
  }
  return { ids };
}

function runExplain() {
  group('explain_startup', function () {
    for (const q of EXPLAIN_QUERIES) {
      const needsTextIndex = q === TEXT_INDEX_QUERY;
      const res = http.get(`${BASE}/api/admin/explain?query=${q}`, {
        timeout: '30s',
        tags: { op: 'explain', query: q },
        // Accept the documented 500 for the $text query when no text index exists so that this
        // startup diagnostic is not counted as a failure of the measured report endpoints.
        responseCallback: needsTextIndex
          ? http.expectedStatuses(200, 500)
          : http.expectedStatuses(200),
      });
      explainCalls.add(1);
      if (res.status === 200) {
        explainMs.add(res.timings.duration);
      } else if (needsTextIndex && res.status === 500) {
        explainUnavailable.add(1);
      }
      check(res, {
        'GET /api/admin/explain -> 200 (or 500 for $text without text index)': (r) =>
          r.status === 200 || (needsTextIndex && r.status === 500),
      });
    }
  });
}

function reportRequests(rng) {
  group('orders_summary', function () {
    const res = http.get(
      `${BASE}/api/reports/sales-by-category?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}`,
      { timeout: '60s', tags: { op: 'orders_summary' } }
    );
    const ok = check(res, { 'GET /api/reports/sales-by-category -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) ordersSummaryMs.add(res.timings.duration);
    else reportErrors.add(1);
  });

  group('top_products', function () {
    const res = http.get(
      `${BASE}/api/reports/top-products?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}&limit=10`,
      { timeout: '60s', tags: { op: 'top_products' } }
    );
    const ok = check(res, { 'GET /api/reports/top-products -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) topProductsMs.add(res.timings.duration);
    else reportErrors.add(1);
  });

  group('catalog_query', function () {
    const query = 'category=' + encodeURIComponent(pick(CATEGORIES, rng)) + '&page=0&size=20&sort=price,asc';
    const res = http.get(`${BASE}/api/products?${query}`, {
      timeout: '15s',
      tags: { op: 'catalog_query' },
    });
    const ok = check(res, { 'GET /api/products?... -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) catalogQueryMs.add(res.timings.duration);
    else reportErrors.add(1);
  });
}

export default function (data) {
  const rng = makeRng(4000 + __VU * 32452843 + __ITER);

  if (!explained) {
    explained = true;
    runExplain();
  }

  // A few heavy requests per iteration only - the load is intentionally sparse.
  reportRequests(rng);

  sleep(0.1 + rng() * 0.1);
}
