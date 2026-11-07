import { useEffect, useState } from 'react';
import type { AnchorHTMLAttributes, ReactNode } from 'react';

/**
 * Minimal hash router. The app has four screens and no nested layout, so a dependency-free
 * router keeps the bundle small and works identically from the Vite dev server and nginx
 * (both serve index.html for every path).
 */
export function normalizePath(hash: string): string {
  const raw = hash.replace(/^#/, '');
  if (raw === '') {
    return '/';
  }
  return raw.startsWith('/') ? raw : `/${raw}`;
}

export function usePath(): string {
  const [path, setPath] = useState(() => normalizePath(window.location.hash));

  useEffect(() => {
    const onChange = () => setPath(normalizePath(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  return path;
}

export function navigate(path: string): void {
  const next = normalizePath(path);
  if (normalizePath(window.location.hash) === next) {
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  } else {
    window.location.hash = next;
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

interface LinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> {
  to: string;
  children: ReactNode;
}

export function Link({ to, children, ...rest }: LinkProps) {
  return (
    <a href={`#${normalizePath(to)}`} {...rest}>
      {children}
    </a>
  );
}
