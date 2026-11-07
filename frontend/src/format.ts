import type { AggregateStats, RunStatus } from './api/types';

/** Number formatting shared by every page, so "—" always means "not measured". */
export function num(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return value.toFixed(digits);
}

export function int(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return String(Math.round(value));
}

/** {@code mean ± stdDev}, the form required for the aggregated tables. */
export function meanStd(stats: AggregateStats | undefined, digits = 1): string {
  if (!stats) {
    return '—';
  }
  return `${num(stats.mean, digits)} ± ${num(stats.stdDev, digits)}`;
}

/** k6 reports error rates as a 0..1 fraction; the console shows percentages. */
export function percent(fraction: number | null | undefined, digits = 2): string {
  if (fraction === null || fraction === undefined || Number.isNaN(fraction)) {
    return '—';
  }
  return `${(fraction * 100).toFixed(digits)}%`;
}

export function timestamp(value: string | null | undefined): string {
  if (!value) {
    return '—';
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
