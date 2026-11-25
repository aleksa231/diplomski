import { Activity, ArrowLeft } from 'lucide-react';
import { useExperimentSession } from '../experiment-session';
import { isActive, statusClass } from '../format';
import { statusLabel } from '../labels';
import { Link } from '../router';

/** Keeps the run in sight while the user is on any screen other than the console itself. */
export default function ActiveRunBanner() {
  const { runId, run, runQuery, setAdminTab } = useExperimentSession();

  if (runId === null) {
    return null;
  }

  const completed = run?.progress?.completedSteps ?? 0;
  const total = run?.progress?.totalSteps ?? 0;
  const percent = total > 0 ? Math.min(100, (completed / total) * 100) : 0;

  return (
    <div className="active-run-banner" role="status" aria-live="polite">
      <span className="active-run-icon">
        <Activity size={16} />
      </span>
      <div className="active-run-text">
        <strong>
          {run ? `${run.request.scenario} - ${run.scenarioTitle}` : 'Mjerenje u toku'}
        </strong>
        <span className="small muted">
          {runQuery.isError ? (
            'Stanje se trenutno ne može pročitati.'
          ) : run === null ? (
            'Učitavam stanje…'
          ) : isActive(run.status) ? (
            `${run.progress?.currentStep || '-'} · ${completed} / ${total} koraka · ${Math.round(percent)}%`
          ) : (
            'Mjerenje je završeno - rezultat je spremljen.'
          )}
        </span>
      </div>
      {run ? <span className={statusClass(run.status)}>{statusLabel(run.status)}</span> : null}
      <Link
        to="/admin"
        className="btn btn-tiny"
        onClick={() => setAdminTab('experiment')}
      >
        <ArrowLeft size={14} /> Otvori mjerenje
      </Link>
    </div>
  );
}
