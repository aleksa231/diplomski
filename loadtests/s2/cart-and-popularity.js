/*
 * S2 - Caching, cart latency & popularity ranking.
 *
 * What it measures: how fast the cart path is (Redis hash / single Mongo doc) and how the
 * catalog + popularity endpoints behave under a hot-id read pattern that exercises the cache.
 *
 * Each iteration:
 *   - add 1-3 items to a fresh cart   POST /api/carts/{cartId}/items
 *   - read the cart                   GET  /api/carts/{cartId}/items
 *   - remove one item                 DELETE /api/carts/{cartId}/items?productId=<id>
 *   - re-read a small hot-id set      GET  /api/products/{id}  (warms the cache)
 *   - periodically                    GET  /api/popular?type=views|purchases&limit=10
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO.
 *
 * Good result: flat cart read latency (cache hit), hot-id reads clearly faster than cold
 * reads, popular ranking stable; 503 only appears if Redis is actually down.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S2';
const HOT_SET_SIZE = Number(__ENV.HOT_SET_SIZE || 5);

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  discardResponseBodies: false,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    cart_get_ms: ['p(95)<300'],
    cart_add_ms: ['p(95)<300'],
    cart_remove_ms: ['p(95)<300'],
    popular_ms: ['p(95)<300'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const cartAddMs = new Trend('cart_add_ms', true);
const cartGetMs = new Trend('cart_get_ms', true);
const cartRemoveMs = new Trend('cart_remove_ms', true);
const popularMs = new Trend('popular_ms', true);
const hotReadMs = new Trend('hot_read_ms', true);

const popularCalls = new Counter('popular_calls');
const hotReads = new Counter('hot_reads');
const cartErrors = new Counter('cart_errors');
const cartOk = new Rate('cart_ok');

const JSON_HEADERS = { 'Content-Type': 'application/json' };

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
  const ids = loadProductIds(BASE, poolSize);
  return { ids, hotIds: ids.slice(0, Math.min(HOT_SET_SIZE, ids.length)) };
}

export default function (data) {
  const rng = makeRng(2000 + __VU * 104729 + __ITER);
  const ids = data.ids;
  const cartId = `s2-${__VU}-${__ITER}-${Math.floor(rng() * 1e6)}`;

  const itemCount = 1 + Math.floor(rng() * 3);
  const added = [];

  group('cart_flow', function () {
    for (let i = 0; i < itemCount; i++) {
      const productId = pick(ids, rng);
      const res = http.post(
        `${BASE}/api/carts/${cartId}/items`,
        JSON.stringify({ productId: productId, quantity: 1 + Math.floor(rng() * 3) }),
        { headers: JSON_HEADERS, timeout: '5s', tags: { op: 'cart_add' } }
      );
      const ok = check(res, { 'POST /api/carts/{id}/items -> 200': (r) => r.status === 200 });
      cartOk.add(ok);
      if (ok) {
        added.push(productId);
        cartAddMs.add(res.timings.duration);
      } else {
        cartErrors.add(1);
      }
    }

    const readRes = http.get(`${BASE}/api/carts/${cartId}/items`, {
      timeout: '5s',
      tags: { op: 'cart_get' },
    });
    const readOk = check(readRes, { 'GET /api/carts/{id}/items -> 200': (r) => r.status === 200 });
    cartOk.add(readOk);
    if (readOk) cartGetMs.add(readRes.timings.duration);
    else cartErrors.add(1);

    if (added.length > 0) {
      const removeRes = http.del(
        `${BASE}/api/carts/${cartId}/items?productId=${encodeURIComponent(added[0])}`,
        null,
        { timeout: '5s', tags: { op: 'cart_remove' } }
      );
      const removeOk = check(removeRes, { 'DELETE /api/carts/{id}/items -> 200': (r) => r.status === 200 || r.status === 204 });
      cartOk.add(removeOk);
      if (removeOk) cartRemoveMs.add(removeRes.timings.duration);
      else cartErrors.add(1);
    }
  });

  // Cache-warming hot-id reads.
  group('hot_reads', function () {
    const hits = 2 + Math.floor(rng() * 2);
    for (let i = 0; i < hits; i++) {
      const id = pick(data.hotIds, rng);
      const res = http.get(`${BASE}/api/products/${id}`, {
        timeout: '5s',
        tags: { op: 'hot_read' },
      });
      if (res.status === 200) hotReadMs.add(res.timings.duration);
      hotReads.add(1);
      check(res, { 'GET /api/products/{id} (hot) -> 200': (r) => r.status === 200 });
    }
  });

  if (rng() < 0.5) {
    group('popularity', function () {
      const type = rng() < 0.7 ? 'views' : 'purchases';
      const res = http.get(`${BASE}/api/popular?type=${type}&limit=10`, {
        timeout: '5s',
        tags: { op: 'popular' },
      });
      const ok = check(res, { 'GET /api/popular -> 200': (r) => r.status === 200 });
      if (ok) popularMs.add(res.timings.duration);
      popularCalls.add(1);
    });
  }

  sleep(0.05 + rng() * 0.15);
}
