import { useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api/endpoints';
import { DeadlineBadge } from '../components/DeadlineBadge';
import { ProblemAlert } from '../components/ProblemAlert';
import { useLoad } from '../hooks';
import { deadlineTypeLabels } from '../labels';
import { useHousehold } from './HouseholdPage';

export function OverviewTab() {
  const household = useHousehold();
  const [days, setDays] = useState(30);
  const upcoming = useLoad(() => api.upcomingDeadlines(household.id, days), [household.id, days]);
  const vehicles = useLoad(() => api.listVehicles(household.id), [household.id]);
  const members = useLoad(() => api.listMembers(household.id), [household.id]);

  const overdue = upcoming.data?.filter((deadline) => deadline.daysUntilDue < 0).length ?? 0;

  return (
    <div className="stack">
      <div className="stats">
        <div className="card stat">
          <span className="stat-value">{upcoming.data?.length ?? '–'}</span>
          <span className="muted">közelgő határidő ({days} nap)</span>
        </div>
        <div className={`card stat ${overdue > 0 ? 'stat-alert' : ''}`}>
          <span className="stat-value">{upcoming.data ? overdue : '–'}</span>
          <span className="muted">lejárt</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{vehicles.data?.totalElements ?? '–'}</span>
          <span className="muted">jármű</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{members.data?.length ?? '–'}</span>
          <span className="muted">családtag</span>
        </div>
      </div>

      <section className="card">
        <div className="section-header">
          <h2>Közelgő és lejárt határidők</h2>
          <label className="inline">
            Következő
            <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
              <option value={7}>7 nap</option>
              <option value={30}>30 nap</option>
              <option value={90}>90 nap</option>
              <option value={365}>1 év</option>
            </select>
          </label>
        </div>
        <ProblemAlert error={upcoming.error} />
        {upcoming.data?.length === 0 && <p className="muted">Nincs teendő ebben az időszakban. 🎉</p>}
        <ul className="list">
          {upcoming.data?.map((deadline) => (
            <li key={deadline.id} className="list-row">
              <DeadlineBadge deadline={deadline} />
              <span className="grow">
                <strong>{deadline.title ?? deadlineTypeLabels[deadline.type]}</strong>
                {deadline.title && <span className="muted small"> · {deadlineTypeLabels[deadline.type]}</span>}
              </span>
              <span className="muted">{deadline.dueDate}</span>
            </li>
          ))}
        </ul>
        <Link to="../deadlines" className="small">
          Összes határidő kezelése →
        </Link>
      </section>
    </div>
  );
}
