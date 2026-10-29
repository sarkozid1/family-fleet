import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/endpoints';
import type { SharingEntry } from '../api/types';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';
import { useHousehold } from './HouseholdPage';

export function SharingTab() {
  const household = useHousehold();
  const navigate = useNavigate();
  const entries = useLoad(() => api.listSharing(household.id), [household.id]);
  const [email, setEmail] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const share = useAction(async () => {
    const created = await api.shareHousehold(household.id, email);
    setNotice(
      created.status === 'ACTIVE'
        ? `${created.email} mostantól eléri ezt a háztartást.`
        : `Meghívó létrehozva: ${created.email}. Amint ezzel a címmel regisztrál, automatikusan eléri a háztartást.`,
    );
    setEmail('');
    entries.reload();
  });

  const revoke = useAction(async (entry: SharingEntry) => {
    const who = entry.displayName ?? entry.email;
    if (!window.confirm(`Biztosan visszavonod: ${who}?`)) {
      return;
    }
    if (entry.status === 'ACTIVE' && entry.userId !== null) {
      await api.revokeUserAccess(household.id, entry.userId);
    } else if (entry.invitationId !== null) {
      await api.revokeInvitation(household.id, entry.invitationId);
    }
    if (entry.you) {
      // A saját hozzáférés visszavonása után a háztartás már nem elérhető (404), ezért vissza a listához.
      navigate('/');
      return;
    }
    setNotice(`${who} hozzáférése visszavonva.`);
    entries.reload();
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void share.run();
  };

  return (
    <div className="stack">
      <section className="card">
        <h2>Kik érik el ezt a háztartást?</h2>
        <p className="muted small">
          Mindenki, aki hozzáfér, ugyanazokat az adatokat látja és szerkesztheti. Akinek még nincs fiókja, meghívót kap:
          ezzel az e-mail-címmel tud regisztrálni.
        </p>
        <ProblemAlert error={entries.error} />
        <ProblemAlert error={revoke.error} />
        {notice && <div className="notice">{notice}</div>}
        <ul className="list">
          {entries.data?.map((entry) => (
            <li key={`${entry.status}-${entry.userId ?? entry.invitationId}`} className="list-row">
              <span className={`badge ${entry.status === 'ACTIVE' ? 'ok' : 'soon'}`}>
                {entry.status === 'ACTIVE' ? 'hozzáfér' : 'meghívva'}
              </span>
              <span className="grow">
                <strong>{entry.displayName ?? entry.email}</strong>
                {entry.you && <span className="muted"> (te)</span>}
                {entry.displayName && <div className="muted small">{entry.email}</div>}
              </span>
              <button
                type="button"
                className="button danger small-button"
                onClick={() => void revoke.run(entry)}
                disabled={revoke.pending}
              >
                Visszavonás
              </button>
            </li>
          ))}
        </ul>
      </section>

      <form onSubmit={onSubmit} className="card form inline-form">
        <h3>Megosztás</h3>
        <label>
          E-mail-cím
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={255} />
        </label>
        <button type="submit" className="button" disabled={share.pending}>
          Megosztás
        </button>
        <ProblemAlert error={share.error} />
      </form>
    </div>
  );
}
