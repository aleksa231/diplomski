/*
 * S6e - Last-item concurrency.
 *
 * The invariant from spec section 7: with stock = 1 and N parallel order requests for that
 * product, exactly one succeeds, the other N-1 get 409, and the stock never goes negative. This
 * must hold on all three services; run it once per target and compare.
 *
 * Shape of the test:
 *   setup()    creates one product with stock = 1 and one cart per order, each holding a single
 *              unit of it, so every order is a genuine competitor for the same stock unit.
 *   default()  runs exactly once per VU (shared-iterations, iterations = vus) so the orders really
 *              are concurrent rather than sequenced.
 *   teardown() reads the product back and records the remaining stock.
 *
 * The invariant is asserted with thresholds, so a violation fails the k6 run (the runner keeps the
 * measurement and logs the failure) instead of being silently averaged away:
 *   order_created == 1, order_rejected == N-1, stock_after == 0, every response 201 or 409.
 *
 * The order count is fixed at ORDERS (default 100, as the spec prescribes) and is deliberately NOT
 * taken from CLIENTS: S6 runs the failover and redis-down targets with a much lower VU count, and
 * this invariant must still be probed with 100 parallel buyers.
 *
 * Env vars: TARGET_URL, SCENARIO, ORDERS (default 100).
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Gauge, Trend } from 'k6/metrics';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S6';
const ORDER_COUNT = Number(__ENV.ORDERS || 100);

export const options = {
  scenarios: {
    last_item: {
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
  // The carts must be unique per invocation. A cart keeps its lines after a failed order (only the
  // winning order consumes its cart), and the cart lives in Redis with a TTL, so reusing
  // `s6e-cart-${i}` let a line for a product that was already sold out survive into the next
  // repetition/run. Every order for such a cart then fails the stock check, which makes the
  // "exactly one winner" invariant flaky for reasons that have nothing to do with concurrency.
  const runId = Date.now().toString(36) + '-' + Math.floor(Math.random() * 1e9).toString(36);
  const product = {
    sku: `s6e-${runId}`,
    name: 'S6e last item',
    category: 'laptops',
    price: 999.0,
    stock: 1,
  };
  const createRes = http.post(`${BASE}/api/products`, JSON.stringify(product), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });
  if (createRes.status !== 201) {
    throw new Error(`S6e: cannot create the single-stock product - HTTP ${createRes.status} ${createRes.body}`);
  }
  const productId = createRes.json().id;

  const carts = [];
  for (let i = 0; i < ORDER_COUNT; i++) {
    const cartId = `s6e-cart-${runId}-${i}`;
    const added = http.post(
      `${BASE}/api/carts/${cartId}/items`,
      JSON.stringify({ productId: productId, quantity: 1 }),
      { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
    );
    if (added.status !== 200 && added.status !== 201) {
      throw new Error(`S6e: cannot seed cart ${cartId} - HTTP ${added.status} ${added.body}`);
    }
    carts.push(cartId);
  }
  console.log(`S6e setup: product ${productId} with stock=1 and ${carts.length} ready carts`);
  return { productId: productId, carts: carts };
}

export default function (data) {
  const cartId = data.carts[(__VU - 1) % data.carts.length];
  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ cartId: cartId, customerRef: `s6e-${__VU}` }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '30s',
      tags: { op: 'order' },
      // Exactly one 201 and N-1 x 409 is the invariant under test, so the 409 must not count as a
      // failure; the order_created / order_rejected counters keep the outcome fully visible.
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
    console.error(`S6e: cannot read the product back - HTTP ${res.status}`);
    return;
  }
  const stock = res.json().stock;
  stockAfter.add(stock);
  console.log(`S6e result: ${ORDER_COUNT} parallel orders for stock=1 left stock=${stock} (must be 0)`);
}
