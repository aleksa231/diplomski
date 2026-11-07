import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, MessageSquarePlus, RotateCcw, ShieldCheck, Star, Truck } from 'lucide-react';
import { ApiRequestError, SHOP_BACKEND, addProductReview, fetchProduct } from '../api/client';
import { ErrorBanner } from '../components/Banner';
import { AddToCartButton } from '../shop/AddToCartButton';
import { ProductArt, galleryVariantsFor } from '../shop/ProductArt';
import { Stars } from '../shop/Stars';
import { attributeLabel, attributeValue, categoryLabel, decimal, price } from '../shop/labels';
import { Link } from '../router';

function dateLabel(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleDateString('sr-Latn-RS', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function ProductDetailPage({ id }: { id: string }) {
  const [quantity, setQuantity] = useState(1);
  const [rating, setRating] = useState(5);
  const [text, setText] = useState('');
  const [artVariant, setArtVariant] = useState<number | null>(null);
  const queryClient = useQueryClient();

  useEffect(() => {
    setArtVariant(null);
  }, [id]);

  const productQuery = useQuery({
    queryKey: ['shop-product', id],
    queryFn: () => fetchProduct(SHOP_BACKEND, id),
  });

  const reviewMutation = useMutation({
    mutationFn: () => addProductReview(SHOP_BACKEND, id, { rating, text: text.trim() }),
    onSuccess: () => {
      setText('');
      void queryClient.invalidateQueries({ queryKey: ['shop-product', id] });
    },
  });

  const product = productQuery.data?.data;

  function submitReview(event: FormEvent) {
    event.preventDefault();
    reviewMutation.mutate();
  }

  if (productQuery.isError) {
    return (
      <div className="stack">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Nazad na katalog
        </Link>
        <ErrorBanner title="Proizvod nije dostupan">
          {productQuery.error instanceof ApiRequestError
            ? productQuery.error.message
            : String(productQuery.error)}
        </ErrorBanner>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="stack">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Nazad na katalog
        </Link>
        <div className="product-detail card">
          <div className="skeleton skeleton-square" />
          <div className="stack">
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line short" />
            <div className="skeleton skeleton-line" />
          </div>
        </div>
      </div>
    );
  }

  const maxQuantity = Math.max(1, product.stock);
  const attributes = Object.entries(product.attributes ?? {});
  const reviews = product.reviews ?? [];
  const galleryVariants = galleryVariantsFor(product.id, product.category);
  const selectedArt = artVariant ?? galleryVariants[0];
  const keySpecs = attributes
    .slice(0, 2)
    .map(([key, value]) => `${attributeLabel(key)}: ${attributeValue(key, value)}`);
  const description = `${product.name} dolazi iz kategorije ${categoryLabel(product.category)}.${
    keySpecs.length > 0 ? ` Ističe se po sljedećem: ${keySpecs.join(' · ')}.` : ''
  } Svaki primjerak provjeravamo prije slanja, a zaliha se ažurira odmah nakon potvrđene porudžbine.`;

  return (
    <div className="stack">
      <Link to="/" className="back-link">
        <ArrowLeft size={16} /> Nazad na katalog
      </Link>

      <section className="product-detail">
        <div className="product-detail-art">
          <ProductArt
            id={product.id}
            category={product.category}
            name={product.name}
            size="lg"
            variant={selectedArt}
          />

          <div className="product-art-strip" role="group" aria-label="Galerija slika proizvoda">
            {galleryVariants.map((variant, index) => (
              <button
                key={variant}
                type="button"
                className={variant === selectedArt ? 'art-thumb is-selected' : 'art-thumb'}
                onClick={() => setArtVariant(variant)}
                aria-label={`Slika ${index + 1}`}
                aria-pressed={variant === selectedArt}
              >
                <ProductArt
                  id={product.id}
                  category={product.category}
                  name={product.name}
                  size="sm"
                  variant={variant}
                />
              </button>
            ))}
          </div>
        </div>

        <div className="product-detail-info">
          <h1>{product.name}</h1>
          <div className="row">
            <Stars rating={product.ratingAvg} count={product.reviewCount} size={17} />
            <span className="small muted">SKU {product.sku}</span>
          </div>

          <div className="detail-price">{price(product.price)}</div>
          <p className={product.stock > 0 ? 'stock stock-ok' : 'stock stock-out'}>
            {product.stock > 0 ? `Na stanju: ${product.stock} komada` : 'Trenutno nema na stanju'}
          </p>

          <div className="buy-row">
            <div className="qty" aria-label="Količina">
              <button
                type="button"
                onClick={() => setQuantity((current) => Math.max(1, current - 1))}
                disabled={quantity <= 1}
                aria-label="Smanji količinu"
              >
                −
              </button>
              <span>{quantity}</span>
              <button
                type="button"
                onClick={() => setQuantity((current) => Math.min(maxQuantity, current + 1))}
                disabled={quantity >= maxQuantity}
                aria-label="Povećaj količinu"
              >
                +
              </button>
            </div>
            <AddToCartButton
              productId={product.id}
              quantity={quantity}
              disabled={product.stock <= 0}
              label="Dodaj u korpu"
            />
          </div>

          <div className="detail-meta">
            <div className="meta-tile">
              <span>Kategorija</span>
              <strong>{categoryLabel(product.category)}</strong>
            </div>
            <div className="meta-tile">
              <span>Zaliha</span>
              <strong>{product.stock}</strong>
            </div>
            <div className="meta-tile">
              <span>Ocjena</span>
              <strong>{product.ratingAvg ? decimal(product.ratingAvg, 1) : '—'} / 5</strong>
            </div>
            <div className="meta-tile">
              <span>Recenzije</span>
              <strong>{product.reviewCount ?? 0}</strong>
            </div>
          </div>

          <div className="detail-perks">
            <div className="detail-perk">
              <Truck size={16} />
              <div>
                <strong>Besplatna dostava</strong>
                <span>Isporuka na kućnu adresu u roku od 2–4 radna dana.</span>
              </div>
            </div>
            <div className="detail-perk">
              <ShieldCheck size={16} />
              <div>
                <strong>Sigurna kupovina</strong>
                <span>Podaci o plaćanju se obrađuju preko zaštićene veze.</span>
              </div>
            </div>
            <div className="detail-perk">
              <RotateCcw size={16} />
              <div>
                <strong>Povrat u 14 dana</strong>
                <span>Ako ne odgovara, vrati proizvod bez objašnjenja.</span>
              </div>
            </div>
          </div>

          <section className="card detail-card">
            <h2 className="card-title">Karakteristike</h2>
            {attributes.length === 0 ? (
              <p className="muted">Proizvođač nije naveo dodatne karakteristike.</p>
            ) : (
              <div className="attributes">
                {attributes.map(([key, value]) => (
                  <div className="attribute" key={key}>
                    <span>{attributeLabel(key)}</span>
                    <strong>{attributeValue(key, value)}</strong>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="card detail-card">
            <h2 className="card-title">O proizvodu</h2>
            <p className="small muted detail-description">{description}</p>
          </section>
        </div>
      </section>

      <section className="card">
        <div className="spread">
          <h2 className="card-title">Recenzije kupaca</h2>
          <span className="small muted">{product.reviewCount ?? 0} ukupno</span>
        </div>

        {reviews.length === 0 ? (
          <p className="muted">Još nema recenzija za ovaj proizvod. Budi prvi.</p>
        ) : (
          <div className="review-list">
            {reviews.map((review, index) => (
              <article className="review" key={`${review.createdAt}-${index}`}>
                <div className="spread">
                  <Stars rating={review.rating} size={13} />
                  <span className="small muted">{dateLabel(review.createdAt)}</span>
                </div>
                <p className="small mt-1">{review.text || 'Kupac nije ostavio komentar.'}</p>
              </article>
            ))}
          </div>
        )}

        <form className="review-form" onSubmit={submitReview}>
          <strong className="row">
            <MessageSquarePlus size={17} /> Ostavi recenziju
          </strong>

          <div className="review-compose mt-2">
            <label className="field review-comment">
              <span>Komentar (opciono)</span>
              <textarea
                className="input"
                rows={3}
                value={text}
                onChange={(event) => setText(event.target.value)}
                placeholder="Kako ti se dopada proizvod?"
              />
            </label>

            <div className="review-rating">
              <span>Ocjena</span>
              <div className="star-picker" role="radiogroup" aria-label="Ocjena">
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    key={value}
                    type="button"
                    role="radio"
                    aria-checked={rating === value}
                    aria-label={`${value} od 5`}
                    title={`${value} / 5`}
                    className={value <= rating ? 'star-button is-on' : 'star-button'}
                    onClick={() => setRating(value)}
                  >
                    <Star size={22} fill={value <= rating ? 'currentColor' : 'none'} />
                  </button>
                ))}
              </div>
              <span className="small muted">{rating} / 5</span>
            </div>
          </div>

          <div className="row mt-2">
            <button type="submit" className="btn btn-primary" disabled={reviewMutation.isPending}>
              {reviewMutation.isPending ? 'Šaljem…' : 'Pošalji recenziju'}
            </button>
            {reviewMutation.isSuccess ? <span className="small stock-ok">Hvala na recenziji.</span> : null}
            {reviewMutation.isError ? (
              <span className="small stock-out">
                {reviewMutation.error instanceof ApiRequestError
                  ? reviewMutation.error.message
                  : String(reviewMutation.error)}
              </span>
            ) : null}
          </div>
        </form>
      </section>
    </div>
  );
}
