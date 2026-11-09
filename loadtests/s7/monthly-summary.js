/*
 * S7 - Where the relational model wins.
 *
 * (a) GET /api/reports/category-monthly-summary on all targets, timed. This report joins orders,
 *     order items, products and categories and groups them per category per month: exactly the
 *     shape a relational engine with a good planner is good at, and the shape MongoDB has to
 *     implement with an aggregation pipeline. The other two report endpoints are polled as well so
 *     the run reflects the whole reporting surface.
 *
 * (b) Referential integrity (delete a category that still has products) is a correctness property,
 *     not a load characteristic. It is verified by the Java test suite per backend and written up
 *     in docs/s7-integrity.md; there is nothing meaningful for k6 to measure there.
 *
 * These are heavy queries, so the load is intentionally sparse: one full reporting round per
 * iteration plus a short pause, rather than a saturation test.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, SCENARIO.
 *
 * Good result: the monthly summary is stable and fast on PostgreSQL; on MongoDB the aggregation
 * pipeline pays for the join. Report both numbers honestly, that is the point of this scenario.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S5';
const REPORT_FROM = __ENV.REPORT_FROM || '2024-01-01T00:00:00Z';
const REPORT_TO = __ENV.REPORT_TO || '2026-12-31T23:59:59Z';

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    monthly_summary_ms: ['p(95)<30000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const monthlyMs = new Trend('monthly_summary_ms', true);
const salesMs = new Trend('sales_by_category_ms', true);
const topProductsMs = new Trend('top_products_ms', true);

const reportErrors = new Counter('report_errors');
const reportOk = new Rate('report_ok');

function makeRng(seed) {
  let s = seed >>> 0;
  return function () {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

export default function () {
  const rng = makeRng(7000 + __VU * 2654435761 + __ITER);

  group('monthly_summary', function () {
    const res = http.get(`${BASE}/api/reports/category-monthly-summary`, {
      timeout: '120s',
      tags: { op: 'monthly_summary' },
    });
    const ok = check(res, { 'GET /api/reports/category-monthly-summary -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) monthlyMs.add(res.timings.duration);
    else reportErrors.add(1);
  });

  group('sales_by_category', function () {
    const url = `${BASE}/api/reports/sales-by-category?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}`;
    const res = http.get(url, { timeout: '120s', tags: { op: 'sales_by_category' } });
    const ok = check(res, { 'GET /api/reports/sales-by-category -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) salesMs.add(res.timings.duration);
    else reportErrors.add(1);
  });

  if (rng() < 0.5) {
    group('top_products', function () {
      const url = `${BASE}/api/reports/top-products?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}&limit=10`;
      const res = http.get(url, { timeout: '120s', tags: { op: 'top_products' } });
      const ok = check(res, { 'GET /api/reports/top-products -> 200': (r) => r.status === 200 });
      reportOk.add(ok);
      if (ok) topProductsMs.add(res.timings.duration);
      else reportErrors.add(1);
    });
  }

  sleep(0.5 + rng() * 0.5);
}
