import type { AggregateStats, RunStatus } from './api/types';

/** Number formatting shared by every page, so "-" always means "not measured". */
export function num(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(digits);
}

export function int(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  return String(Math.round(value));
}

/**
 * Throughput is often far below one request per second (the heavy reports), where rounding to an
 * integer would show 0 and make a measured run look empty. Keep enough decimals to stay readable.
 */
export function rate(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  const abs = Math.abs(value);
  if (abs >= 100) {
    return value.toFixed(0);
  }
  if (abs >= 10) {
    return value.toFixed(1);
  }
  return value.toFixed(2);
}

/** Same adaptive precision for the "mean ± stdDev" throughput cells. */
export function rateStd(stats: AggregateStats | undefined): string {
  if (!stats) {
    return '-';
  }
  return `${rate(stats.mean)} ± ${rate(stats.stdDev)}`;
}

/** {@code mean ± stdDev}, the form required for the aggregated tables. */
export function meanStd(stats: AggregateStats | undefined, digits = 1): string {
  if (!stats) {
    return '-';
  }
  return `${num(stats.mean, digits)} ± ${num(stats.stdDev, digits)}`;
}

/** k6 reports error rates as a 0..1 fraction; the console shows percentages. */
export function percent(fraction: number | null | undefined, digits = 2): string {
  if (fraction === null || fraction === undefined || Number.isNaN(fraction)) {
    return '-';
  }
  return `${(fraction * 100).toFixed(digits)}%`;
}

export function timestamp(value: string | null | undefined): string {
  if (!value) {
    return '-';
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

export function isActive(status: RunStatus | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING';
}

export function statusClass(status: RunStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'badge badge-ok';
    case 'RUNNING':
      return 'badge badge-info';
    case 'PENDING':
      return 'badge badge-muted';
    case 'REJECTED':
      return 'badge badge-warn';
    default:
      return 'badge badge-error';
  }
}
