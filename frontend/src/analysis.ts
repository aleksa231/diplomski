import type { ExperimentRun, SampleMetrics, TargetRunResult, TimelinePoint } from './api/types';

/** Colour per compared target; kept stable so a target keeps its colour across charts. */
export const SERIES_COLORS = [
  '#38bdf8',
  '#f472b6',
  '#4ade80',
  '#fbbf24',
  '#a78bfa',
  '#fb7185',
  '#22d3ee',
  '#facc15',
];

/** One measured target of one run: the unit the Results page compares side by side. */
export interface ComparisonRow {
  key: string;
  runId: string;
  scenario: string;
  label: string;
  clients: number;
  error: string | null;
  setup: string[];
  target: TargetRunResult;
}

export interface Series {
  data: Array<Record<string, number | string>>;
  keys: string[];
}

/** Flattens finished runs into comparable rows, labelling each with its scenario and variant. */
export function buildComparisonRows(runs: ExperimentRun[]): ComparisonRow[] {
  const rows: ComparisonRow[] = [];
  runs.forEach((run) => {
    (run.targets ?? []).forEach((target) => {
      const suffix = [target.variant, target.writeConcern].filter(Boolean).join(', ');
      rows.push({
        key: `${run.id}:${target.target}`,
        runId: run.id,
        scenario: run.request.scenario,
        label: `${run.request.scenario} · ${target.target}${suffix ? ` (${suffix})` : ''}`,
        clients: run.request.clients ?? 0,
        error: target.error,
        setup: target.setup ?? [],
        target,
      });
    });
  });
  return rows;
}

export function aggregatedValue(target: TargetRunResult, metric: string): number | null {
  const stats = target.aggregated?.[metric];
  return stats ? stats.mean : null;
}

export function repetitions(target: TargetRunResult): SampleMetrics[] {
  return [...(target.samples ?? [])].sort((a, b) => a.repetition - b.repetition);
}

/** Total completed requests of a target across its repetitions. */
export function httpRequests(target: TargetRunResult): number {
  return (target.samples ?? []).reduce((sum, sample) => sum + (sample.httpRequests ?? 0), 0);
}

/**
 * A target is only measured when at least one request completed. On the heavy reports a target can
 * finish its window with zero requests: then 0 req/s and 0 ms mean "no data", not "measured zero",
 * and such a target must not be ranked against the others.
 */
export function isMeasured(target: TargetRunResult): boolean {
  return !target.error && (target.samples?.length ?? 0) > 0 && httpRequests(target) > 0;
}

/** Bar chart data: one bar per compared target with its aggregated mean p95. */
export function p95Bars(rows: ComparisonRow[]): Array<{ name: string; p95: number }> {
  return rows.map((row) => ({ name: row.label, p95: aggregatedValue(row.target, 'latencyP95') ?? 0 }));
}

/** Bar chart data: one bar per compared target with its aggregated mean throughput. */
export function rpsBars(rows: ComparisonRow[]): Array<{ name: string; rps: number }> {
  return rows.map((row) => ({ name: row.label, rps: aggregatedValue(row.target, 'rps') ?? 0 }));
}

/** Throughput against the client count, one line per compared target. */
export function rpsVsClients(rows: ComparisonRow[]): Series {
  const byClients = new Map<number, Record<string, number | string>>();
  const keys: string[] = [];
  rows.forEach((row) => {
    if (!keys.includes(row.label)) {
      keys.push(row.label);
    }
    const point = byClients.get(row.clients) ?? { clients: row.clients };
    point[row.label] = aggregatedValue(row.target, 'rps') ?? 0;
    byClients.set(row.clients, point);
  });
  return {
    data: [...byClients.values()].sort((a, b) => Number(a.clients) - Number(b.clients)),
    keys,
  };
}

export function hasTimeline(rows: ComparisonRow[]): boolean {
  return rows.some((row) => pointsOf(row).length > 0);
}

/**
 * Per-second series recorded for resilience runs (S6). Each repetition has its own timeline, so
 * the values of one target are averaged per second before the targets are put on the same axis.
 */
export function timelineSeries(rows: ComparisonRow[], field: keyof TimelinePoint): Series {
  const bySecond = new Map<number, Record<string, number | string>>();
  const keys: string[] = [];
  rows.forEach((row) => {
    const perSecond = new Map<number, number[]>();
    pointsOf(row).forEach((point) => {
      const values = perSecond.get(point.secondOffset) ?? [];
      values.push(Number(point[field]));
      perSecond.set(point.secondOffset, values);
    });
    if (perSecond.size === 0) {
      return;
    }
    if (!keys.includes(row.label)) {
      keys.push(row.label);
    }
    perSecond.forEach((values, second) => {
      const merged = bySecond.get(second) ?? { secondOffset: second };
      merged[row.label] = values.reduce((sum, value) => sum + value, 0) / values.length;
      bySecond.set(second, merged);
    });
  });
  return {
    data: [...bySecond.values()].sort((a, b) => Number(a.secondOffset) - Number(b.secondOffset)),
    keys,
  };
}

function pointsOf(row: ComparisonRow): TimelinePoint[] {
  return (row.target.samples ?? []).flatMap((sample) => sample.timeline ?? []);
}
