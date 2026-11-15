/*
 * S3 - Last-item concurrency, transactions, and integrity.
 *
 * What it measures:
 *   100 concurrent customers try to buy a single remaining product item (stock = 1).
 *   - Exactly one purchase succeeds (HTTP 201).
 *   - 99 purchases are rejected due to insufficient stock (HTTP 409 Conflict).
 *   - Final product stock must remain exactly 0 and never go negative.
 *   - Measures latency, transaction execution, and conflict handling.
 *
 * Env vars: TARGET_URL, SCENARIO, ORDERS (default 100).
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Gauge, Trend } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S3';
const ORDER_COUNT = Number(__ENV.ORDERS || 100);

export const options = {
  scenarios: {
    last_item_checkout: {
      executor: 'shared-iterations',
      vus: ORDER_COUNT,
      iterations: ORDER_COUNT,
      maxDuration: '60s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    order_created: ['count==1'],
    order_rejected: ['count==' + (ORDER_COUNT - 1)],
    stock_after: ['value==0'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const created = new Counter('order_created');
const rejected = new Counter('order_rejected');
const unexpected = new Counter('unexpected_status');
const orderMs = new Trend('order_ms', true);
const stockAfter = new Gauge('stock_after');

export function setup() {
  const runId = Date.now().toString(36) + '-' + Math.floor(Math.random() * 1e9).toString(36);
  const product = {
    sku: `s3-last-item-${runId}`,
    name: 'S3 Posljednji Primjerak',
    category: 'laptops',
    price: 1200.0,
    stock: 1,
  };
  const createRes = http.post(`${BASE}/api/products`, JSON.stringify(product), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });
  if (createRes.status !== 201) {
    throw new Error(`S3: cannot create the single-stock product - HTTP ${createRes.status} ${createRes.body}`);
  }
  const productId = createRes.json().id;

  const carts = [];
  for (let i = 0; i < ORDER_COUNT; i++) {
    const cartId = `s3-cart-${runId}-${i}`;
    const added = http.post(
      `${BASE}/api/carts/${cartId}/items`,
      JSON.stringify({ productId: productId, quantity: 1 }),
      { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
    );
    if (added.status !== 200 && added.status !== 201) {
      throw new Error(`S3: cannot seed cart ${cartId} - HTTP ${added.status} ${added.body}`);
    }
    carts.push(cartId);
  }
  console.log(`S3 setup: created product ${productId} (stock=1) with ${carts.length} carts ready`);
  return { productId: productId, carts: carts };
}

export default function (data) {
  const cartId = data.carts[(__VU - 1) % data.carts.length];
  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ cartId: cartId, customerRef: `s3-buyer-${__VU}` }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '30s',
      tags: { op: 'order_checkout' },
      // 201 (success) or 409 (out of stock) are both expected business outcomes
      responseCallback: http.expectedStatuses(201, 409),
    }
  );

  if (res.status === 201) {
    created.add(1);
  } else if (res.status === 409) {
    rejected.add(1);
  } else {
    unexpected.add(1);
  }
  orderMs.add(res.timings.duration);

  check(res, { 'POST /api/orders -> 201 (winner) or 409 (out of stock)': (r) => r.status === 201 || r.status === 409 });
}

export function teardown(data) {
  const res = http.get(`${BASE}/api/products/${data.productId}`, { timeout: '15s' });
  if (res.status !== 200) {
    console.error(`S3: cannot read the product back - HTTP ${res.status}`);
    return;
  }
  const stock = res.json().stock;
  stockAfter.add(stock);
  console.log(`S3 result: ${ORDER_COUNT} parallel orders for stock=1 left stock=${stock} (must be 0)`);
}
