import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { SHOP_BACKEND, fetchCart } from '../api/client';

/**
 * The cart is anonymous: every browser gets a stable id, and the backend keeps the items under it.
 * Persisting the id means a reload does not lose the basket.
 */
const CART_STORAGE_KEY = 'aurora-market.cart-id';

function randomCartId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `cart-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function getCartId(): string {
  const existing = window.localStorage.getItem(CART_STORAGE_KEY);
  if (existing) {
    return existing;
  }
  const created = randomCartId();
  window.localStorage.setItem(CART_STORAGE_KEY, created);
  return created;
}

export function useCartId(): string {
  return useMemo(getCartId, []);
}

export function useCartQuery() {
  const cartId = useCartId();
  return useQuery({
    queryKey: ['cart', cartId],
    queryFn: () => fetchCart(SHOP_BACKEND, cartId),
  });
}

export function totalQuantity(items: Array<{ quantity: number }> | undefined): number {
  return (items ?? []).reduce((sum, item) => sum + item.quantity, 0);
}
