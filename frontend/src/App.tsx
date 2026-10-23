import React, { useState } from 'react';

type Tab = 'experiments' | 'results' | 'catalog' | 'live';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('experiments');

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <header style={{ backgroundColor: '#1e293b', borderBottom: '1px solid #334155', padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <h1 style={{ fontSize: '1.25rem', fontWeight: 'bold', color: '#38bdf8' }}>
            NoSQL vs Relational Benchmark
          </h1>
          <span style={{ fontSize: '0.75rem', backgroundColor: '#0369a1', color: '#e0f2fe', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>
            Spring Boot 3.4
          </span>
        </div>
        <nav style={{ display: 'flex', gap: '1rem' }}>
          {(['experiments', 'results', 'catalog', 'live'] as Tab[]).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                background: activeTab === tab ? '#38bdf8' : 'transparent',
                color: activeTab === tab ? '#0f172a' : '#94a3b8',
                border: 'none',
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                fontWeight: 600,
                textTransform: 'capitalize',
                transition: 'all 0.2s'
              }}
            >
              {tab === 'experiments' ? 'Experiment Console' :
               tab === 'results' ? 'Results' :
               tab === 'catalog' ? 'Catalog Explorer' : 'Live Status'}
            </button>
          ))}
        </nav>
      </header>

      <main style={{ flex: 1, padding: '2rem' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', backgroundColor: '#1e293b', borderRadius: '8px', padding: '1.5rem', border: '1px solid #334155' }}>
          <h2 style={{ fontSize: '1.1rem', marginBottom: '1rem', color: '#f1f5f9' }}>
            {activeTab === 'experiments' && 'Run & Configure Experiments'}
            {activeTab === 'results' && 'Comparative Analysis & Metrics'}
            {activeTab === 'catalog' && 'Interactive Catalog & Dynamic Filtering'}
            {activeTab === 'live' && 'Real-time Service Health & System Telemetry'}
          </h2>
          <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>
            Benchmark platform operational. Full scenario suite ready.
          </p>
        </div>
      </main>

      <footer style={{ padding: '1rem 2rem', borderTop: '1px solid #334155', textAlign: 'center', color: '#64748b', fontSize: '0.8rem' }}>
        MongoDB 7.0 (Replica Set) | Redis 7.2 | PostgreSQL 16.2 | Spring Boot 3.4
      </footer>
    </div>
  );
}
