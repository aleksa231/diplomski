import { useEffect, useRef, useState } from 'react';
import type { FormEvent, RefObject } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowRight, PackageSearch, Search, X } from 'lucide-react';
import { ApiRequestError, SHOP_BACKEND, fetchProducts, searchProducts } from '../api/client';
import { EmptyState, ErrorBanner } from '../components/Banner';
import { ProductArt } from '../shop/ProductArt';
import { ProductCard } from '../shop/ProductCard';
import { SHOP_CATEGORIES } from '../shop/labels';

/** Catalog pages are always full rows: the page size follows the grid's real column count. */
const ROWS_PER_PAGE = 3;

function columnCount(container: HTMLElement): number {
  const grid = container.querySelector<HTMLElement>('.product-grid');
  if (grid) {
    const count = getComputedStyle(grid)
      .gridTemplateColumns.split(' ')
      .filter(Boolean).length;
    if (count > 0) {
      return count;
    }
  }
  // The grid is not mounted (empty/error state); mirror the CSS breakpoints closely enough.
  const minCard = window.matchMedia('(max-width: 640px)').matches ? 160 : 230;
  return Math.max(1, Math.floor((container.clientWidth + 20) / (minCard + 20)));
}

function usePageSize(containerRef: RefObject<HTMLElement>): number {
  const [pageSize, setPageSize] = useState(ROWS_PER_PAGE * 4);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return undefined;
    }
    const update = () => {
      const next = columnCount(container) * ROWS_PER_PAGE;
      setPageSize((current) => (current === next ? current : next));
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(container);
    window.addEventListener('resize', update);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', update);
    };
  }, [containerRef]);

  return pageSize;
}

const SORT_OPTIONS = [
  { value: '', label: 'Nasumično' },
  { value: 'price,asc', label: 'Cijena: rastuće' },
  { value: 'price,desc', label: 'Cijena: opadajuće' },
  { value: 'name,asc', label: 'Naziv A–Ž' },
  { value: 'createdAt,desc', label: 'Najnovije' },
];

function buildParams(
  category: string,
  sort: string,
  minPrice: string,
  maxPrice: string,
  page: number,
  pageSize: number,
) {
  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (category) {
    params.set('category', category);
  }
  if (sort) {
    params.set('sort', sort);
  }
  if (minPrice) {
    params.set('minPrice', minPrice);
  }
  if (maxPrice) {
    params.set('maxPrice', maxPrice);
  }
  return params;
}

function errorMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : String(error);
}

export default function ShopPage() {
  const [category, setCategory] = useState('');
  const [sort, setSort] = useState('');
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [searchDraft, setSearchDraft] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [pageInput, setPageInput] = useState('1');

  const catalogRef = useRef<HTMLElement>(null);
  const pageSize = usePageSize(catalogRef);

  // A resize can change the page size, so the old page number may no longer exist.
  useEffect(() => {
    setPage(0);
  }, [pageSize]);

  // Keep the "jump to page" field in sync whenever navigation happens elsewhere.
  useEffect(() => {
    setPageInput(String(page + 1));
  }, [page]);

  // Live search: commit the draft while typing (debounced) so results update letter by letter.
  useEffect(() => {
    const term = searchDraft.trim();
    if (term === search) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setSearch(term);
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [searchDraft, search]);

  const searching = search.length > 0;

  const productsQuery = useQuery({
    queryKey: ['shop-products', search, category, sort, minPrice, maxPrice, page, pageSize],
    queryFn: () =>
      search
        ? searchProducts(SHOP_BACKEND, search, page, pageSize)
        : fetchProducts(SHOP_BACKEND, buildParams(category, sort, minPrice, maxPrice, page, pageSize)),
    placeholderData: keepPreviousData,
  });

  const pageData = productsQuery.data?.data;
  const products = pageData?.content ?? [];
  const hasFilters = Boolean(category || sort || minPrice || maxPrice || search);
  const catalogSize =
    !hasFilters && pageData ? pageData.totalElements : undefined;

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setSearch(searchDraft.trim());
    setPage(0);
  }

  function clearSearch() {
    setSearchDraft('');
    setSearch('');
    setPage(0);
  }

  function resetFilters() {
    setCategory('');
    setSort('');
    setMinPrice('');
    setMaxPrice('');
    setSearchDraft('');
    setSearch('');
    setPage(0);
  }

  function goToPage(event: FormEvent) {
    event.preventDefault();
    const totalPages = Math.max(1, pageData?.totalPages ?? 1);
    const parsed = Number.parseInt(pageInput, 10);
    if (!Number.isFinite(parsed)) {
      setPageInput(String(page + 1));
      return;
    }
    const target = Math.min(totalPages, Math.max(1, parsed));
    setPageInput(String(target));
    setPage(target - 1);
    document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' });
  }

  return (
    <div className="shop-page">
      <section className="shop-hero">
        <div className="shop-hero-copy">
          <h1>
            Sve za tvoj <em>digitalni</em> i svakodnevni život.
          </h1>
          <p>
            Laptopi, telefoni, odjeća, namirnice i knjige — pet kategorija iz mjerljivog,
            deterministički generisanog kataloga. Ista ponuda, tri različita backend modela.
          </p>
          <div className="row mt-2">
            <button
              type="button"
              className="btn btn-primary btn-lg"
              onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })}
            >
              Istraži katalog <ArrowRight size={16} />
            </button>
            <span className="small muted">Besplatna dostava · Sigurna kupovina</span>
          </div>
          <div className="shop-hero-stats">
            <div className="stat">
              <strong>{catalogSize !== undefined ? catalogSize.toLocaleString('de-DE') : '5.000'}</strong>
              <span>artikala</span>
            </div>
            <div className="stat">
              <strong>5</strong>
              <span>kategorija</span>
            </div>
            <div className="stat">
              <strong>3</strong>
              <span>backend modela</span>
            </div>
          </div>
        </div>
        <div className="shop-hero-art" aria-hidden="true">
          <div className="hero-float hero-float-a">
            <ProductArt id="hero-laptop" category="laptops" size="sm" />
          </div>
          <div className="hero-float hero-float-b">
            <ProductArt id="hero-phone" category="phones" size="sm" />
          </div>
          <div className="hero-float hero-float-c">
            <ProductArt id="hero-book" category="books" size="sm" />
          </div>
          <div className="hero-float hero-float-d">
            <ProductArt id="hero-shirt" category="clothing" size="sm" />
          </div>
        </div>
      </section>

      <section id="catalog" className="shop-catalog" ref={catalogRef}>
        <div className="shop-catalog-head">
          <div>
            <h2>Katalog proizvoda</h2>
            <p className="small muted">
              {searching
                ? `Rezultati pretrage za „${search}”`
                : 'Filtriraj po kategoriji, cijeni i redoslijedu.'}
            </p>
          </div>
          {pageData ? (
            <span className="small muted">
              {pageData.totalElements.toLocaleString('de-DE')} rezultata · stranica{' '}
              {pageData.page + 1}/{Math.max(1, pageData.totalPages)}
            </span>
          ) : null}
        </div>

        <form className="search-form" onSubmit={submitSearch} role="search">
          <Search size={18} className="search-icon" />
          <input
            className="input search-input"
            type="search"
            placeholder="Pretraži po nazivu, npr. Laptop Aurora…"
            value={searchDraft}
            onChange={(event) => setSearchDraft(event.target.value)}
            aria-label="Pretraga proizvoda"
          />
          {searching ? (
            <button type="button" className="btn" onClick={clearSearch}>
              <X size={15} /> Očisti
            </button>
          ) : null}
          <button type="submit" className="btn btn-primary">
            Pretraži
          </button>
        </form>

        {!searching ? (
          <div className="shop-filters">
            <div className="category-chips">
              <button
                type="button"
                className={category === '' ? 'category-chip category-chip-active' : 'category-chip'}
                onClick={() => {
                  setCategory('');
                  setPage(0);
                }}
              >
                Sve
              </button>
              {SHOP_CATEGORIES.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  className={category === item.value ? 'category-chip category-chip-active' : 'category-chip'}
                  onClick={() => {
                    setCategory(item.value);
                    setPage(0);
                  }}
                >
                  {item.label}
                </button>
              ))}
            </div>

            <div className="shop-controls">
              <label className="field">
                <span>Redoslijed</span>
                <select
                  className="input"
                  value={sort}
                  onChange={(event) => {
                    setSort(event.target.value);
                    setPage(0);
                  }}
                >
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Min cijena (€)</span>
                <input
                  className="input"
                  type="number"
                  min={0}
                  value={minPrice}
                  onChange={(event) => {
                    setMinPrice(event.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <label className="field">
                <span>Max cijena (€)</span>
                <input
                  className="input"
                  type="number"
                  min={0}
                  value={maxPrice}
                  onChange={(event) => {
                    setMaxPrice(event.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <button type="button" className="btn" onClick={resetFilters} disabled={!hasFilters}>
                Resetuj filtere
              </button>
            </div>
          </div>
        ) : null}

        {productsQuery.isError ? (
          <ErrorBanner title="Katalog trenutno nije dostupan">
            {errorMessage(productsQuery.error)}. Provjeri da li je backend servis pokrenut.
          </ErrorBanner>
        ) : productsQuery.isLoading ? (
          <div className="product-grid">
            {Array.from({ length: pageSize }, (_, index) => (
              <div className="product-card skeleton-card" key={index}>
                <div className="skeleton skeleton-art" />
                <div className="product-card-body">
                  <div className="skeleton skeleton-line" />
                  <div className="skeleton skeleton-line short" />
                </div>
              </div>
            ))}
          </div>
        ) : products.length === 0 ? (
          <div className="empty-panel">
            <PackageSearch size={36} />
            <EmptyState>
              {searching
                ? `Nema proizvoda koji odgovaraju pretrazi „${search}”.`
                : 'Nema proizvoda za izabrane filtere.'}
            </EmptyState>
            {hasFilters ? (
              <button type="button" className="btn" onClick={resetFilters}>
                Resetuj filtere
              </button>
            ) : null}
          </div>
        ) : (
          <>
            <div className="product-grid">
              {products.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>

            <nav className="pagination" aria-label="Stranice kataloga">
              <div className="pagination-pages">
                <button
                  type="button"
                  className="btn"
                  disabled={page === 0 || productsQuery.isFetching}
                  onClick={() => {
                    setPage((current) => Math.max(0, current - 1));
                    document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' });
                  }}
                >
                  Prethodna
                </button>
                <span className="small muted">
                  Stranica {pageData ? pageData.page + 1 : page + 1} od{' '}
                  {Math.max(1, pageData?.totalPages ?? 1)}
                </span>
                <button
                  type="button"
                  className="btn"
                  disabled={!pageData || pageData.page + 1 >= pageData.totalPages || productsQuery.isFetching}
                  onClick={() => {
                    setPage((current) => current + 1);
                    document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' });
                  }}
                >
                  Sljedeća
                </button>
              </div>

              <form className="page-jump" onSubmit={goToPage} noValidate>
                <label className="small muted" htmlFor="page-jump-input">
                  Idi na stranicu
                </label>
                <input
                  id="page-jump-input"
                  className="input page-jump-input"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={Math.max(1, pageData?.totalPages ?? 1)}
                  value={pageInput}
                  onChange={(event) => setPageInput(event.target.value)}
                  disabled={!pageData || productsQuery.isFetching}
                />
                <button
                  type="submit"
                  className="btn"
                  disabled={!pageData || productsQuery.isFetching}
                >
                  Idi
                </button>
              </form>
            </nav>
          </>
        )}
      </section>
    </div>
  );
}
