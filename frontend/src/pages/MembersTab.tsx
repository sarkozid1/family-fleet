import { useState, type FormEvent } from 'react';
import * as api from '../api/endpoints';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';
import { useHousehold } from './HouseholdPage';

export function MembersTab() {
  const household = useHousehold();
  const members = useLoad(() => api.listMembers(household.id), [household.id]);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');

  const add = useAction(async () => {
    await api.addMember(household.id, { name, email: email.trim() === '' ? null : email });
    setName('');
    setEmail('');
    members.reload();
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void add.run();
  };

  return (
    <div className="stack">
      <section className="card">
        <h2>Családtagok</h2>
        <p className="muted small">Az e-mail-címmel rendelkező tagok kapják a napi emlékeztetőt.</p>
        <ProblemAlert error={members.error} />
        {members.data?.length === 0 && <p className="muted">Még nincs tag.</p>}
        <ul className="list">
          {members.data?.map((member) => (
            <li key={member.id} className="list-row">
              <span className="grow">
                <strong>{member.name}</strong>
              </span>
              <span className="muted">{member.email ?? 'nincs e-mail'}</span>
            </li>
          ))}
        </ul>
      </section>

      <form onSubmit={onSubmit} className="card form inline-form">
        <h3>Új tag</h3>
        <label>
          Név
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
        </label>
        <label>
          E-mail (nem kötelező)
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={255} />
        </label>
        <button type="submit" className="button" disabled={add.pending}>
          Hozzáadás
        </button>
        <ProblemAlert error={add.error} />
      </form>
    </div>
  );
}
