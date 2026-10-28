import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <img src="/favicon.svg" alt="" width={28} height={28} />
          Family Fleet
        </Link>
        {user && (
          <div className="topbar-user">
            <span>
              {user.displayName}
              {user.role === 'ADMIN' && <span className="badge">admin</span>}
            </span>
            <button type="button" className="button secondary" onClick={logout}>
              Kijelentkezés
            </button>
          </div>
        )}
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
