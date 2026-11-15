/**
 * Scenario catalogue and the target combinations the runner drives.
 */
export interface ScenarioPreset {
  code: string;
  title: string;
  clients: number;
  durationSec: number;
  datasetSize?: number;
  /**
   * Targets the scenario is defined by. Selected as soon as the scenario is picked, so the default
   * run always is the comparison the scenario describes.
   */
  targets: string[];
  /**
   * Targets that can be measured with this scenario's script and still answer a meaningful
   * question, but are not part of its canonical definition.
   */
  optionalTargets?: string[];
  /** Brief explanation of what the test does (Serbian). */
  description?: string;
}

export const DEFAULT_DATASET_SIZE = 50000;

export const SCENARIOS: ScenarioPreset[] = [
  {
    code: 'S1',
    title: 'Katalog proizvoda - dokumentni model naspram povezanih tabela',
    description:
      'Šta se testira: kupac otvara detalje proizvoda sa osnovnim podacima, dodatnim karakteristikama i posljednjih 20 recenzija. PostgreSQL podatke sastavlja spajanjem iz povezanih tabela, dok MongoDB čita jedan dokument koji sadrži proizvod, njegove atribute i ugrađene novije recenzije. Mjere se vrijeme odgovora (posebno p95) i propusnost bez Redis keša.',
    clients: 50,
    durationSec: 30,
    targets: ['nosql-blocking', 'relational'],
    optionalTargets: ['nosql-reactive'],
  },
  {
    code: 'S2',
    title: 'Često ponavljani zahtjevi - Redis kao dopuna trajnoj bazi',
    description:
      'Šta se testira: veliki broj kupaca ponavlja iste zahtjeve za katalog i listu proizvoda (filtriranje i sortiranje). Isti backend mjeri se bez keša i sa zagrijanim Redis kešom, za PostgreSQL i za MongoDB. Posmatra se koliko keširanje rasterećuje bazu podataka i smanjuje latenciju ponovljenih čitanja.',
    clients: 50,
    durationSec: 30,
    targets: ['mongo-no-cache', 'mongo-cache', 'relational-no-cache', 'relational-cache'],
  },
  {
    code: 'S3',
    title: 'Kupovina posljednjeg primjerka - transakcije, konkurentnost i integritet',
    description:
      'Šta se testira: 100 kupaca istovremeno pokušava kupiti proizvod čija je zaliha tačno 1 komad. Provjerava se konkurentnost, izolacija i integritet transakcija: da li je tačno jedna kupovina uspjela (HTTP 201), da li su ostale odbijene (HTTP 409), te da zaliha ne ode u minus (ostaje tačno 0) i u MongoDB-u i u PostgreSQL-u.',
    clients: 100,
    durationSec: 30,
    targets: ['nosql-blocking', 'relational'],
  },
  {
    code: 'S4',
    title: 'Mjesečni izvještaj o prodaji - povezivanje podataka i agregacije',
    description:
      'Šta se testira: administrator traži mjesečni prihod po kategorijama i najprodavanije proizvode. PostgreSQL koristi povezivanje više tabela (JOIN) i SQL grupisanje, dok MongoDB koristi agregacioni pipeline ($lookup, $unwind, $group). Mjeri se vrijeme izvršavanja analitičkih upita i ponašanje pri većem obimu podataka.',
    clients: 20,
    durationSec: 60,
    datasetSize: 50000,
    targets: ['nosql-blocking', 'relational'],
  },
];

export function scenarioPreset(code: string): ScenarioPreset | undefined {
  return SCENARIOS.find((scenario) => scenario.code === code);
}
