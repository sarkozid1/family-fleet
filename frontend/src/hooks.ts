import { useCallback, useEffect, useState } from 'react';

interface LoadState<T> {
  data: T | undefined;
  error: unknown;
  loading: boolean;
}

/**
 * Adatbetöltés egy komponensben: a deps változásakor újratölt, a reload() kézzel újratölt.
 * A "cancelled" jelző miatt egy elavult válasz nem írja felül az újabbat.
 */
export function useLoad<T>(load: () => Promise<T>, deps: readonly unknown[]) {
  const [state, setState] = useState<LoadState<T>>({ data: undefined, error: undefined, loading: true });
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState((previous) => ({ ...previous, loading: true }));
    load().then(
      (data) => !cancelled && setState({ data, error: undefined, loading: false }),
      (error: unknown) => !cancelled && setState({ data: undefined, error, loading: false }),
    );
    return () => {
      cancelled = true;
    };
    // A load függvény minden rendereléskor új, ezért szándékosan csak a deps-re figyelünk.
  }, [...deps, reloadCount]);

  const reload = useCallback(() => setReloadCount((count) => count + 1), []);
  return { ...state, reload };
}

/** Egy művelet (pl. űrlapküldés) futása: pending állapot és a hiba megjelenítéshez. */
export function useAction<A extends unknown[], R>(action: (...args: A) => Promise<R>) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(undefined);

  const run = async (...args: A): Promise<R | undefined> => {
    setPending(true);
    setError(undefined);
    try {
      return await action(...args);
    } catch (caught) {
      setError(caught);
      return undefined;
    } finally {
      setPending(false);
    }
  };

  return { run, pending, error, clearError: () => setError(undefined) };
}
