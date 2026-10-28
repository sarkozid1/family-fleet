import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../api/endpoints';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';

export function HouseholdsPage() {
  const navigate = useNavigate();
  const households = useLoad(() => api.listHouseholds(), []);
  const [name, setName] = useState('');

  const create = useAction(async () => {
    const created = await api.createHousehold(name);
    navigate(`/households/${created.id}`);
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void create.run();
  };

  return (
    <div className="stack">
      <h1>Háztartásaim</h1>
      <ProblemAlert error={households.error} />

      {households.data && households.data.length === 0 && (
        <p className="muted">Még nincs háztartásod. Hozz létre egyet lent!</p>
      )}
      <div className="grid">
        {households.data?.map((household) => (
          <Link key={household.id} to={`/households/${household.id}`} className="card household-card">
            <h2>{household.name}</h2>
            <span className="muted small">Létrehozva: {new Date(household.createdAt).toLocaleDateString('hu-HU')}</span>
          </Link>
        ))}
      </div>

      <form onSubmit={onSubmit} className="card form inline-form">
        <h3>Új háztartás</h3>
        <label>
          Név
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} placeholder="pl. Kovács család" />
        </label>
        <button type="submit" className="button" disabled={create.pending}>
          Létrehozás
        </button>
        <ProblemAlert error={create.error} />
      </form>
    </div>
  );
}
