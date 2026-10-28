import { useState, type FormEvent } from 'react';
import * as api from '../api/endpoints';
import type { Deadline, DeadlineType, Member, Policy, Vehicle } from '../api/types';
import { DeadlineBadge } from '../components/DeadlineBadge';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';
import { deadlineStatusLabels, deadlineTypeLabels, dueDateOptional, policyTypeLabels, requiredLink } from '../labels';
import { useHousehold } from './HouseholdPage';

export function DeadlinesTab() {
  const household = useHousehold();
  const [filter, setFilter] = useState<api.DeadlineFilter>({ status: 'OPEN', type: '', from: '', to: '', page: 0 });
  const deadlines = useLoad(
    () => api.searchDeadlines(household.id, filter),
    [household.id, filter.status, filter.type, filter.from, filter.to, filter.page],
  );
  const vehicles = useLoad(() => api.listVehicles(household.id), [household.id]);
  const members = useLoad(() => api.listMembers(household.id), [household.id]);
  // Háztartásszintű kötvénylista nincs, ezért járművenként kérjük le (párhuzamosan).
  const policies = useLoad(async () => {
    const page = await api.listVehicles(household.id);
    const perVehicle = await Promise.all(page.content.map((vehicle) => api.listPolicies(vehicle.id)));
    return perVehicle.flat();
  }, [household.id]);

  const [notice, setNotice] = useState<string | null>(null);
  const updateFilter = (change: Partial<api.DeadlineFilter>) => setFilter((previous) => ({ ...previous, ...change, page: 0 }));
  // A teljesített sor a "Nyitott" szűrésből eltűnik, ezért a visszajelzés a lista fölött jelenik meg.
  const onRowChanged = (message?: string) => {
    setNotice(message ?? null);
    deadlines.reload();
  };

  const describeLinks = (deadline: Deadline) => {
    const vehicle = vehicles.data?.content.find((v) => v.id === deadline.vehicleId);
    const member = members.data?.find((m) => m.id === deadline.memberId);
    return [vehicle?.licensePlate, member?.name].filter(Boolean).join(' · ');
  };

  return (
    <div className="stack">
      <section className="card">
        <div className="filters">
          <label>
            Állapot
            <select value={filter.status} onChange={(e) => updateFilter({ status: e.target.value as api.DeadlineFilter['status'] })}>
              <option value="">mind</option>
              {Object.entries(deadlineStatusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Típus
            <select value={filter.type} onChange={(e) => updateFilter({ type: e.target.value as api.DeadlineFilter['type'] })}>
              <option value="">mind</option>
              {Object.entries(deadlineTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Ettől
            <input type="date" value={filter.from} onChange={(e) => updateFilter({ from: e.target.value })} />
          </label>
          <label>
            Eddig
            <input type="date" value={filter.to} onChange={(e) => updateFilter({ to: e.target.value })} />
          </label>
        </div>

        <ProblemAlert error={deadlines.error} />
        {notice && <div className="notice">{notice}</div>}
        {deadlines.data?.content.length === 0 && <p className="muted">Nincs a szűrésnek megfelelő határidő.</p>}
        <ul className="list">
          {deadlines.data?.content.map((deadline) => (
            <DeadlineRow key={deadline.id} deadline={deadline} links={describeLinks(deadline)} onChanged={onRowChanged} />
          ))}
        </ul>

        {deadlines.data && deadlines.data.totalPages > 1 && (
          <div className="pager">
            <button
              type="button"
              className="button secondary"
              disabled={deadlines.data.page === 0}
              onClick={() => setFilter((previous) => ({ ...previous, page: (previous.page ?? 0) - 1 }))}
            >
              ← Előző
            </button>
            <span className="muted">
              {deadlines.data.page + 1}. / {deadlines.data.totalPages} oldal ({deadlines.data.totalElements} db)
            </span>
            <button
              type="button"
              className="button secondary"
              disabled={deadlines.data.page + 1 >= deadlines.data.totalPages}
              onClick={() => setFilter((previous) => ({ ...previous, page: (previous.page ?? 0) + 1 }))}
            >
              Következő →
            </button>
          </div>
        )}
      </section>

      <NewDeadlineForm
        householdId={household.id}
        vehicles={vehicles.data?.content ?? []}
        members={members.data ?? []}
        policies={policies.data ?? []}
        onCreated={deadlines.reload}
      />
    </div>
  );
}

function DeadlineRow({
  deadline,
  links,
  onChanged,
}: {
  deadline: Deadline;
  links: string;
  onChanged: (message?: string) => void;
}) {
  const [completing, setCompleting] = useState(false);
  const [nextDueDate, setNextDueDate] = useState('');
  const label = deadline.title ?? deadlineTypeLabels[deadline.type];

  const complete = useAction(async () => {
    const result = await api.completeDeadline(deadline.id, nextDueDate || null);
    setCompleting(false);
    onChanged(
      result.next
        ? `„${label}” teljesítve. Következő határidő létrehozva: ${result.next.dueDate}.`
        : `„${label}” teljesítve.`,
    );
  });
  const dismiss = useAction(async () => {
    await api.dismissDeadline(deadline.id);
    onChanged(`„${label}” elvetve.`);
  });

  const onCompleteSubmit = (event: FormEvent) => {
    event.preventDefault();
    void complete.run();
  };

  return (
    <li className="list-row wrap">
      <DeadlineBadge deadline={deadline} />
      <span className="grow">
        <strong>{deadline.title ?? deadlineTypeLabels[deadline.type]}</strong>
        <div className="muted small">
          {deadline.title && `${deadlineTypeLabels[deadline.type]} · `}
          esedékes: {deadline.dueDate}
          {links && ` · ${links}`}
          {deadline.note && ` · ${deadline.note}`}
        </div>
      </span>
      {deadline.status === 'OPEN' && !completing && (
        <span className="actions">
          <button type="button" className="button small-button" onClick={() => setCompleting(true)}>
            Teljesítve
          </button>
          <button type="button" className="button secondary small-button" onClick={() => void dismiss.run()} disabled={dismiss.pending}>
            Elvetés
          </button>
        </span>
      )}
      {completing && (
        <form onSubmit={onCompleteSubmit} className="inline-form complete-form">
          <label className="inline">
            Következő esedékesség (nem kötelező)
            <input type="date" value={nextDueDate} onChange={(e) => setNextDueDate(e.target.value)} />
          </label>
          <button type="submit" className="button small-button" disabled={complete.pending}>
            Rögzítés
          </button>
          <button type="button" className="link-button" onClick={() => setCompleting(false)}>
            Mégse
          </button>
        </form>
      )}
      <div className="full">
        <ProblemAlert error={complete.error} />
        <ProblemAlert error={dismiss.error} />
      </div>
    </li>
  );
}

function NewDeadlineForm({
  householdId,
  vehicles,
  members,
  policies,
  onCreated,
}: {
  householdId: number;
  vehicles: Vehicle[];
  members: Member[];
  policies: Policy[];
  onCreated: () => void;
}) {
  const [type, setType] = useState<DeadlineType>('TECHNICAL_INSPECTION');
  const [dueDate, setDueDate] = useState('');
  const [title, setTitle] = useState('');
  const [note, setNote] = useState('');
  const [linkId, setLinkId] = useState('');
  const link = requiredLink[type];

  const create = useAction(async () => {
    const id = linkId === '' ? null : Number(linkId);
    await api.createDeadline(householdId, {
      type,
      dueDate: dueDate === '' ? null : dueDate,
      title: title.trim() === '' ? null : title,
      note: note.trim() === '' ? null : note,
      vehicleId: link === 'vehicle' ? id : null,
      memberId: link === 'member' ? id : null,
      policyId: link === 'policy' ? id : null,
    });
    setDueDate('');
    setTitle('');
    setNote('');
    onCreated();
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void create.run();
  };

  const plateOf = (vehicleId: number) => vehicles.find((vehicle) => vehicle.id === vehicleId)?.licensePlate ?? `#${vehicleId}`;

  return (
    <form onSubmit={onSubmit} className="card form">
      <h3>Új határidő</h3>
      <div className="form-grid">
        <label>
          Típus
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value as DeadlineType);
              setLinkId('');
            }}
          >
            {Object.entries(deadlineTypeLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        {link === 'vehicle' && (
          <label>
            Jármű
            <select value={linkId} onChange={(e) => setLinkId(e.target.value)} required>
              <option value="">– válassz –</option>
              {vehicles.map((vehicle) => (
                <option key={vehicle.id} value={vehicle.id}>
                  {vehicle.licensePlate} – {vehicle.brand} {vehicle.model}
                </option>
              ))}
            </select>
          </label>
        )}
        {link === 'member' && (
          <label>
            Családtag
            <select value={linkId} onChange={(e) => setLinkId(e.target.value)} required>
              <option value="">– válassz –</option>
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {link === 'policy' && (
          <label>
            Kötvény
            <select value={linkId} onChange={(e) => setLinkId(e.target.value)} required>
              <option value="">– válassz –</option>
              {policies.map((policy) => (
                <option key={policy.id} value={policy.id}>
                  {plateOf(policy.vehicleId)} – {policyTypeLabels[policy.policyType]}, {policy.insurerName}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Esedékesség {dueDateOptional.has(type) && <span className="muted small">(üresen: automatikus)</span>}
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required={!dueDateOptional.has(type)} />
        </label>
        <label>
          Cím (nem kötelező)
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
        </label>
        <label className="span-2">
          Megjegyzés
          <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={2000} />
        </label>
      </div>
      <ProblemAlert error={create.error} />
      <button type="submit" className="button" disabled={create.pending}>
        Határidő felvétele
      </button>
    </form>
  );
}
