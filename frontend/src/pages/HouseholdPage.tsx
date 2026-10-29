import { NavLink, Outlet, useOutletContext, useParams } from 'react-router-dom';
import * as api from '../api/endpoints';
import type { Household } from '../api/types';
import { ProblemAlert } from '../components/ProblemAlert';
import { useLoad } from '../hooks';

export interface HouseholdContext {
  household: Household;
}

/** A gyerek-útvonalak (fülek) így érik el a betöltött háztartást. */
export const useHousehold = () => useOutletContext<HouseholdContext>().household;

export function HouseholdPage() {
  const householdId = Number(useParams().householdId);
  const household = useLoad(() => api.getHousehold(householdId), [householdId]);

  if (household.error) {
    // Idegen vagy nem létező háztartásnál a backend 404-et ad (ADR-0003).
    return <ProblemAlert error={household.error} />;
  }
  if (!household.data) {
    return <p className="muted">Betöltés…</p>;
  }

  return (
    <div className="stack">
      <h1>{household.data.name}</h1>
      <nav className="tabs">
        <NavLink to="overview">Áttekintés</NavLink>
        <NavLink to="deadlines">Határidők</NavLink>
        <NavLink to="vehicles">Járművek és kötvények</NavLink>
        <NavLink to="members">Tagok</NavLink>
        <NavLink to="sharing">Megosztás</NavLink>
      </nav>
      <Outlet context={{ household: household.data } satisfies HouseholdContext} />
    </div>
  );
}
