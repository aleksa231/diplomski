/*
 * S6 - Horizontal scaling and resilience: steady load for the failure-injection targets.
 *
 * Used by the `failover` target (S6c) and the `redis-down` target (S6d); the runner fires the
 * matching script from scripts/ roughly `inject-delay-sec` seconds into the run and (for
 * redis-down) runs the recovery script afterwards. The per-second timeline of rps and errors is
 * read from Prometheus by the runner, not by this script.
 *
 * The mix is deliberately steady and catalog-heavy, because that is what the two outages probe:
 *   - 60% GET /api/products/{id}          -> must keep succeeding from the database after the
 *                                            primary failover, and must fall back when Redis dies
 *   - 25% GET /api/products?...           -> same, through the cache path
 *   - 15% cart add + get                  -> Redis-backed; while Redis is down this must return
 *                                            503 quickly instead of hanging
 *
 * No http_req_failed threshold is set: for a resilience run the failures ARE the measurement, and
 * a threshold would only obscure that. Cart failures are counted separately because a 503 there is
 * the specified behaviour, not a defect.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S6';

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];

export const options = {
  vus: Number(__ENV.CLIENTS || 50),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    // Only the catalog path is expected to survive an outage end to end.
    catalog_read_ms: ['p(95)<5000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const catalogReadMs = new Trend('catalog_read_ms', true);
const catalogQueryMs = new Trend('catalog_query_ms', true);
const cartMs = new Trend('cart_ms', true);

const catalogErrors = new Counter('catalog_errors');
const cartUnavailable = new Counter('cart_unavailable_503');
const cartErrors = new Counter('cart_errors');
const catalogOk = new Rate('catalog_ok');

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

export function setup() {
  const poolSize = Number(__ENV.DATASET_SIZE || 5000);
  return { ids: loadProductIds(BASE, poolSize) };
}

export default function (data) {
  const rng = makeRng(6000 + __VU * 2654435761 + __ITER);
  const ids = data.ids;
  const roll = rng();

  if (roll < 0.60) {
    group('catalog_read', function () {
      const res = http.get(`${BASE}/api/products/${pick(ids, rng)}`, {
        timeout: '10s',
        tags: { op: 'read_by_id' },
      });
      const ok = res.status === 200;
      catalogOk.add(ok);
      if (!ok) catalogErrors.add(1);
      if (ok) catalogReadMs.add(res.timings.duration);
    });
  } else if (roll < 0.85) {
    group('catalog_query', function () {
      const query = 'category=' + encodeURIComponent(pick(CATEGORIES, rng)) + '&page=0&size=20';
      const res = http.get(`${BASE}/api/products?${query}`, {
        timeout: '15s',
        tags: { op: 'query_filter' },
      });
      const ok = res.status === 200;
      catalogOk.add(ok);
      if (!ok) catalogErrors.add(1);
      if (ok) catalogQueryMs.add(res.timings.duration);
    });
  } else {
    group('cart', function () {
      const cartId = `bench-cart-${__VU}`;
      const productId = pick(ids, rng);
      const payload = JSON.stringify({ productId: productId, quantity: 1 });
      const headers = { 'Content-Type': 'application/json' };

      const add = http.post(`${BASE}/api/carts/${cartId}/items`, payload, {
        headers: headers,
        timeout: '10s',
        tags: { op: 'cart_add' },
        // 503 is the specified fail-fast behaviour while Redis is down, so it is not an error.
        responseCallback: http.expectedStatuses(200, 201, 503),
      });
      // 503 is the specified behaviour while Redis is down (fail fast, no hanging).
      if (add.status === 503) {
        cartUnavailable.add(1);
      } else if (add.status !== 200 && add.status !== 201) {
        cartErrors.add(1);
      }
      cartMs.add(add.timings.duration);

      const get = http.get(`${BASE}/api/carts/${cartId}/items`, {
        timeout: '10s',
        tags: { op: 'cart_get' },
        // Same as the add above: 503 while Redis is down is the specified behaviour.
        responseCallback: http.expectedStatuses(200, 503),
      });
      if (get.status === 503) {
        cartUnavailable.add(1);
      } else if (get.status !== 200) {
        cartErrors.add(1);
      }
      cartMs.add(get.timings.duration);

      check(add, { 'cart add -> 200/201/503': (r) => r.status === 200 || r.status === 201 || r.status === 503 });
    });
  }

  sleep(0.05 + rng() * 0.15);
}
