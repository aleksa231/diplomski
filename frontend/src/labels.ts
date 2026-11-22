import type { RunStatus } from './api/types';

/** Human-friendly, Serbian (ijekavian) names for experiment targets (IDs). */
const TARGET_LABELS: Record<string, string> = {
  'nosql-blocking': 'MongoDB (blokirajući pristup)',
  'nosql-reactive': 'MongoDB (reaktivni pristup)',
  relational: 'PostgreSQL (relaciona baza)',
  'relational-normalized': 'PostgreSQL (podaci u tabelama)',
  'relational-cache': 'PostgreSQL (keš uključen)',
  'relational-no-cache': 'PostgreSQL (bez keša)',
  'mongo-cache': 'MongoDB (keš uključen)',
  'mongo-no-cache': 'MongoDB (bez keša)',
  postgres: 'PostgreSQL',
};

export function targetLabel(id: string | null | undefined): string {
  if (!id) return '-';
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
      return status ? String(status) : '-';
  }
}
