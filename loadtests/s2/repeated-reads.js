/*
 * S2 - Caching: Repeated catalog queries with Redis cache vs direct database read.
 *
 * What it measures:
 *   Repeated read requests for product lists (category filter, sorting, pagination).
 *   - Compares backend with Redis cache enabled vs disabled.
 *   - Exercises both MongoDB and PostgreSQL.
 *   - Verifies cache latency reduction and throughput improvement.
 *   - Periodically mutates a product price/stock to trigger cache eviction.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, SCENARIO.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S2';

export const options = {
  vus: Number(__ENV.CLIENTS || 50),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    catalog_query_ms: ['p(95)<500'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const queryTrend = new Trend('catalog_query_ms', true);
const queryRate = new Rate('catalog_query_ok');
const evictionCount = new Counter('cache_eviction_updates');

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];
const SORTS = ['price,asc', 'price,desc', 'name,asc'];

export function setup() {
  const poolSize = Number(__ENV.DATASET_SIZE || 5000);
  const ids = loadProductIds(BASE, poolSize);
  return { ids };
}

export default function (data) {
  // Repeated requests for standard catalog queries (hot query pattern)
  const category = CATEGORIES[__ITER % CATEGORIES.length];
  const sort = SORTS[__ITER % SORTS.length];

  group('cached_catalog_query', function () {
    const res = http.get(`${BASE}/api/products?category=${encodeURIComponent(category)}&page=0&size=20&sort=${encodeURIComponent(sort)}`, {
      timeout: '10s',
      tags: { op: 'catalog_query' },
    });
    const ok = check(res, { 'GET /api/products?category=... -> 200': (r) => r.status === 200 });
    queryRate.add(ok);
    if (res.status === 200) {
      queryTrend.add(res.timings.duration);
    }
  });

  // Once every ~100 iterations per VU, perform an update to demonstrate cache eviction/invalidation
  if (__ITER > 0 && __ITER % 100 === 0 && __VU === 1 && data.ids.length > 0) {
    const targetId = data.ids[0];
    const getRes = http.get(`${BASE}/api/products/${targetId}`);
    if (getRes.status === 200) {
      const prod = getRes.json();
      const updatedPrice = prod.price > 100 ? prod.price - 1 : prod.price + 1;
      const updateRes = http.put(
        `${BASE}/api/products/${targetId}`,
        JSON.stringify({
          sku: prod.sku,
          name: prod.name,
          category: prod.category,
          price: updatedPrice,
          stock: prod.stock,
          attributes: prod.attributes || {}
        }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
      );
      if (updateRes.status === 200) {
        evictionCount.add(1);
      }
    }
  }

  sleep(0.02 + Math.random() * 0.05);
}
