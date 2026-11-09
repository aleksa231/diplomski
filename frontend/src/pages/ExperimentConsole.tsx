import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  BarChart3,
  Check,
  Clock,
  Gauge,
  Info,
  Play,
  Server,
  SlidersHorizontal,
  Terminal,
  Users,
} from 'lucide-react';
import { ApiRequestError, exportRunResult, fetchRun, startExperiment } from '../api/client';
import { EmptyState, ErrorBanner } from '../components/Banner';
import RunReport from '../components/RunReport';
import { int, isActive, statusClass, timestamp } from '../format';
import { statusLabel, targetLabel } from '../labels';
import { DEFAULT_DATASET_SIZE, SCENARIOS, scenarioPreset } from '../scenarios';

export default function ExperimentConsole() {
  const initial = SCENARIOS[0];
  const [scenario, setScenario] = useState(initial.code);
  // The numeric fields are held as strings so that an emptied field stays empty while typing, and
  // are parsed into numbers only when the run is started.
  const [datasetSize, setDatasetSize] = useState(String(DEFAULT_DATASET_SIZE));
  const [clients, setClients] = useState(String(initial.clients));
  const [durationSec, setDurationSec] = useState(String(initial.durationSec));
  const [repetitions, setRepetitions] = useState('3');
  const [targets, setTargets] = useState<string[]>(initial.targets);
  const [runId, setRunId] = useState<string | null>(null);

  const datasetSizeValue = parsePositiveInt(datasetSize);
  const clientsValue = parsePositiveInt(clients);
  const durationSecValue = parsePositiveInt(durationSec);
  const repetitionsValue = parsePositiveInt(repetitions);
  const configValid =
    datasetSizeValue !== null && clientsValue !== null && durationSecValue !== null && repetitionsValue !== null;
  const ready = configValid && targets.length > 0;

  const start = useMutation({
    mutationFn: () => {
      if (!configValid) {
        throw new Error('Sva numerička polja moraju biti pozitivni brojevi.');
      }
      return startExperiment({
        scenario,
        datasetSize: datasetSizeValue as number,
        clients: clientsValue as number,
        durationSec: durationSecValue as number,
        repetitions: repetitionsValue as number,
        targets,
      });
    },
    onSuccess: (result) => setRunId(result.data.id),
  });

  // Progress is polled while the run is active; the interval stops as soon as it finishes.
  const runQuery = useQuery({
    queryKey: ['run', runId],
    queryFn: () => fetchRun(runId as string),
    enabled: runId !== null,
    refetchInterval: (query) => (isActive(query.state.data?.data.status) ? 1500 : false),
  });

  const run = runQuery.data?.data ?? null;

  // Prikazuje se isključivo run pokrenut u trenutnoj sesiji (bez istorije ranijih run-ova).
  const reportRun = run;

  function applyScenario(code: string) {
    setScenario(code);
    const preset = scenarioPreset(code);
    if (preset) {
      setClients(String(preset.clients));
      setDurationSec(String(preset.durationSec));
      setTargets(preset.targets);
    }
  }

  function toggleTarget(name: string) {
    setTargets((current) =>
      current.includes(name) ? current.filter((item) => item !== name) : [...current, name],
    );
  }

  // Only the selected scenario's targets are offered, so an impossible comparison cannot be started
  // from the console. The optional ones are extra backends the scenario's script can measure, and
  // they are never part of the canonical comparison.
  const preset = scenarioPreset(scenario);
  const scenarioTargets = preset?.targets ?? [];
  const scenarioOptionalTargets = preset?.optionalTargets ?? [];
  const scenarioDescription = preset?.description ?? '';

  const completedSteps = run?.progress?.completedSteps ?? 0;
  const totalSteps = run?.progress?.totalSteps ?? 0;
  const progressPercent = totalSteps > 0 ? Math.min(100, (completedSteps / totalSteps) * 100) : 0;
  const logLines = run?.log ?? [];

  return (
    <div className="console">
      <section className="console-panel">
        <div className="console-head">
          <div>
            <span className="console-eyebrow">
              <SlidersHorizontal size={14} /> Konfiguracija
            </span>
            <h2 className="console-title">Pokreni mjerenje</h2>
            <p className="console-lead">
              Izaberi scenario, podesi opterešenje i sisteme, pa pokreni mjerenje.
            </p>
          </div>
          {!ready ? (
            <span className="console-state">
              <Info size={13} /> Provjeri unos
            </span>
          ) : null}
        </div>

        <div className="scenario-grid" role="group" aria-label="Scenario">
          {SCENARIOS.map((item) => {
            const active = item.code === scenario;
            return (
              <button
                key={item.code}
                type="button"
                title={item.description}
                className={active ? 'scenario-card scenario-card-active' : 'scenario-card'}
                aria-pressed={active}
                onClick={() => applyScenario(item.code)}
              >
                <span className="scenario-top">
                  <span className="scenario-code">{item.code}</span>
                  {active ? <Check size={14} /> : null}
                </span>
                <strong className="scenario-name">{item.title}</strong>
                <span className="scenario-meta">
                  {item.clients} virtuelnih korisnika · {item.durationSec} s ·{' '}
                  {item.targets.length} sistema
                </span>
              </button>
            );
          })}
        </div>

        {scenarioDescription ? (
          <div className="console-note">
            <Info size={15} />
            <span>{scenarioDescription}</span>
          </div>
        ) : null}

        <div className="console-fields">
          <label className="field">
            <span>Veličina dataseta</span>
            <input
              className="input"
              type="number"
              min={1}
              value={datasetSize}
              onChange={(event) => setDatasetSize(event.target.value)}
            />
          </label>
          <label className="field">
            <span>Klijenti (virtuelni korisnici)</span>
            <input
              className="input"
              type="number"
              min={1}
              value={clients}
              onChange={(event) => setClients(event.target.value)}
            />
          </label>
          <label className="field">
            <span>Trajanje (s)</span>
            <input
              className="input"
              type="number"
              min={1}
              value={durationSec}
              onChange={(event) => setDurationSec(event.target.value)}
            />
          </label>
          <label className="field">
            <span>Repeticije</span>
            <input
              className="input"
              type="number"
              min={1}
              value={repetitions}
              onChange={(event) => setRepetitions(event.target.value)}
            />
          </label>
        </div>

        <div className="console-block">
          <div className="console-block-head">
            <span className="field-label">Sistemi</span>
            <span className="small muted">Izabrano: {targets.length}</span>
          </div>
          <div className="chips">
            {scenarioTargets.map((name) => (
              <button
                key={name}
                type="button"
                className={targets.includes(name) ? 'chip chip-active' : 'chip'}
                onClick={() => toggleTarget(name)}
              >
                {targetLabel(name)}
              </button>
            ))}
          </div>
          <p className="small muted console-hint">
            Porede se samo procesi i varijante koje scenario {scenario} ispituje.
          </p>

          {scenarioOptionalTargets.length > 0 ? (
            <div className="console-subblock">
              <span className="field-label">Opcioni sistemi</span>
              <div className="chips">
                {scenarioOptionalTargets.map((name) => (
                  <button
                    key={name}
                    type="button"
                    className={targets.includes(name) ? 'chip chip-active' : 'chip chip-optional'}
                    onClick={() => toggleTarget(name)}
                  >
                    {targetLabel(name)}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>

        <div className="console-actions">
          <button
            type="button"
            className="btn btn-primary btn-lg"
            disabled={!ready || start.isPending || isActive(run?.status)}
            onClick={() => start.mutate()}
          >
            <Play size={16} />
            {start.isPending
              ? 'Pokrećem…'
              : isActive(run?.status)
                ? 'Mjerenje u toku…'
                : 'Pokreni mjerenje'}
          </button>
          <span className="small muted">
            {!configValid
              ? 'Sva numerička polja moraju biti pozitivni brojevi.'
              : targets.length === 0
                ? 'Izaberi najmanje jedan sistem.'
                : isActive(run?.status)
                  ? 'Mjerenje je u toku — sačekajte završetak.'
                  : 'Pokretanje se pokreće odmah; napredak i rezultat se prikazuju ispod.'}
          </span>
        </div>

        {start.isError ? (
          <div className="mt-2">
            <ErrorBanner title="Pokretanje nije prihvaćeno">
              {start.error instanceof ApiRequestError ? start.error.message : String(start.error)}
            </ErrorBanner>
          </div>
        ) : null}
      </section>

      <section className="console-panel">
        <div className="console-head">
          <div>
            <h2 className="console-title">Napredak</h2>
          </div>
          {run ? <span className={statusClass(run.status)}>{statusLabel(run.status)}</span> : null}
        </div>

        {runId === null ? (
          <div className="console-empty">
            <Gauge size={22} />
            <div>
              <strong>Nema aktivnog pokretanja</strong>
              <p className="small muted">
                Pokreni mjerenje iznad pa še se ovdje prikazati koraci, log i stanje po sistemu.
              </p>
            </div>
          </div>
        ) : runQuery.isError ? (
          <ErrorBanner title="Ne mogu da pročitam stanje pokretanja">
            {runQuery.error instanceof ApiRequestError ? runQuery.error.message : String(runQuery.error)}
          </ErrorBanner>
        ) : run === null ? (
          <p className="muted">Učitavam…</p>
        ) : (
          <div className="console-run">
            <div className="console-run-head">
              <div className="row">
                <strong>
                  {run.request.scenario} — {run.scenarioTitle}
                </strong>
              </div>
              <span className="console-progress-value">{Math.round(progressPercent)}%</span>
            </div>

            <div>
              <div className="spread small muted">
                <span>{run.progress?.currentStep || '—'}</span>
                <span>
                  {completedSteps} / {totalSteps} koraka
                </span>
              </div>
              <div className="progress-track mt-1">
                <div className="progress-fill" style={{ width: `${progressPercent}%` }} />
              </div>
            </div>

            <div className="console-stats">
              <div className="console-stat">
                <span>
                  <Clock size={13} /> Početak
                </span>
                <strong>{timestamp(run.startedAt)}</strong>
              </div>
              <div className="console-stat">
                <span>
                  <Clock size={13} /> Kraj
                </span>
                <strong>{timestamp(run.finishedAt)}</strong>
              </div>
              <div className="console-stat">
                <span>
                  <Users size={13} /> Klijenti
                </span>
                <strong>{int(run.request.clients)} virtuelnih korisnika</strong>
              </div>
              <div className="console-stat">
                <span>
                  <Clock size={13} /> Trajanje
                </span>
                <strong>{int(run.request.durationSec)} s</strong>
              </div>
            </div>

            {run.error ? <ErrorBanner title="Run je prijavio grešku">{run.error}</ErrorBanner> : null}

            <div className="console-block">
              <div className="console-block-head">
                <span className="field-label">
                  <Server size={13} /> Sistemi
                </span>
                <span className="small muted">{run.targets?.length ?? 0} pripremljeno</span>
              </div>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Sistem</th>
                      <th>Skripta</th>
                      <th>Primijenjeno</th>
                      <th className="text-center">Repeticije</th>
                      <th>Stanje</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(run.targets ?? []).map((target) => (
                      <tr key={target.target}>
                        <td>{targetLabel(target.target)}</td>
                        <td className="mono small">{target.script}</td>
                        <td className="small">{(target.setup ?? []).join(', ') || '—'}</td>
                        <td className="text-center">{target.samples?.length ?? 0}</td>
                        <td>
                          {target.error ? (
                            <span className="badge badge-error">GREŠKA</span>
                          ) : (target.samples?.length ?? 0) > 0 ? (
                            <span className="badge badge-ok">izmjereno</span>
                          ) : (
                            <span className="badge badge-muted">u toku</span>
                          )}
                        </td>
                      </tr>
                    ))}
                    {(run.targets ?? []).length === 0 ? (
                      <tr>
                        <td colSpan={5} className="muted">
                          Sistemi još nisu pripremljeni.
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="console-block">
              <div className="console-block-head">
                <span className="field-label">
                  <Terminal size={13} /> Log
                </span>
                <span className="small muted">posljednjih {Math.min(40, logLines.length)} linija</span>
              </div>
              <pre className="log console-log">{logLines.slice(-40).join('\n') || '—'}</pre>
            </div>

            {run.status === 'COMPLETED' ? (
              <p className="small muted">
                Rezultat u čitljivom obliku, sa grafikonima i tabelama, je u sekciji „Rezultat" ispod.
              </p>
            ) : null}
          </div>
        )}
      </section>

      <section className="console-result">
        <div className="console-head">
          <div>
            <h2 className="console-title">Rezultat mjerenja</h2>
          </div>
          {reportRun?.status === 'COMPLETED' ? (
            <button
              type="button"
              className="btn"
              onClick={() => void exportRunResult(reportRun.id, reportRun.request.scenario)}
            >
              Preuzmi rezultat (JSON)
            </button>
          ) : null}
        </div>

        {runId === null ? (
          <EmptyState>
            Nema pokrenutog eksperimenta. Pokreni mjerenje iznad i rezultat će se prikazati ovdje.
          </EmptyState>
        ) : runQuery.isError ? (
          <ErrorBanner title="Ne mogu da pročitam rezultat">
            {runQuery.error instanceof ApiRequestError
              ? runQuery.error.message
              : String(runQuery.error)}
          </ErrorBanner>
        ) : reportRun === null ? (
          <p className="muted">Učitavam rezultat…</p>
        ) : isActive(reportRun.status) ? (
          <p className="muted">
            Pokretanje {reportRun.request.scenario} je u toku ({reportRun.progress?.currentStep || 'priprema'}).
            Rezultat se prikazuje čim se završi.
          </p>
        ) : (
          <div className="console-result-body">
            <div className="row">
              <span className={statusClass(reportRun.status)}>{statusLabel(reportRun.status)}</span>
              <strong>
                {reportRun.request.scenario} — {reportRun.scenarioTitle}
              </strong>
              <span className="small muted">
                pokrenut {timestamp(reportRun.startedAt)}, završen {timestamp(reportRun.finishedAt)}
              </span>
            </div>
            {reportRun.error ? <ErrorBanner title="Run je prijavio grešku">{reportRun.error}</ErrorBanner> : null}
            <RunReport run={reportRun} />
          </div>
        )}
      </section>

      <div className="console-footnote">
        <BarChart3 size={20} />
        <div>
          <strong>Šta se mjeri</strong>
          <p className="small muted">
            Svaki target se mjeri u najmanje tri repeticije; prosjek i standardna devijacija se računaju
            nad RPS-om, srednjom latencijom, p50, p95, p99, stopom grešaka te CPU i memorijom kontejnera.
            Konfiguracija se upisuje uz svaki rezultat, tako da je svako mjerenje reproduktivno.
          </p>
        </div>
      </div>
    </div>
  );
}

/** Parses a numeric form field into a positive integer; {@code null} while the field is empty or invalid. */
export function parsePositiveInt(value: string): number | null {
  if (value.trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}
