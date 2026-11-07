/*
 * S4B - Index isolation (no join fanout).
 *
 * What it measures: a date-filtered aggregation that does NOT join/lookup the products collection,
 * so the dominant indexed step is the match on orders.createdAt / orders(created_at). This isolates
 * the index effect that S4's join-heavy reports ($lookup + $group) can hide.
 *   - GET /api/reports/order-totals?from=&to=            (date-filtered totals, no product join)
 *   - GET /api/products?category=&page=&size=&sort=       (catalog list, category+price index)
 *
 * S4 remains the canonical join-heavy aggregation scenario; S4B answers "does the index help when the
 * access path is the bottleneck?" The narrow window (2024-01-05..2024-01-07, ~180 of 10k seeded orders (6h, highly selective))
 * makes the createdAt index selective. Same window as S4 probes.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO, REPORT_FROM, REPORT_TO.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S4B';
const REPORT_FROM = __ENV.REPORT_FROM || '2024-01-05T00:00:00Z';
const REPORT_TO = __ENV.REPORT_TO || '2024-01-05T06:00:00Z';

export const options = {
  scenarios: {
    isolated: {
      executor: 'constant-vus',
      vus: Number(__ENV.CLIENTS || 20),
      duration: `${Number(__ENV.DURATION_SEC || 30)}s`,
      exec: 'isolatedIndex',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    order_totals_ms: ['p(95)<10000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const orderTotalsMs = new Trend('order_totals_ms', true);
const catalogQueryMs = new Trend('catalog_query_ms', true);
const reportErrors = new Counter('report_errors');
const reportOk = new Rate('report_ok');

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];
function pick(arr, rng) {
  return arr[Math.floor(rng() * arr.length)];
}
function mulberry32(a) {
  return function () {
    let t = (a += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32((__VU || 1) * 7919);

export function isolatedIndex() {
  // Run the index-sensitive report twice per iteration so the createdAt access path dominates.
  for (let i = 0; i < 2; i++) {
    group('order_totals', function () {
      const res = http.get(
        `${BASE}/api/reports/order-totals?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}`,
        { timeout: '60s', tags: { op: 'order_totals' } }
      );
      const ok = check(res, { 'GET /api/reports/order-totals -> 200': (r) => r.status === 200 });
      reportOk.add(ok);
      if (ok) orderTotalsMs.add(res.timings.duration);
      else reportErrors.add(1);
    });
  }

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

  sleep(0.1);
}

export function handleSummary(data) {
  return { stdout: textSummary(data) };
}
function textSummary(data) {
  const m = data.metrics || {};
  const g = (n, s) => (m[n] && m[n].values && m[n].values[s] !== undefined ? m[n].values[s].toFixed(2) : 'n/a');
  return (
    `\n=== S4B Index Isolation [${SCENARIO}] target=${BASE} ===\n` +
    `  http_reqs          : ${g('http_reqs', 'count')}\n` +
    `  http_req_failed    : ${g('http_req_failed', 'rate')}\n` +
    `  order_totals  p95  : ${g('order_totals_ms', 'p(95)')} ms\n` +
    `  catalog_query p95  : ${g('catalog_query_ms', 'p(95)')} ms\n`
  );
}
