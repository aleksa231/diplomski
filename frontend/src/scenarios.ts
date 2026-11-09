/**
 * Scenario catalogue and the target combinations the Makefile drives (spec section 10.3).
 * The numeric fields only prefill the console form and stay editable. The target list is fixed: a
 * scenario is defined by which processes it compares, so the console offers exactly these targets
 * plus an explicit list of optional ones (below). Measuring anything else would answer a different
 * question than the one the scenario asks (and could leave state behind that the result metadata
 * cannot describe).
 */
export interface ScenarioPreset {
  code: string;
  title: string;
  clients: number;
  durationSec: number;
  /**
   * Targets the scenario is defined by. Selected as soon as the scenario is picked, so the default
   * run always is the comparison the scenario describes.
   */
  targets: string[];
  /**
   * Targets that can be measured with this scenario's script and still answer a meaningful
   * question, but are not part of its canonical definition. Offered unselected, and each one is
   * chosen so it cannot corrupt the preset measurement: either it is a stateless backend or it
   * pins its own runtime state (cache/index) because the name says so.
   */
  optionalTargets?: string[];
  /** Brief explanation of what the test does (Serbian). */
  description?: string;
}

export const DEFAULT_DATASET_SIZE = 5000;

export const SCENARIOS: ScenarioPreset[] = [
  {
    code: 'S1',
    title: 'Fleksibilnost šeme i dokument model',
    description:
      'Šta se testira: koliko brzo tri različita načina čuvanja istog kataloga proizvoda mogu odgovoriti na zahtjeve kupaca. MongoDB čuva cijeli proizvod u jednom zapisu (dokument), dok PostgreSQL čuva podatke u tabelama (normalizovano) ili u jednoj JSON koloni. Mjeri se brzina čitanja kataloga i to da li dokument model ima prednost kad je šema fleksibilna.',
    clients: 50,
    durationSec: 30,
    targets: ['nosql-blocking', 'relational-normalized', 'relational-jsonb'],
    // Same catalog reads, non-blocking stack: shows whether the document-model result holds there.
    optionalTargets: ['nosql-reactive'],
  },
  {
    code: 'S2',
    title: 'Keširanje i niska latencija',
    description:
      'Šta se testira: koliko Redis keš (brza privremena memorija) ubrzava čitanje korpe i popularnih proizvoda. Upoređuje se isti backend sa uključenim i isključenim kešom, da bi se vidjelo koliko je baš keš odgovoran za nisku latenciju, a ne sama baza.',
    // The cache is on by default, so a run without cache-off targets would measure "Mongo+Redis vs
    // Postgres+Redis" and could not attribute the low latency to the cache. Both states are set
    // explicitly because a target that leaves the state untouched (null) would inherit whatever the
    // previous target applied.
    clients: 50,
    durationSec: 30,
    targets: ['mongo-no-cache', 'mongo-cache', 'relational-no-cache', 'relational-cache'],
  },
  {
    code: 'S3',
    title: 'Propusnost pisanja pod opterećenjem',
    description:
      'Šta se testira: koliko narudžbi i proizvoda sistem može upisati u bazu u sekundi kada mnogo korisnika istovremeno naručuje. Upoređuje se MongoDB i PostgreSQL. MongoDB se testira sa dva nivoa sigurnosti zapisa: w1 (upis se potvrđuje ćim ga primarna baza primi - brži ali manje siguran, podaci mogu biti izgubljeni pri padu) i majority (upis se potvrđuje tek nakon što ga većina čvorova replica seta potvrdi - sporiji ali sigurniji, podaci su trajno sačuvani).',
    clients: 100,
    durationSec: 30,
    targets: ['mongo-w1', 'mongo-majority', 'postgres'],
    // Write path on the non-blocking stack; write concern does not apply there, so one target is
  },
  {
    code: 'S4',
    title: 'Reaktivno naspram blokirajućeg',
    description:
      'Šta se testira: koliko dobro sistem podnosi hiljade istovremenih zahtjeva - klasični (blokirajući) pristup naspram reaktivnog (non-blocking). Mjeri se skalabilnost pod visokim opterećenjem, tj. da li reaktivni pristup stvarno podnosi više korisnika.',
    clients: 2000,
    durationSec: 60,
    targets: ['nosql-blocking', 'nosql-reactive'],
    // A second blocking baseline (servlet + JDBC), so the blocking cost is not read off one stack.
    optionalTargets: ['relational'],
  },
  {
    code: 'S5',
    title: 'Gdje relacioni model pobjeđuje',
    description:
      'Šta se testira: koliko brzo se mogu izračunati složeni mjesečni izvještaji (spajanje više tabela, JOIN operacije) na velikim skupovima podataka - situacija u kojoj relaciona baza tradicionalno ima prednost pred dokument bazom.',
    // The monthly aggregation takes ~18s on MongoDB, so a 20-client/30s window interrupts every
    // iteration before it finishes and the NoSQL target ends up with no measurement at all.
    // Sparse load and a longer window let each reporting round complete on both targets.
    clients: 10,
    durationSec: 60,
    targets: ['nosql-blocking', 'relational'],
    // Same aggregation on the jsonb storage model: tells whether the relational win comes from the
    // query planner or from the normalized tables.
    optionalTargets: ['relational-jsonb'],
  },
];

export function scenarioPreset(code: string): ScenarioPreset | undefined {
  return SCENARIOS.find((scenario) => scenario.code === code);
}
