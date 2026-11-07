import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { SHOP_BACKEND, fetchOrders } from '../api/client';
import type { OrderResponse } from '../api/types';
import { EmptyState, ErrorBanner } from '../components/Banner';
import { timestamp } from '../format';
import { price } from '../shop/labels';

const PAGE_SIZE = 20;

function customerLabel(order: OrderResponse): string {
  if (order.customerName && order.customerEmail) {
    return `${order.customerName} (${order.customerEmail})`;
  }
  if (order.customerName) {
    return order.customerName;
  }
  if (order.customerEmail) {
    return order.customerEmail;
  }
  return order.customerRef || 'Nepoznat kupac';
}

/** Searchable text across all transaction fields (status intentionally excluded from display). */
function matchesAll(order: OrderResponse, needle: string): boolean {
  if (!needle) return true;
  const q = needle.toLowerCase();
  const parts: string[] = [
    order.id,
    order.customerRef ?? '',
    order.customerName ?? '',
    order.customerEmail ?? '',
    String(order.total ?? ''),
    price(order.total),
    timestamp(order.createdAt),
    order.createdAt ?? '',
  ];
  for (const item of order.items ?? []) {
    parts.push(item.productId, item.productName ?? '', String(item.price ?? ''), String(item.quantity ?? ''));
  }
  return parts.join(' ').toLowerCase().includes(q);
}

export default function TransactionHistory() {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [pageInput, setPageInput] = useState('1');

  const ordersQuery = useQuery({
    queryKey: ['admin-orders', page],
    queryFn: () => fetchOrders(SHOP_BACKEND, page, PAGE_SIZE),
  });

  const data = ordersQuery.data?.data;
  const orders = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);
  const totalElements = data?.totalElements ?? 0;

  useEffect(() => {
    setPageInput(String(page + 1));
  }, [page]);

  // Keep page within bounds if server data changes.
  useEffect(() => {
    if (page > totalPages - 1) setPage(Math.max(0, totalPages - 1));
  }, [page, totalPages]);

  const filtered = useMemo(() => orders.filter((o) => matchesAll(o, query.trim())), [orders, query]);

  function goToPage(event: FormEvent) {
    event.preventDefault();
    const parsed = Number.parseInt(pageInput, 10);
    if (!Number.isFinite(parsed)) {
      setPageInput(String(page + 1));
      return;
    }
    const target = Math.min(totalPages, Math.max(1, parsed));
    setPageInput(String(target));
    setPage(target - 1);
  }

  return (
    <div className="stack" style={{ width: '100%' }}>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, fontSize: '1.05rem' }}>Istorija transakcija</h2>
        <div className="row" style={{ gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="small muted">{totalElements.toLocaleString('sr-Latn-ME')} ukupno</span>
          <input
            className="input"
            type="search"
            placeholder="Filtriraj po svemu (kupac, e-pošta, ID, stavke, iznos, datum)…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Filtriranje transakcija"
            style={{ minWidth: '20rem', maxWidth: '100%', flex: '1 1 20rem' }}
          />
        </div>
      </div>

      {ordersQuery.isError ? (
        <ErrorBanner title="Ne mogu da učitam transakcije">
          {ordersQuery.error instanceof Error ? ordersQuery.error.message : 'Došlo je do greške.'}
        </ErrorBanner>
      ) : null}

      {ordersQuery.isLoading ? (
        <p className="muted small">
          <Loader2 size={14} className="spin" style={{ verticalAlign: 'middle', marginRight: 6 }} />
          Učitavanje transakcija…
        </p>
      ) : filtered.length === 0 ? (
        <EmptyState>
          {query
            ? 'Nema rezultata za unijeti filter na ovoj stranici.'
            : 'Još uvijek nema zabilježenih transakcija.'}
        </EmptyState>
      ) : (
        <>
          <div className="table-wrap tx-table-wrap">
            <table className="table" style={{ width: '100%', maxWidth: 'none', margin: 0 }}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Kupac</th>
                  <th>Stavke</th>
                  <th>Ukupno</th>
                  <th>Datum</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <code>{order.id.length > 12 ? `${order.id.slice(0, 8)}…` : order.id}</code>
                    </td>
                    <td>{customerLabel(order)}</td>
                    <td className="small">
                      {order.items.map((item) => `${item.productName} ×${item.quantity}`).join(', ')}
                    </td>
                    <td className="mono">{price(order.total)}</td>
                    <td className="small muted">{timestamp(order.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav className="pagination" aria-label="Stranice transakcija">
            <div className="pagination-pages">
              <button
                type="button"
                className="btn btn-sm"
                disabled={page === 0 || ordersQuery.isFetching}
                onClick={() => setPage((p) => p - 1)}
              >
                ← Prethodna
              </button>
              <span className="small muted">
                Stranica {data ? data.page + 1 : page + 1} od {totalPages}
              </span>
              <button
                type="button"
                className="btn btn-sm"
                disabled={page >= totalPages - 1 || ordersQuery.isFetching}
                onClick={() => setPage((p) => p + 1)}
              >
                Sljedeća →
              </button>
            </div>

            <form className="page-jump" onSubmit={goToPage} noValidate>
              <label className="small muted" htmlFor="tx-page-jump-input">
                Idi na stranicu
              </label>
              <input
                id="tx-page-jump-input"
                className="input page-jump-input"
                type="number"
                inputMode="numeric"
                min={1}
                max={totalPages}
                value={pageInput}
                onChange={(event) => setPageInput(event.target.value)}
                disabled={!data || ordersQuery.isFetching}
              />
              <button type="submit" className="btn btn-sm" disabled={!data || ordersQuery.isFetching}>
                Idi
              </button>
            </form>
          </nav>
        </>
      )}
    </div>
  );
}
