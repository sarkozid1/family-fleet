import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import * as api from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction } from '../hooks';

export function LoginPage() {
  const { user, login } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');

  const submit = useAction(async () => {
    if (mode === 'register') {
      await api.register({ email, password, displayName });
    }
    await login(email, password);
  });

  if (user) {
    return <Navigate to="/" replace />;
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void submit.run();
  };

  return (
    <div className="login">
      <div className="card login-card">
        <h1>Family Fleet</h1>
        <p className="muted">Járművek, biztosítások és határidők a család számára.</p>

        <div className="segmented" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            className={mode === 'login' ? 'active' : ''}
            onClick={() => {
              setMode('login');
              submit.clearError();
            }}
          >
            Belépés
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            className={mode === 'register' ? 'active' : ''}
            onClick={() => {
              setMode('register');
              submit.clearError();
            }}
          >
            Regisztráció
          </button>
        </div>

        <form onSubmit={onSubmit} className="form">
          {mode === 'register' && (
            <label>
              Név
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={100} />
            </label>
          )}
          <label>
            E-mail
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          </label>
          <label>
            Jelszó
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={mode === 'register' ? 8 : undefined}
              maxLength={72}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            />
          </label>
          <ProblemAlert error={submit.error} />
          <button type="submit" className="button" disabled={submit.pending}>
            {mode === 'login' ? 'Belépés' : 'Regisztráció és belépés'}
          </button>
        </form>
      </div>
    </div>
  );
}
