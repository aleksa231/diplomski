import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App.tsx';
import { ExperimentSessionProvider } from './experiment-session.tsx';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A benchmark console shows failures instead of hiding them behind retries.
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ExperimentSessionProvider>
        <App />
      </ExperimentSessionProvider>
    </QueryClientProvider>
  </React.StrictMode>
);
