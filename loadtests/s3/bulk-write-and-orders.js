/*
 * S3 - Write throughput (bulk insert + full cart -> order flow).
 *
 * What it measures: write-path throughput and the atomic stock-decrement of the order flow.
 *   - ~40% of iterations: POST /api/products/bulk   (batch taken from the shared setup pool,
 *                          each batch gets unique SKUs so inserts never collide)
 *   - ~60% of iterations: POST /api/carts/{cartId}/items  (add 1-3 items)
 *                         POST /api/orders               (turn the cart into an order)
 *
 * HTTP 409 from POST /api/orders is an EXPECTED, non-failing outcome: it means the conditional
 * stock decrement correctly rejected an order for insufficient stock. It is counted in the
 * `expected_conflicts` counter and never treated as an error. Every other non-2xx is an error.
 *
 * Env vars: TARGET_URL, CLIENTS, DURATION_SEC, DATASET_SIZE, SCENARIO.
 *
 * Good result: high bulk throughput, order latency dominated by the stock update, and
 * `order_unexpected_errors` staying at 0.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S3';
const BATCH_SIZE = Number(__ENV.BULK_BATCH_SIZE || 20);

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    // NOTE: k6 counts HTTP 409 as a "failed" request, and 409 is an expected order outcome
    // here, so the generic threshold stays lenient - the custom counters are authoritative.
    http_req_failed: ['rate<0.5'],
    checks: ['rate>0.90'],
    bulk_insert_ms: ['p(95)<2000'],
    order_create_ms: ['p(95)<1000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const bulkMs = new Trend('bulk_insert_ms', true);
const orderMs = new Trend('order_create_ms', true);
const cartAddMs = new Trend('cart_add_ms', true);

const productsInserted = new Counter('products_inserted');
const ordersCreated = new Counter('orders_created');
const expectedConflicts = new Counter('expected_conflicts');
const orderUnexpected = new Counter('order_unexpected_errors');
const cartErrors = new Counter('cart_errors');
const bulkOk = new Rate('bulk_insert_ok');
const orderOk = new Rate('order_create_ok');

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

// Category-appropriate attribute template (mirrors docs/data-model.md).
function attributesFor(category, rng) {
  switch (category) {
    case 'laptops':
      return { cpu: rng() < 0.5 ? 'Intel Core i7' : 'AMD Ryzen 7', ramGb: [8, 16, 32, 64][Math.floor(rng() * 4)], gpu: 'integrated' };
    case 'phones':
      return { os: rng() < 0.5 ? 'Android' : 'iOS', ramGb: [4, 6, 8, 12][Math.floor(rng() * 4)], fiveG: rng() < 0.5 };
    case 'clothing':
      return { size: ['S', 'M', 'L', 'XL'][Math.floor(rng() * 4)], color: ['black', 'white', 'blue'][Math.floor(rng() * 3)] };
    case 'groceries':
      return { organic: rng() < 0.5, weightG: [250, 500, 1000][Math.floor(rng() * 3)] };
    case 'books':
      return { genre: ['Sci-Fi', 'Fantasy', 'Mystery'][Math.floor(rng() * 3)], pages: 150 + Math.floor(rng() * 600) };
    default:
      return {};
  }
}

function buildProducts(rng, count, runId) {
  const batch = [];
  for (let i = 0; i < count; i++) {
    const category = pick(CATEGORIES, rng);
    // The VU/ITER/i part repeats across k6 invocations (warmup + every repetition) because the RNG
    // is seeded deterministically, so the SKUs used to collide on the next run: Postgres rejects them
    // (unique constraint on sku) while Mongo silently accepts the duplicates. runId is generated once
    // per k6 run in setup() and makes every SKU unique across the whole benchmark.
    const suffix = String(__VU) + '-' + String(__ITER) + '-' + String(i) + '-' + runId;
    batch.push({
      sku: 'BK-' + suffix.toUpperCase(),
      name: 'S3 Product ' + suffix,
      category: category,
      price: Math.round((5 + rng() * 1995) * 100) / 100,
      stock: 10 + Math.floor(rng() * 990),
      attributes: attributesFor(category, rng),
    });
  }
  return batch;
}

export function setup() {
  // Paginate the catalog (up to DATASET_SIZE ids) instead of capping at one 100-item page: a tiny
  // pool makes every VU hammer the same few products, and the concurrent stock decrements then
  // collide inside MongoDB transactions. S1/S2/S5/S6 all use this same helper.
  const poolSize = Number(__ENV.DATASET_SIZE || 200);
  // Unique per k6 invocation: keeps bulk-insert SKUs from colliding across warmup and repetitions.
  const runId = Date.now().toString(36) + '-' + Math.floor(Math.random() * 1e9).toString(36);
  return { ids: loadProductIds(BASE, poolSize), runId: runId };
}

function bulkInsert(rng, runId) {
  const res = http.post(`${BASE}/api/products/bulk`, JSON.stringify(buildProducts(rng, BATCH_SIZE, runId)), {
    headers: JSON_HEADERS,
    timeout: '20s',
    tags: { op: 'bulk_insert' },
  });
  const ok = check(res, { 'POST /api/products/bulk -> 201': (r) => r.status === 201 });
  bulkOk.add(ok);
  if (ok) {
    bulkMs.add(res.timings.duration);
    let inserted = 0;
    try {
      inserted = res.json('insertedCount') || 0;
    } catch (e) {
      inserted = 0;
    }
    productsInserted.add(inserted);
  }
}

function orderFlow(rng, ids) {
  const cartId = `s3-${__VU}-${__ITER}-${Math.floor(rng() * 1e6)}`;
  const itemCount = 1 + Math.floor(rng() * 3);
  let added = 0;

  for (let i = 0; i < itemCount; i++) {
    const productId = pick(ids, rng);
    const res = http.post(
      `${BASE}/api/carts/${cartId}/items`,
      JSON.stringify({ productId: productId, quantity: 1 }),
      { headers: JSON_HEADERS, timeout: '5s', tags: { op: 'cart_add' } }
    );
    if (res.status === 200) {
      added++;
      cartAddMs.add(res.timings.duration);
    } else {
      cartErrors.add(1);
    }
  }

  if (added === 0) {
    // Nothing could be added (e.g. synthetic ids); skip the order instead of sending garbage.
    return;
  }

  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ cartId: cartId, customerRef: 's3-cust-' + __VU }),
    {
      headers: JSON_HEADERS,
      timeout: '10s',
      tags: { op: 'order_create' },
      // 409 is the expected "not enough stock" outcome of the conditional decrement, so it must
      // not count towards http_req_failed; timeouts and 5xx remain the real failures.
      responseCallback: http.expectedStatuses(201, 409),
    }
  );

  if (res.status === 201) {
    ordersCreated.add(1);
    orderOk.add(true);
    orderMs.add(res.timings.duration);
  } else if (res.status === 409) {
    // Expected conflict: not enough stock for a cart line. Not an error.
    expectedConflicts.add(1);
    orderOk.add(true);
  } else {
    orderUnexpected.add(1);
    orderOk.add(false);
  }
  check(res, { 'POST /api/orders -> 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

// Shared id pool populated in setup() before the first iteration runs.
export default function (data) {
  const ids = data.ids;
  const runId = data.runId;
  const rng = makeRng(3000 + __VU * 15485863 + __ITER);

  if (rng() < 0.4) {
    group('bulk_write', function () {
      bulkInsert(rng, runId);
    });
  } else {
    group('order_flow', function () {
      orderFlow(rng, ids);
    });
  }

  sleep(0.05 + rng() * 0.15);
}
