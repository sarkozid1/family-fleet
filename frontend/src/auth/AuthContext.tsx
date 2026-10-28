import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { onUnauthorized, tokenStore } from '../api/client';
import * as api from '../api/endpoints';
import type { User } from '../api/types';

interface AuthState {
  user: User | null;
  /** Az első betöltéskor még nem tudjuk, érvényes-e a tárolt token. */
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    // Lejárt token (401) esetén az API-kliens törli a tokent, mi pedig kijelentkeztetünk.
    onUnauthorized(() => setUser(null));
    if (!tokenStore.get()) {
      setInitializing(false);
      return;
    }
    api.me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false));
  }, []);

  const login = async (email: string, password: string) => {
    const token = await api.login({ email, password });
    tokenStore.set(token.accessToken);
    setUser(await api.me());
  };

  const logout = () => {
    tokenStore.clear();
    setUser(null);
  };

  return <AuthContext.Provider value={{ user, initializing, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth csak AuthProvider-en belül használható');
  }
  return context;
}
