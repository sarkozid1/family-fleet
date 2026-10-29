import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { DeadlinesTab } from './pages/DeadlinesTab';
import { HouseholdPage } from './pages/HouseholdPage';
import { HouseholdsPage } from './pages/HouseholdsPage';
import { LoginPage } from './pages/LoginPage';
import { MembersTab } from './pages/MembersTab';
import { OverviewTab } from './pages/OverviewTab';
import { SharingTab } from './pages/SharingTab';
import { VehiclesTab } from './pages/VehiclesTab';

/** Csak bejelentkezve érhető el; különben a belépő oldalra irányít. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth();
  if (initializing) {
    return <p className="muted center">Betöltés…</p>;
  }
  return user ? children : <Navigate to="/login" replace />;
}

/*
 * HashRouter (#/households/1): az útvonal a # után van, a böngésző ezt nem küldi a szervernek.
 * Így oldalfrissítéskor is mindig az index.html töltődik be, és a Spring Boot oldalon nem kell
 * külön "minden ismeretlen útvonal -> index.html" továbbítás.
 */
export function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route index element={<HouseholdsPage />} />
            <Route path="households/:householdId" element={<HouseholdPage />}>
              <Route index element={<Navigate to="overview" replace />} />
              <Route path="overview" element={<OverviewTab />} />
              <Route path="deadlines" element={<DeadlinesTab />} />
              <Route path="vehicles" element={<VehiclesTab />} />
              <Route path="members" element={<MembersTab />} />
              <Route path="sharing" element={<SharingTab />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </HashRouter>
    </AuthProvider>
  );
}
