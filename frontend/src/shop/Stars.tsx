import { Star } from 'lucide-react';
import { decimal } from './labels';

export function Stars({
  rating,
  count,
  size = 14,
}: {
  rating: number | null | undefined;
  count?: number | null;
  size?: number;
}) {
  const value = Math.max(0, Math.min(5, rating ?? 0));
  return (
    <span className="stars" title={rating ? `${decimal(rating, 1)} / 5` : 'Bez ocjene'}>
      {[1, 2, 3, 4, 5].map((step) => {
        const filled = step <= Math.round(value);
        return (
          <Star
            key={step}
            size={size}
            className={filled ? 'star star-on' : 'star'}
            fill={filled ? 'currentColor' : 'none'}
          />
        );
      })}
      {count !== undefined && count !== null ? <span className="stars-count">({count})</span> : null}
    </span>
  );
}
