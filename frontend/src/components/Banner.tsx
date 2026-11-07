import type { ReactNode } from 'react';

export function ErrorBanner({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="banner banner-error" role="alert">
      <strong>{title}</strong>
      {children ? <div className="small mt-1">{children}</div> : null}
    </div>
  );
}

export function WarnBanner({ children }: { children: ReactNode }) {
  return <div className="banner banner-warn">{children}</div>;
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="muted">{children}</p>;
}
