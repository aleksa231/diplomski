import { Suspense, lazy, useEffect, useState } from 'react';
import { LayoutDashboard, ShoppingBag, Store, Sun } from 'lucide-react';
import ShopPage from './pages/Shop';
import { Link, usePath } from './router';
import { totalQuantity, useCartQuery } from './shop/cart';

// The shop catalog is the entry route and stays in the main chunk; the admin console (charts) and
// the checkout/detail routes are loaded on demand so the first paint does not ship them.
const AdminPage = lazy(() => import('./pages/Admin'));
const CartPage = lazy(() => import('./pages/Cart'));
const ProductDetailPage = lazy(() => import('./pages/ProductDetail'));

function RouteFallback() {
  return (
    <p className="small muted" role="status">
      Učitavanje…
    </p>
  );
}

/** The only dynamic segment in the app: {@code /product/<id>}. */
function productIdFrom(path: string): string | null {
  const match = /^\/product\/([^/]+)\/?$/.exec(path);
  return match ? decodeURIComponent(match[1]) : null;
}

function CartLink({ active }: { active: boolean }) {
  const cartQuery = useCartQuery();
  const count = totalQuantity(cartQuery.data?.data.items);

  return (
    <Link to="/cart" className={active ? 'shop-nav-link shop-nav-link-active' : 'shop-nav-link'}>
      <ShoppingBag size={16} />
      <span>Korpa</span>
      {count > 0 ? <span className="cart-badge">{count}</span> : null}
    </Link>
  );
}

export default function App() {
  const path = usePath();
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    try {
      return (localStorage.getItem('km-theme') as 'dark' | 'light') || 'dark';
    } catch {
      return 'dark';
    }
  });
  useEffect(() => {
    document.documentElement.classList.toggle('light', theme === 'light');
    try { localStorage.setItem('km-theme', theme); } catch {}
  }, [theme]);
  const productId = productIdFrom(path);
  const isAdmin = path === '/admin' || path.startsWith('/admin/');
  const catalogActive = path === '/' || path.startsWith('/product/');

  return (
    <div className="layout">
      <header className="shop-header">
        <div className="shop-header-inner">
          <Link to="/" className="shop-logo">
            <span className="shop-logo-mark">
              <Store size={18} />
            </span>
            <span className="shop-logo-text">
              <strong>KlikMarket</strong>
            </span>
          </Link>

          <nav className="shop-nav" aria-label="Glavna navigacija">
            <Link
              to="/"
              className={catalogActive ? 'shop-nav-link shop-nav-link-active' : 'shop-nav-link'}
            >
              Katalog
            </Link>
            <CartLink active={path === '/cart'} />
            <Link
              to="/admin"
              className={
                isAdmin ? 'shop-nav-link shop-nav-admin shop-nav-link-active' : 'shop-nav-link shop-nav-admin'
              }
            >
              <LayoutDashboard size={16} /> Admin
            </Link>
            <button
              type="button"
              className="shop-nav-link theme-toggle-btn"
              onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
              aria-label={theme === 'dark' ? 'Prebaci na svijetli režim' : 'Prebaci na tamni režim'}
              title={theme === 'dark' ? 'Svijetli režim' : 'Tamni režim'}
            >
              <Sun size={16} />
              <span className="small">Tema</span>
            </button>
          </nav>
        </div>
      </header>

      <main className="app-main shop-main">
        <div className="container">
          <Suspense fallback={<RouteFallback />}>
            {isAdmin ? (
              <AdminPage />
            ) : productId ? (
              <ProductDetailPage id={productId} />
            ) : path === '/cart' ? (
              <CartPage />
            ) : (
              <ShopPage />
            )}
          </Suspense>
        </div>
      </main>

      <footer className="shop-footer">
        <div className="shop-footer-inner">
          <span>© {new Date().getFullYear()} KlikMarket</span>
          <span>Kvalitetni proizvodi · Brza dostava · Povoljne cijene</span>
        </div>
      </footer>
    </div>
  );
}
