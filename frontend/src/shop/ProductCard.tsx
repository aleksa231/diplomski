import type { ProductDto } from '../api/types';
import { Link } from '../router';
import { AddToCartButton } from './AddToCartButton';
import { ProductArt } from './ProductArt';
import { Stars } from './Stars';
import { categoryLabel, price } from './labels';

export function ProductCard({ product }: { product: ProductDto }) {
  return (
    <article className="product-card">
      <Link to={`/product/${encodeURIComponent(product.id)}`} className="product-card-link">
        <ProductArt id={product.id} category={product.category} name={product.name} />
        <div className="product-card-body">
          <span className="product-category">{categoryLabel(product.category)}</span>
          <h3 className="product-name">{product.name}</h3>
          <Stars rating={product.ratingAvg} count={product.reviewCount} />
          <div className="spread mt-1">
            <strong className="product-price">{price(product.price)}</strong>
            <span className={product.stock > 0 ? 'stock stock-ok' : 'stock stock-out'}>
              {product.stock > 0 ? `${product.stock} na stanju` : 'nema na stanju'}
            </span>
          </div>
        </div>
      </Link>
      <div className="product-card-footer">
        <AddToCartButton productId={product.id} disabled={product.stock <= 0} compact />
      </div>
    </article>
  );
}
