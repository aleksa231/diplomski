/*
 * S1 - Schema flexibility & document read/filter.
 *
 * What it measures: the catalog read path shared by every service over one REST contract.
 *   - 60%: GET /api/products/{id}          (single document / row read by primary key)
 *   - 40%: GET /api/products?category=&page=&size=&sort=&attr.<name>=<value>
 *          (filtered, paginated list query, incl. dynamic attribute filters)
 *   The dynamic-filter parameter syntax is `attr.<attributeName>=<value>` (see
 *   contract/openapi.yaml and nosql-blocking CatalogQueryService/ProductController).
 *
 * Env vars: TARGET_URL (base url), CLIENTS (vus), DURATION_SEC, DATASET_SIZE (id-pool size),
 *           SCENARIO (tag only).
 *
 * Good result: low p95 for by-id reads, filtered queries not much slower, 0 unexpected
 * 5xx and only a few 404s from the synthetic fallback pool.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { loadProductIds } from '../lib/product-ids.js';

const BASE = __ENV.TARGET_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'S1';

const CATEGORIES = ['laptops', 'phones', 'clothing', 'groceries', 'books'];
// Dynamic attribute filters aligned with docs/data-model.md / the seeder.
const ATTR_FILTERS = [
  'attr.ramGb=16',
  'attr.ramGb=8',
  'attr.fiveG=true',
  'attr.organic=true',
  'attr.size=M',
  'attr.color=black',
  'attr.os=iOS',
  'attr.genre=Sci-Fi',
  'attr.material=cotton',
  'attr.language=English',
];
const SORTS = ['price,asc', 'price,desc', 'name,asc', 'createdAt,desc'];

export const options = {
  vus: Number(__ENV.CLIENTS || 20),
  duration: (Number(__ENV.DURATION_SEC || 30)) + 's',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    catalog_read_ms: ['p(95)<500'],
    catalog_query_ms: ['p(95)<1000'],
  },
  tags: { scenario: SCENARIO, target: BASE },
};

const byIdTrend = new Trend('catalog_read_ms', true);
const queryTrend = new Trend('catalog_query_ms', true);
const notFound = new Counter('product_not_found');
const readRate = new Rate('catalog_read_ok');
const queryRate = new Rate('catalog_query_ok');

// Deterministic per-VU RNG so the request mix is reproducible across runs.
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

function buildQuery(rng) {
  const parts = [];
  if (rng() < 0.5) parts.push('category=' + encodeURIComponent(pick(CATEGORIES, rng)));
  if (rng() < 0.6) parts.push(pick(ATTR_FILTERS, rng));
  if (rng() < 0.3) {
    const lo = Math.floor(rng() * 500);
    parts.push('minPrice=' + lo, 'maxPrice=' + (lo + 500));
  }
  parts.push('page=' + Math.floor(rng() * 3));
  parts.push('size=' + (10 + Math.floor(rng() * 3) * 10));
  if (rng() < 0.5) parts.push('sort=' + encodeURIComponent(pick(SORTS, rng)));
  return parts.join('&');
}

export function setup() {
  const poolSize = Number(__ENV.DATASET_SIZE || 5000);
  return { ids: loadProductIds(BASE, poolSize) };
}

export default function (data) {
  const rng = makeRng(1000 + __VU * 7919 + __ITER);
  const ids = data.ids;

  if (rng() < 0.6) {
    group('read_by_id', function () {
      const id = pick(ids, rng);
      const res = http.get(`${BASE}/api/products/${id}`, {
        timeout: '5s',
        tags: { op: 'read_by_id' },
      });
      const ok = check(res, { 'GET /api/products/{id} -> 200': (r) => r.status === 200 });
      readRate.add(ok);
      if (res.status === 404) notFound.add(1);
      if (res.status === 200) byIdTrend.add(res.timings.duration);
    });
  } else {
    group('filtered_query', function () {
      const res = http.get(`${BASE}/api/products?${buildQuery(rng)}`, {
        timeout: '10s',
        tags: { op: 'query_filter' },
      });
      const ok = check(res, { 'GET /api/products?... -> 200': (r) => r.status === 200 });
      queryRate.add(ok);
      if (res.status === 200) queryTrend.add(res.timings.duration);
    });
  }

  sleep(0.05 + rng() * 0.15);
}
