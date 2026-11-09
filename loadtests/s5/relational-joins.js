/*
 * S5 - Where the relational model wins (join-heavy analytical reporting).
 *
 * Hits a single endpoint that performs a 4-table join (orders → order_item → product → category)
 * with multi-dimensional aggregation: revenue, distinct orders/customers/products, and averages,
 * grouped by (category, month) over a date range.
 *
 * PostgreSQL executes this as one SQL query with hash/merge joins and native aggregates.
 * MongoDB must $unwind items, $lookup products to resolve category, and maintain multiple
 * $addToSet accumulators for distinct counts - the natural cost of this shape in a document store.
 *
 * The load is focused (one endpoint, short think time) so the measurement reflects query cost.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, SCENARIO, REPORT_FROM, REPORT_TO.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S5';
// Covers the seeded order window (orders seeded around Oct 2026).
const REPORT_FROM = __ENV.REPORT_FROM || '2026-01-01T00:00:00Z';
const REPORT_TO = __ENV.REPORT_TO || '2026-12-31T23:59:59Z';

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 60)) + 's',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    revenue_detail_ms: ['p(95)<30000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const revenueDetailMs = new Trend('revenue_detail_ms', true);
const reportErrors = new Counter('report_errors');
const reportOk = new Rate('report_ok');

export default function () {
  group('category_revenue_detail', function () {
    const url = `${BASE}/api/reports/category-revenue-detail?from=${encodeURIComponent(REPORT_FROM)}&to=${encodeURIComponent(REPORT_TO)}`;
    const res = http.get(url, {
      timeout: '120s',
      tags: { op: 'category_revenue_detail' },
    });
    const ok = check(res, { 'GET /api/reports/category-revenue-detail -> 200': (r) => r.status === 200 });
    reportOk.add(ok);
    if (ok) revenueDetailMs.add(res.timings.duration);
    else reportErrors.add(1);
  });

  // Minimal think time so throughput reflects query/engine cost rather than client sleep.
  sleep(0.05);
}
