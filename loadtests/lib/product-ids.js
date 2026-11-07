import http from 'k6/http';

/**
 * Loads real product ids from the target API (paginated). Falls back to synthetic ids only when
 * the catalog is unreachable so setup never crashes; synthetic ids produce 404s and invalidate runs.
 */
export function loadProductIds(baseUrl, poolSize) {
  const want = Math.min(Math.max(poolSize, 20), 500);
  const ids = [];
  const pageSize = 100;
  let page = 0;

  while (ids.length < want) {
    const res = http.get(`${baseUrl}/api/products?page=${page}&size=${pageSize}`, {
      timeout: '30s',
    });
    if (res.status !== 200) {
      break;
    }
    let content = [];
    try {
      const body = res.json();
      content = body && body.content ? body.content : [];
    } catch (e) {
      break;
    }
    if (content.length === 0) {
      break;
    }
    for (const p of content) {
      if (p && p.id) {
        ids.push(p.id);
      }
    }
    page += 1;
    if (content.length < pageSize) {
      break;
    }
  }

  if (ids.length === 0) {
    for (let i = 0; i < want; i++) {
      ids.push(`synthetic-${i}`);
    }
  }
  return ids;
}
