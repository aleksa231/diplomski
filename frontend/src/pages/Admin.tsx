import { useState } from 'react';
import { FlaskConical, Receipt } from 'lucide-react';
import ExperimentConsole from './ExperimentConsole';
import TransactionHistory from './TransactionHistory';

export default function AdminPage() {
  const [tab, setTab] = useState<'experiment' | 'history'>('experiment');

  return (
    <div className="stack admin-page">
      <section className="admin-intro">
        <h1 className="page-title">Administracija</h1>
        <p className="small muted">
          Pokreni scenario, prati napredak i uporedi rezultate tri backend modela, ili pregledaj
          istoriju transakcija kupaca. Prodavnica i administracija rade nad istim deterministički
          generisanim katalogom.
        </p>
      </section>

      <div className="admin-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'experiment'}
          className={tab === 'experiment' ? 'admin-tab admin-tab-active' : 'admin-tab'}
          onClick={() => setTab('experiment')}
        >
          <FlaskConical size={14} /> Eksperimentalna konzola
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'history'}
          className={tab === 'history' ? 'admin-tab admin-tab-active' : 'admin-tab'}
          onClick={() => setTab('history')}
        >
          <Receipt size={14} /> Istorija transakcija
        </button>
      </div>

      {tab === 'experiment' ? <ExperimentConsole /> : <TransactionHistory />}
    </div>
  );
}