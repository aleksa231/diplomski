import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Loader2, ShoppingCart } from 'lucide-react';
import { SHOP_BACKEND, addCartItem } from '../api/client';
import { useCartId } from './cart';

export function AddToCartButton({
  productId,
  quantity = 1,
  disabled = false,
  compact = false,
  label = 'Dodaj u korpu',
}: {
  productId: string;
  quantity?: number;
  disabled?: boolean;
  compact?: boolean;
  label?: string;
}) {
  const cartId = useCartId();
  const queryClient = useQueryClient();
  const [added, setAdded] = useState(false);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const mutation = useMutation({
    mutationFn: () => addCartItem(SHOP_BACKEND, cartId, productId, quantity),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cart', cartId] });
      setAdded(true);
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => setAdded(false), 1600);
    },
  });

  return (
    <button
      type="button"
      className={`btn btn-primary btn-add${compact ? ' btn-tiny' : ''}`}
      disabled={disabled || mutation.isPending}
      onClick={() => mutation.mutate()}
    >
      {mutation.isPending ? (
        <Loader2 size={16} className="spin" />
      ) : added ? (
        <Check size={16} />
      ) : (
        <ShoppingCart size={16} />
      )}
      <span>
        {mutation.isPending ? 'Dodajem…' : added ? 'Dodato u korpu' : disabled ? 'Nema na stanju' : label}
      </span>
    </button>
  );
}
