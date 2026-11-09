import type { RunStatus } from './api/types';

/** Human-friendly, Serbian (ijekavian) names for experiment targets (IDs). */
const TARGET_LABELS: Record<string, string> = {
  'nosql-blocking': 'MongoDB (standardni način rada)',
  'nosql-reactive': 'MongoDB (brži odziv pod opterećenjem)',
  'nosql-reactive-cache': 'MongoDB, brži odziv (keš uključen)',
  'nosql-reactive-no-cache': 'MongoDB, brži odziv (bez keša)',
  relational: 'PostgreSQL (relaciona baza)',
  'relational-normalized': 'PostgreSQL (podaci u tabelama)',
  'relational-jsonb': 'PostgreSQL (podaci u JSON obliku)',
  'relational-jsonb-indexed': 'PostgreSQL, JSON (sa indeksom)',
  'relational-jsonb-unindexed': 'PostgreSQL, JSON (bez indeksa)',
  'relational-cache': 'PostgreSQL (keš uključen)',
  'relational-no-cache': 'PostgreSQL (bez keša)',
  'mongo-cache': 'MongoDB (keš uključen)',
  'mongo-no-cache': 'MongoDB (bez keša)',
  'mongo-indexed': 'MongoDB (sa indeksom)',
  'mongo-unindexed': 'MongoDB (bez indeksa)',
  'mongo-w1': 'MongoDB (writeConcern: w1)',
  'mongo-majority': 'MongoDB (writeConcern: majority)',
  postgres: 'PostgreSQL',
  'postgres-indexed': 'PostgreSQL (sa indeksom)',
  'postgres-unindexed': 'PostgreSQL (bez indeksa)',
  failover: 'Prebacivanje na rezervnu bazu (kvar)',
  'redis-down': 'Rad bez keša (Redis isključen)',
  concurrency: 'Istovremeni pristup (više korisnika odjednom)',
};

export function targetLabel(id: string | null | undefined): string {
  if (!id) return '—';
  return TARGET_LABELS[id] ?? id;
}

export function statusLabel(status: RunStatus | string | null | undefined): string {
  switch (status) {
    case 'PENDING':
      return 'Na čekanju';
    case 'RUNNING':
      return 'U toku';
    case 'COMPLETED':
      return 'Završeno';
    case 'FAILED':
      return 'Neuspjelo';
    case 'REJECTED':
      return 'Odbijeno';
    default:
      return status ? String(status) : '—';
  }
}

export function writeConcernLabel(value: string | null | undefined): string {
  if (!value) return '';
  if (value === 'w1') return 'brži upis';
  if (value === 'majority') return 'nivo sigurnosti: visok';
  return value;
}
