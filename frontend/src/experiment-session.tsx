import { createContext, useCallback, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';
import { fetchRun } from './api/client';
import type { Timed } from './api/client';
import type { ExperimentRun } from './api/types';
import { isActive } from './format';

export type AdminTab = 'experiment' | 'history';

const RUN_KEY = 'km-active-run';
const TAB_KEY = 'km-admin-tab';

function readStorage(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.sessionStorage.removeItem(key);
    } else {
      window.sessionStorage.setItem(key, value);
    }
  } catch {
    // Storage can be disabled; navigation still works within the current page load.
  }
}

interface ExperimentSessionValue {
  runId: string | null;
  setRunId: (id: string | null) => void;
  runQuery: UseQueryResult<Timed<ExperimentRun>, Error>;
  run: ExperimentRun | null;
  adminTab: AdminTab;
  setAdminTab: (tab: AdminTab) => void;
}

const ExperimentSessionContext = createContext<ExperimentSessionValue | null>(null);

/**
 * Owns the run that is currently on screen. It lives above the router so leaving the admin console,
 * switching scenario or reloading the tab cannot unmount the polling query and make the run vanish.
 */
export function ExperimentSessionProvider({ children }: { children: ReactNode }) {
  const [runId, setRunIdState] = useState<string | null>(() => readStorage(RUN_KEY));
  const [adminTab, setAdminTabState] = useState<AdminTab>(() =>
    readStorage(TAB_KEY) === 'history' ? 'history' : 'experiment',
  );

  const setRunId = useCallback((id: string | null) => {
    setRunIdState(id);
    writeStorage(RUN_KEY, id);
  }, []);

  const setAdminTab = useCallback((tab: AdminTab) => {
    setAdminTabState(tab);
    writeStorage(TAB_KEY, tab);
  }, []);

  // Polling is hosted here rather than in the console page, so it keeps running on every route.
  const runQuery = useQuery({
    queryKey: ['run', runId],
    queryFn: () => fetchRun(runId as string),
    enabled: runId !== null,
    refetchInterval: (query) => (isActive(query.state.data?.data.status) ? 1500 : false),
  });

  const run = runQuery.data?.data ?? null;

  return (
    <ExperimentSessionContext.Provider value={{ runId, setRunId, runQuery, run, adminTab, setAdminTab }}>
      {children}
    </ExperimentSessionContext.Provider>
  );
}

export function useExperimentSession(): ExperimentSessionValue {
  const session = useContext(ExperimentSessionContext);
  if (!session) {
    throw new Error('useExperimentSession must be used inside ExperimentSessionProvider');
  }
  return session;
}
