import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { ExperimentRun } from '../api/types';
import {
  SERIES_COLORS,
  type ComparisonRow,
  aggregatedValue,
  buildComparisonRows,
  hasTimeline,
  isMeasured,
  p95Bars,
  repetitions,
  rpsBars,
  timelineSeries,
} from '../analysis';
import { EmptyState } from './Banner';
import { targetLabel } from '../labels';
import { int, meanStd, num, percent, timestamp } from '../format';

const AXIS = { stroke: '#94a3b8', tick: { fontSize: 11 } };
const TOOLTIP_STYLE = { backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: 6 };

/** Plain-language names for the access path recorded next to each S4 query plan. */
const SCAN_LABELS: Record<string, string> = {
  INDEX: 'indeks',
  COLLECTION_SCAN: 'scan kolekcije',
  SEQUENTIAL_SCAN: 'scan tabele',
  UNKNOWN: 'n/a',
};

function scanBadgeClass(scanType: string): string {
  if (scanType === 'INDEX') return 'badge badge-ok';
  if (scanType === 'UNKNOWN') return 'badge badge-muted';
  return 'badge badge-warn';
}

const QUERY_ORDER = ['Q1', 'Q2', 'Q3', 'Q4', 'Q5', 'Q6', 'Q7'] as const;

const QUERY_LEGEND: Record<string, string> = {
  Q1: 'katalog (kategorija + sortiranje po cijeni)',
  Q2: 'prodaja po kategoriji u u?em periodu',
  Q3: 'najprodavaniji proizvodi u u?em periodu',
  Q4: 'prodaja po kategoriji bez vremenskog filtera (kontrast, cijeli dataset)',
  Q5: 'najprodavaniji bez filtera (kontrast)',
  Q6: 'ukupne prodaje u u?em periodu, bez spajanja narud?bi i proizvoda (izoluje indeks po datumu)',
  Q7: 'ukupne prodaje bez vremenskog filtera (kontrast, cijeli dataset)',
};

function explainLegend(queries: string[]): string {
  if (queries.includes('Q6')) {
    return 'Q4/Q5/Q7 su kontrast bez filtera (scan punog skupa je o?ekivan). Q6 namjerno ne spaja narud?be i proizvode, pa filter po datumu ostaje klju?an korak.';
  }
  return 'Q4/Q5 su kontrast bez filtera (scan punog skupa je o?ekivan) ? oni pokazuju za?to indeks poma?e kad filter postoji (Q2/Q3).';
}

function rps(row: ComparisonRow): number {
  return aggregatedValue(row.target, 'rps') ?? 0;
}

function p95(row: ComparisonRow): number {
  return aggregatedValue(row.target, 'latencyP95') ?? 0;
}

/** The scenario is already in the card header, so targets are named without repeating it. */
function targetName(row: ComparisonRow): string {
  const suffix = [row.target.variant, row.target.writeConcern].filter(Boolean).join(', ');
  return `${targetLabel(row.target.target)}${suffix ? ` (${suffix})` : ''}`;
}

/**
 * Report of one finished run, written to be read before it is analysed: the sentences first, the
 * aggregate table and the repetitions after them.
 */
export default function RunReport({ run }: { run: ExperimentRun }) {
  const rows = buildComparisonRows([run]).map((row) => ({ ...row, label: targetName(row) }));
  const measured = rows.filter((row) => isMeasured(row.target));
  const failed = rows.filter((row) => row.error);
  const unmeasured = rows.filter((row) => !row.error && !isMeasured(row.target));
  const [focusKey, setFocusKey] = useState<string | null>(null);
  const focus =
    rows.find((row) => row.key === focusKey) ?? [...measured].sort((a, b) => rps(b) - rps(a))[0];

  if (rows.length === 0) {
    return <EmptyState>Pokretanje jos nema nijedan sistem, pa nema ni rezultata za prikaz.</EmptyState>;
  }

  const byRps = [...measured].sort((a, b) => rps(b) - rps(a));
  const byP95 = [...measured].sort((a, b) => p95(a) - p95(b));
  const fastest = byRps[0];
  const slowest = byRps[byRps.length - 1];
  const lowestP95 = byP95[0];
  const highestP95 = byP95[byP95.length - 1];
  const repetitionsCount = Math.max(...rows.map((row) => row.target.samples?.length ?? 0));
  const withErrors = measured.filter((row) => (row.target.aggregated?.errorRate?.mean ?? 0) > 0);
  const throughput = rpsBars(measured);
  const latency = p95Bars(measured);
  const explainRows = rows.filter((row) => (row.target.explainPlans?.length ?? 0) > 0);
  const showTimeline = hasTimeline(measured);
  const rpsTimeline = timelineSeries(measured, 'rps');
  const p95Timeline = timelineSeries(measured, 'p95Ms');

  return (
    <div className="stack">
      {measured.length === 0 ? (
        <EmptyState>
          Nijedan sistem nije izmjeren. Provjeri log pokretanja i dostupnost servisa.
        </EmptyState>
      ) : (
        <div className="stack">
          <p className="small">
            Prikazane vrijednosti su prosjek {repetitionsCount} repeticije po sistemu.
            {fastest ? (
              <>
                {' '}
                NajveÄ‡u propusnost ima <strong>{fastest.label}</strong> sa{' '}
                <strong>{int(rps(fastest))} zahtjeva/s</strong>
                {slowest && slowest !== fastest ? (
                  <>
                    , što je {num(rps(fastest) / Math.max(rps(slowest), 0.01), 1)}× više od{' '}
                    {slowest.label} ({int(rps(slowest))} zahtjeva/s)
                  </>
                ) : null}
                .
              </>
            ) : null}
            {lowestP95 ? (
              <>
                {' '}
                Najnižu p95 latenciju ima <strong>{lowestP95.label}</strong> (
                <strong>{num(p95(lowestP95), 1)} ms</strong>)
                {highestP95 && highestP95 !== lowestP95 ? (
                  <>
                    , a najvišu {highestP95.label} ({num(p95(highestP95), 1)} ms)
                  </>
                ) : null}
                .
              </>
            ) : null}{' '}
            {withErrors.length === 0
              ? 'Nijedan sistem nije prijavio gre?ke.'
              : `Greške su izmjerene kod: ${withErrors
                  .map((row) => `${row.label} (${percent(row.target.aggregated?.errorRate?.mean)})`)
                  .join(', ')}.`}
            {unmeasured.length > 0 ? (
              <>
                {' '}
                Sistemi koji nisu izmjereni (nijedan zahtjev nije zavr?en u mjernom prozoru) se ne
                rangiraju: {unmeasured.map((row) => row.label).join(', ')}. Njihove nule znaÄe
                odsustvo podataka, a ne izmjerene vrijednosti.
              </>
            ) : null}
          </p>

          <div className="grid-4">
            {rows.map((row) => (
              <div className="tile" key={row.key}>
                <div className="small muted">{row.label}</div>
                {row.error ? (
                  <div className="badge badge-error mt-1">GREŠKA</div>
                ) : !isMeasured(row.target) ? (
                  <>
                    <div className="badge badge-muted mt-1">nije izmjereno</div>
                    <div className="small muted mt-1">
                      nijedan zahtjev nije završen u prozoru — vrijednosti nisu mjerodavne
                    </div>
                  </>
                ) : (
                  <>
                    <div className="mt-1">
                      <strong>{int(aggregatedValue(row.target, 'rps'))} zahtjeva u sekundi</strong>
                    </div>
                    <div className="small muted mt-1">
                      srednja {num(aggregatedValue(row.target, 'latencyMean'), 1)} ms · p95{' '}
                      {num(aggregatedValue(row.target, 'latencyP95'), 1)} ms · greške{' '}
                      {percent(row.target.aggregated?.errorRate?.mean)}
                    </div>
                    <div className="small muted">
                      CPU {num(aggregatedValue(row.target, 'cpuAvg'), 2)} · mem{' '}
                      {num(aggregatedValue(row.target, 'memAvg'), 0)} MB ·{' '}
                      {(row.target.samples?.length ?? 0)} rep.
                    </div>
                    {row.setup.length > 0 || row.target.variant || row.target.writeConcern ? (
                      <div className="small muted">
                        primijenjeno: {[...row.setup, row.target.variant, row.target.writeConcern]
                          .filter(Boolean)
                          .join(', ')}
                      </div>
                    ) : null}
                  </>
                )}
              </div>
            ))}
          </div>

          {failed.length > 0 ? (
            <div className="banner banner-error">
              <strong>Sistemi koji nisu izmjereni:</strong>
              <ul className="small">
                {failed.map((row) => (
                  <li key={row.key}>
                    {row.label}: {row.error}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      )}

      {measured.length > 0 ? (
        <>
          <section className="card">
            <h2 className="card-title">Propusnost po sistemu (zahtjeva u sekundi, prosjek repeticija)</h2>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={throughput} margin={{ top: 35, right: 30, bottom: 64, left: 20 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="name" {...AXIS} interval={0} angle={-20} textAnchor="end" height={70} />
                  <YAxis {...AXIS} unit=" rps" width={75} />
                  <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value) => `${int(Number(value))} zahtjeva/s`} />
                  <Bar dataKey="rps" fill="#4ade80" name="zahtjeva/s" maxBarSize={60} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section className="card">
            <h2 className="card-title">Ka?njenje (p95) po sistemu (ms, prosjek repeticija)</h2>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={latency} margin={{ top: 35, right: 30, bottom: 64, left: 20 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="name" {...AXIS} interval={0} angle={-20} textAnchor="end" height={70} />
                  <YAxis {...AXIS} unit=" ms" width={75} />
                  <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value) => `${num(Number(value), 1)} ms`} />
                  <Bar dataKey="p95" fill="#38bdf8" name="p95" maxBarSize={60} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          {showTimeline ? (
            <section className="card">
              <h2 className="card-title">Kretanje po sekundi: propusnost i p95</h2>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={rpsTimeline.data} margin={{ top: 35, right: 30, bottom: 8, left: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                    <XAxis dataKey="secondOffset" type="number" domain={['dataMin', 'dataMax']} {...AXIS} unit=" s" />
                    <YAxis {...AXIS} unit=" rps" width={75} />
                    <Tooltip contentStyle={TOOLTIP_STYLE} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    {rpsTimeline.keys.map((key, index) => (
                      <Line
                        key={key}
                        type="monotone"
                        dataKey={key}
                        stroke={SERIES_COLORS[index % SERIES_COLORS.length]}
                        strokeWidth={2}
                        dot={false}
                        connectNulls
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={p95Timeline.data} margin={{ top: 35, right: 30, bottom: 8, left: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                    <XAxis dataKey="secondOffset" type="number" domain={['dataMin', 'dataMax']} {...AXIS} unit=" s" />
                    <YAxis {...AXIS} unit=" ms" width={75} />
                    <Tooltip contentStyle={TOOLTIP_STYLE} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    {p95Timeline.keys.map((key, index) => (
                      <Line
                        key={key}
                        type="monotone"
                        dataKey={key}
                        stroke={SERIES_COLORS[index % SERIES_COLORS.length]}
                        strokeWidth={2}
                        dot={false}
                        connectNulls
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <p className="small muted">
                Vrijednosti su usrednjene preko repeticija; pad propusnosti i skok p95 oznaÄavaju trenutak
                ubacivanja otkaza.
              </p>
            </section>
          ) : null}

          <section className="card">
            <h2 className="card-title">Agregirano po sistemu (prosjek ? standardna devijacija)</h2>
            <div className="table-wrap">
              <table className="table">
              <thead>
                <tr>
                  <th>Sistem</th>
                  <th>Konfiguracija</th>
                  <th className="text-center">zahtjeva/s</th>
                  <th className="text-center">Srednja (ms)</th>
                  <th className="text-center">p50</th>
                  <th className="text-center">p95</th>
                  <th className="text-center">p99</th>
                  <th className="text-center">Greške</th>
                  <th className="text-center">CPU</th>
                  <th className="text-center">Mem (MB)</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.key}>
                    <td>
                      {row.label}
                      {row.error ? <span className="badge badge-error ml-1">GREŠKA</span> : null}
                    </td>
                    <td className="small">{row.setup.join(', ') || '—'}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.rps, 0)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.latencyMean)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.latencyP50)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.latencyP95)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.latencyP99)}</td>
                    <td className="text-center">{percent(row.target.aggregated?.errorRate?.mean)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.cpuAvg)}</td>
                    <td className="text-center">{meanStd(row.target.aggregated?.memAvg, 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            <p className="small muted mt-1">
              Kolona „Konfiguracija" pokazuje šta je runner primijenio prije mjerenja (npr.
              cache=disabled), što je odluÄujuÄ‡e za poreÄ‘enje sa i bez Redis-a.
            </p>
            <p className="small muted">
              Kako Äitati brojeve: <strong>zahtjeva/s</strong> je broj zahtjeva koje sistem završi u
              sekundi (više je bolje); <strong>srednja</strong> je prosjeÄno vrijeme odgovora, a{' '}
              <strong>p95</strong> znaÄi da je 95% korisnika dobilo odgovor brže od tog vremena
              (manje je bolje); <strong>greške</strong> je udio zahtjeva koji nisu uspjeli.
            </p>
          </section>

          {explainRows.length > 0 ? (() => {
            const present = new Set(explainRows.flatMap((row) => (row.target.explainPlans ?? []).map((p) => p.query)));
            const explainQueries = QUERY_ORDER.filter((q) => present.has(q));
            const scenarioCode = run.request.scenario;
            const explainTitle = 'Planovi upita: da li se koristi indeks';
            return (
            <section className="card">
              <h2 className="card-title">{explainTitle}</h2>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Sistem</th>
                      {explainQueries.map((query) => (
                        <th key={query} className="text-center">
                          {query}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {explainRows.map((row) => (
                      <tr key={row.key}>
                        <td>{row.label}</td>
                        {explainQueries.map((query) => {
                          const snapshot = row.target.explainPlans?.find((item) => item.query === query);
                          return (
                            <td key={query} className="text-center">
                              {snapshot ? (
                                <span
                                  className={scanBadgeClass(snapshot.scanType)}
                                  title={
                                    snapshot.error ??
                                    (snapshot.stages.length > 0 ? snapshot.stages.join(' ? ') : undefined)
                                  }
                                >
                                  {snapshot.error ? 'n/a' : (SCAN_LABELS[snapshot.scanType] ?? snapshot.scanType)}
                                </span>
                              ) : (
                                <span className="muted">?</span>
                              )}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="small muted mt-1">
                Plan se ?ita sa servisa prije mjerenja (isti upiti koje pokre?e {scenarioCode}).{' '}
                {explainQueries.map((q, i) => (
                  <span key={q}>
                    <strong>{q}</strong> {QUERY_LEGEND[q] ?? q}
                    {i < explainQueries.length - 1 ? '; ' : '. '}
                  </span>
                ))}
                ?indeks? = baza je koristila indeks; ?scan? = pro?itala je sve redove. {explainLegend(explainQueries)}
              </p>
            </section>
            );
          })() : null}

          {focus ? (
            <section className="card">
              <div className="spread">
                <h2 className="card-title">PojedinaÄne repeticije</h2>
                <select
                  className="input"
                  value={focus.key}
                  onChange={(event) => setFocusKey(event.target.value)}
                >
                  {rows.map((row) => (
                    <option key={row.key} value={row.key}>
                      {row.label}
                    </option>
                  ))}
                </select>
              </div>
            <EmptyState>Nema pojedinačnih mjerenja za ovaj sistem.</EmptyState>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th className="text-center">zahtjeva/s</th>
                      <th className="text-center">Srednja (ms)</th>
                      <th className="text-center">p50</th>
                      <th className="text-center">p95</th>
                      <th className="text-center">p99</th>
                      <th className="text-center">Greške</th>
                      <th className="text-center">CPU</th>
                      <th className="text-center">Mem (MB)</th>
                      <th className="text-center">HTTP</th>
                      <th className="text-center">k6 exit</th>
                      <th>PoÄetak</th>
                    </tr>
                  </thead>
                  <tbody>
                    {repetitions(focus.target).map((sample) => (
                      <tr key={sample.repetition}>
                        <td>{sample.repetition}</td>
                        <td className="text-center">{int(sample.rps)}</td>
                        <td className="text-center">{num(sample.latencyMean)}</td>
                        <td className="text-center">{num(sample.latencyP50)}</td>
                        <td className="text-center">{num(sample.latencyP95)}</td>
                        <td className="text-center">{num(sample.latencyP99)}</td>
                        <td className="text-center">{percent(sample.errorRate)}</td>
                        <td className="text-center">{num(sample.cpuAvg)}</td>
                        <td className="text-center">{num(sample.memAvg, 0)}</td>
                        <td className="text-center">{int(sample.httpRequests)}</td>
                        <td className="text-center">{sample.k6ExitCode}</td>
                        <td className="small">{timestamp(sample.startedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
            </div>
              <p className="small muted mt-1">
                Format „prosjek ± standardna devijacija" u tabeli iznad raÄuna se iz ovih mjerenja.
              </p>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
