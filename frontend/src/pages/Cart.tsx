import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, Loader2, ShoppingBag, Trash2 } from 'lucide-react';
import {
  ApiRequestError,
  SHOP_BACKEND,
  clearCart,
  createOrder,
  fetchProduct,
  removeCartItem,
  updateCartItem,
} from '../api/client';
import type { OrderResponse, ProductDto } from '../api/types';
import { ErrorBanner } from '../components/Banner';
import { Link } from '../router';
import { ProductArt } from '../shop/ProductArt';
import { totalQuantity, useCartId, useCartQuery } from '../shop/cart';
import { categoryLabel, price } from '../shop/labels';
import { statusLabel } from '../labels';

function errorMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : String(error);
}

export default function CartPage() {
  const cartId = useCartId();
  const queryClient = useQueryClient();
  const cartQuery = useCartQuery();
  const [customerName, setCustomerName] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [order, setOrder] = useState<OrderResponse | null>(null);

  const items = cartQuery.data?.data.items ?? [];
  const productIds = items.map((item) => item.productId).sort();

  const productsQuery = useQuery({
    queryKey: ['cart-products', productIds.join(',')],
    queryFn: async () => {
      const loaded = await Promise.all(
        items.map((item) =>
          fetchProduct(SHOP_BACKEND, item.productId)
            .then((result) => result.data)
            .catch(() => null),
        ),
      );
      return loaded.filter((product): product is ProductDto => product !== null);
    },
    enabled: items.length > 0,
  });

  const products = new Map((productsQuery.data ?? []).map((product) => [product.id, product]));
  const quantity = totalQuantity(items);
  const subtotal = items.reduce(
    (sum, item) => sum + (products.get(item.productId)?.price ?? 0) * item.quantity,
    0,
  );

  function invalidateCart() {
    void queryClient.invalidateQueries({ queryKey: ['cart', cartId] });
    void queryClient.invalidateQueries({ queryKey: ['cart-products'] });
  }

  const updateMutation = useMutation({
    mutationFn: ({ productId, nextQuantity }: { productId: string; nextQuantity: number }) =>
      updateCartItem(SHOP_BACKEND, cartId, productId, nextQuantity),
    onSuccess: invalidateCart,
  });

  const removeMutation = useMutation({
    mutationFn: (productId: string) => removeCartItem(SHOP_BACKEND, cartId, productId),
    onSuccess: invalidateCart,
  });

  const clearMutation = useMutation({
    mutationFn: () => clearCart(SHOP_BACKEND, cartId),
    onSuccess: invalidateCart,
  });

  const orderMutation = useMutation({
    mutationFn: () => createOrder(SHOP_BACKEND, cartId, customerName, customerEmail),
    onSuccess: (result) => {
      setOrder(result.data);
      setCustomerName('');
      setCustomerEmail('');
      invalidateCart();
      void queryClient.invalidateQueries({ queryKey: ['shop-products'] });
      void queryClient.invalidateQueries({ queryKey: ['shop-product'] });
    },
  });

  function submitOrder(event: FormEvent) {
    event.preventDefault();
    orderMutation.mutate();
  }

  if (order) {
    return (
      <div className="stack">
        <section className="card order-success">
          <CheckCircle2 size={48} className="order-success-icon" />
          <h1>Porudžbina je primljena</h1>
          <p className="muted">
            Hvala, {order.customerName || order.customerRef}. Broj porudžbine je <strong className="mono">{order.id}</strong>.
          </p>
          <div className="order-lines">
            {order.items.map((item) => (
              <div className="summary-row" key={item.productId}>
                <span>
                  {item.productName} × {item.quantity}
                </span>
                <span>{price(item.price * item.quantity)}</span>
              </div>
            ))}
          </div>
          <div className="summary-total">
            <span>Ukupno</span>
            <strong>{price(order.total)}</strong>
          </div>
          <div className="row mt-2">
            <span className="badge badge-ok">{statusLabel(order.status)}</span>
            <span className="small muted">
              {new Date(order.createdAt).toLocaleString('sr-Latn-RS')}
            </span>
          </div>
          <div className="row mt-2">
            <Link to="/" className="btn btn-primary">
              Nastavi kupovinu
            </Link>
          </div>
        </section>
      </div>
    );
  }

  if (cartQuery.isError) {
    return (
      <div className="stack">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Nazad na katalog
        </Link>
        <ErrorBanner title="Korpa nije dostupna">{errorMessage(cartQuery.error)}</ErrorBanner>
      </div>
    );
  }

  if (cartQuery.isLoading) {
    return (
      <div className="stack">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Nazad na katalog
        </Link>
        <div className="card">
          <div className="row">
            <Loader2 size={18} className="spin" />
            <span className="muted">Učitavam korpu…</span>
          </div>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="stack">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Nazad na katalog
        </Link>
        <div className="empty-panel">
          <ShoppingBag size={40} />
          <h2>Korpa je prazna</h2>
          <p className="muted">Dodaj proizvode iz kataloga i vrati se ovdje da završiš kupovinu.</p>
          <Link to="/" className="btn btn-primary">
            Istraži katalog
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="stack">
      <Link to="/" className="back-link">
        <ArrowLeft size={16} /> Nazad na katalog
      </Link>

      <div className="spread">
        <div>
          <h1 className="page-title">Tvoja korpa</h1>
          <p className="small muted">
            {quantity} {quantity === 1 ? 'artikal' : 'artikala'} u korpi
          </p>
        </div>
        <button
          type="button"
          className="btn"
          onClick={() => clearMutation.mutate()}
          disabled={clearMutation.isPending}
        >
          <Trash2 size={15} /> Isprazni korpu
        </button>
      </div>

      <div className="cart-layout">
        <section className="card cart-items">
          {items.map((item) => {
            const product = products.get(item.productId);
            const lineTotal = product ? product.price * item.quantity : null;
            const busy =
              (updateMutation.isPending &&
                updateMutation.variables?.productId === item.productId) ||
              (removeMutation.isPending && removeMutation.variables === item.productId);

            return (
              <article className="cart-line" key={item.productId}>
                <Link to={`/product/${encodeURIComponent(item.productId)}`}>
                  <ProductArt
                    id={item.productId}
                    category={product?.category ?? ''}
                    name={product?.name}
                    size="sm"
                  />
                </Link>
                <div>
                  <Link to={`/product/${encodeURIComponent(item.productId)}`}>
                    <strong>{product?.name ?? 'Artikal više nije dostupan'}</strong>
                  </Link>
                  <div className="small muted">
                    {product ? categoryLabel(product.category) : item.productId}
                  </div>
                  <div className="small muted">
                    {product ? `${price(product.price)} po komadu` : 'Cijena nije dostupna'}
                  </div>
                  <div className="row mt-1">
                    <div className="qty">
                      <button
                        type="button"
                        disabled={busy || item.quantity <= 1}
                        onClick={() =>
                          updateMutation.mutate({
                            productId: item.productId,
                            nextQuantity: item.quantity - 1,
                          })
                        }
                        aria-label="Smanji količinu"
                      >
                        −
                      </button>
                      <span>{item.quantity}</span>
                      <button
                        type="button"
                        disabled={busy || (product !== undefined && item.quantity >= product.stock)}
                        onClick={() =>
                          updateMutation.mutate({
                            productId: item.productId,
                            nextQuantity: item.quantity + 1,
                          })
                        }
                        aria-label="Povećaj količinu"
                      >
                        +
                      </button>
                    </div>
                    <button
                      type="button"
                      className="btn btn-tiny"
                      disabled={busy}
                      onClick={() => removeMutation.mutate(item.productId)}
                    >
                      Ukloni
                    </button>
                  </div>
                </div>
                <strong>{lineTotal === null ? '—' : price(lineTotal)}</strong>
              </article>
            );
          })}

          {productsQuery.isError ? (
            <p className="small stock-out mt-2">
              Detalji nekih artikala nisu dostupni: {errorMessage(productsQuery.error)}
            </p>
          ) : null}
        </section>

        <aside className="card cart-summary">
          <h2 className="card-title">Pregled porudžbine</h2>
          <div className="summary-row">
            <span>Artikala</span>
            <span>{quantity}</span>
          </div>
          <div className="summary-row">
            <span>Međusum</span>
            <span>{price(subtotal)}</span>
          </div>
          <div className="summary-row">
            <span>Dostava</span>
            <span className="stock-ok">Besplatno</span>
          </div>
          <div className="summary-total">
            <span>Ukupno</span>
            <strong>{price(subtotal)}</strong>
          </div>

          <form onSubmit={submitOrder}>
            <label className="field mt-2">
              <span>Ime i prezime</span>
              <input
                className="input"
                value={customerName}
                onChange={(event) => setCustomerName(event.target.value)}
                placeholder="Ana Anić"
                required
              />
            </label>
            <label className="field mt-2">
              <span>E-mail</span>
              <input
                className="input"
                type="email"
                value={customerEmail}
                onChange={(event) => setCustomerEmail(event.target.value)}
                placeholder="ana@example.com"
                required
              />
            </label>
            <button
              type="submit"
              className="btn btn-primary btn-block mt-2"
              disabled={
                orderMutation.isPending ||
                customerName.trim() === '' ||
                customerEmail.trim() === '' ||
                !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail.trim())
              }
            >
              {orderMutation.isPending ? <Loader2 size={16} className="spin" /> : <CheckCircle2 size={16} />}
              {orderMutation.isPending ? 'Šaljem porudžbinu…' : 'Završi kupovinu'}
            </button>
          </form>

          {orderMutation.isError ? (
            <div className="mt-2">
              <ErrorBanner title="Porudžbina nije prihvaćena">
                {errorMessage(orderMutation.error)}
              </ErrorBanner>
            </div>
          ) : null}

          <p className="small muted mt-2">
            Narud?bina se upisuje u bazu podataka, a stanje proizvoda se umanjuje u istoj transakciji.
          </p>
        </aside>
      </div>
    </div>
  );
}
