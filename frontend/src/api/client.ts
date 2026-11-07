import type {
  CartResponse,
  ErrorResponse,
  ExperimentRequest,
  ExperimentRun,
  OrderResponse,
  PageOrderResponse,
  PageProductResponse,
  ProductDto,
} from './types';

/**
 * The runner is always addressed through {@code /api/runner}, which nginx and the Vite dev proxy
 * both rewrite to the runner's own {@code /api}. Using one prefix keeps dev and container
 * behaviour identical.
 */
export const RUNNER = '/api/runner';

export type Backend = 'blocking' | 'reactive' | 'relational';

const BACKEND_PREFIX: Record<Backend, string> = {
  blocking: '/api/blocking',
  reactive: '/api/reactive',
  relational: '/api/relational',
};

/**
 * The storefront reads and writes through the blocking service by default. It is the reference
 * implementation of the contract; the other two are benchmark targets and are exercised from the
 * admin console. Override with VITE_SHOP_BACKEND when a different service should back the shop.
 */
export const SHOP_BACKEND: Backend = (import.meta.env.VITE_SHOP_BACKEND as Backend | undefined) ?? 'blocking';

/** A failed call, carrying the HTTP status so the UI can tell "down" from "bad request". */
export class ApiRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

/** Payload plus the wall-clock time the call took, which the console displays. */
export interface Timed<T> {
  data: T;
  latencyMs: number;
}

export async function request<T>(url: string, init?: RequestInit): Promise<Timed<T>> {
  const started = performance.now();
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      },
    });
  } catch {
    throw new ApiRequestError(`Servis nije dostupan (${url})`, 0);
  }
  const latencyMs = performance.now() - started;
  const text = await response.text();
  if (!response.ok) {
    throw new ApiRequestError(describeError(text, response.status), response.status);
  }
  return { data: (text ? JSON.parse(text) : undefined) as T, latencyMs };
}

function describeError(body: string, status: number): string {
  try {
    const parsed = JSON.parse(body) as ErrorResponse;
    if (parsed && parsed.message) {
      return `${status} · ${parsed.code}: ${parsed.message}`;
    }
  } catch {
    // The body was not the shared error model; fall through to the bare status.
  }
  return `HTTP ${status}`;
}

/* ---------- benchmark runner ---------- */

export function startExperiment(body: ExperimentRequest): Promise<Timed<ExperimentRun>> {
  return request<ExperimentRun>(`${RUNNER}/experiments`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function fetchRuns(): Promise<Timed<ExperimentRun[]>> {
  return request<ExperimentRun[]>(`${RUNNER}/experiments`);
}

export function fetchRun(id: string): Promise<Timed<ExperimentRun>> {
  return request<ExperimentRun>(`${RUNNER}/experiments/${id}`);
}

/** Downloads the persisted result document, exactly as written into {@code results/}. */
export async function exportRunResult(runId: string, scenario: string): Promise<void> {
  const response = await fetch(`${RUNNER}/experiments/${runId}/result`);
  if (!response.ok) {
    const text = await response.text();
    throw new ApiRequestError(describeError(text, response.status), response.status);
  }
  const text = await response.text();
  const objectUrl = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = `${scenario.toLowerCase()}-${runId}.json`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}

/* ---------- catalog ---------- */

export function fetchProducts(backend: Backend, params: URLSearchParams): Promise<Timed<PageProductResponse>> {
  return request<PageProductResponse>(`${BACKEND_PREFIX[backend]}/products?${params.toString()}`);
}

export function searchProducts(
  backend: Backend,
  query: string,
  page: number,
  size: number,
): Promise<Timed<PageProductResponse>> {
  const params = new URLSearchParams({ q: query, page: String(page), size: String(size) });
  return request<PageProductResponse>(`${BACKEND_PREFIX[backend]}/products/search?${params.toString()}`);
}

export function fetchProduct(backend: Backend, id: string): Promise<Timed<ProductDto>> {
  return request<ProductDto>(`${BACKEND_PREFIX[backend]}/products/${encodeURIComponent(id)}`);
}

export function addProductReview(
  backend: Backend,
  id: string,
  body: { rating: number; text: string },
): Promise<Timed<ProductDto>> {
  return request<ProductDto>(`${BACKEND_PREFIX[backend]}/products/${encodeURIComponent(id)}/reviews`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/* ---------- cart & orders ---------- */

export function fetchCart(backend: Backend, cartId: string): Promise<Timed<CartResponse>> {
  return request<CartResponse>(`${BACKEND_PREFIX[backend]}/carts/${encodeURIComponent(cartId)}/items`);
}

export function addCartItem(
  backend: Backend,
  cartId: string,
  productId: string,
  quantity: number,
): Promise<Timed<CartResponse>> {
  return request<CartResponse>(`${BACKEND_PREFIX[backend]}/carts/${encodeURIComponent(cartId)}/items`, {
    method: 'POST',
    body: JSON.stringify({ productId, quantity }),
  });
}

export function updateCartItem(
  backend: Backend,
  cartId: string,
  productId: string,
  quantity: number,
): Promise<Timed<CartResponse>> {
  return request<CartResponse>(`${BACKEND_PREFIX[backend]}/carts/${encodeURIComponent(cartId)}/items`, {
    method: 'PATCH',
    body: JSON.stringify({ productId, quantity }),
  });
}

export function removeCartItem(
  backend: Backend,
  cartId: string,
  productId: string,
): Promise<Timed<CartResponse>> {
  const params = new URLSearchParams({ productId });
  return request<CartResponse>(
    `${BACKEND_PREFIX[backend]}/carts/${encodeURIComponent(cartId)}/items?${params.toString()}`,
    { method: 'DELETE' },
  );
}

export function clearCart(backend: Backend, cartId: string): Promise<Timed<CartResponse>> {
  return request<CartResponse>(`${BACKEND_PREFIX[backend]}/carts/${encodeURIComponent(cartId)}/items`, {
    method: 'DELETE',
  });
}

export function createOrder(
  backend: Backend,
  cartId: string,
  customerName: string,
  customerEmail: string,
): Promise<Timed<OrderResponse>> {
  const composed = `${customerName.trim()} <${customerEmail.trim()}>`;
  return request<OrderResponse>(`${BACKEND_PREFIX[backend]}/orders`, {
    method: 'POST',
    body: JSON.stringify({
      cartId,
      customerRef: composed,
      customerName: customerName.trim(),
      customerEmail: customerEmail.trim(),
    }),
  });
}

export function fetchOrders(
  backend: Backend,
  page: number,
  size: number,
  query?: string,
): Promise<Timed<PageOrderResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (query && query.trim()) {
    params.set('q', query.trim());
  }
  return request<PageOrderResponse>(`${BACKEND_PREFIX[backend]}/orders?${params.toString()}`);
}
