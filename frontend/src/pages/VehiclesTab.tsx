import { useState, type FormEvent } from 'react';
import * as api from '../api/endpoints';
import type { Member, Vehicle, VehicleType } from '../api/types';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';
import { formatNumber, vehicleTypeLabels } from '../labels';
import { useHousehold } from './HouseholdPage';
import { PoliciesPanel } from './PoliciesPanel';

export function VehiclesTab() {
  const household = useHousehold();
  const vehicles = useLoad(() => api.listVehicles(household.id), [household.id]);
  const members = useLoad(() => api.listMembers(household.id), [household.id]);

  return (
    <div className="stack">
      <ProblemAlert error={vehicles.error} />
      {vehicles.data?.content.length === 0 && <p className="muted">Még nincs jármű.</p>}
      {vehicles.data?.content.map((vehicle) => (
        <VehicleCard
          key={vehicle.id}
          vehicle={vehicle}
          members={members.data ?? []}
          onChanged={vehicles.reload}
        />
      ))}
      <NewVehicleForm householdId={household.id} members={members.data ?? []} onCreated={vehicles.reload} />
    </div>
  );
}

function VehicleCard({ vehicle, members, onChanged }: { vehicle: Vehicle; members: Member[]; onChanged: () => void }) {
  const [mileage, setMileage] = useState(String(vehicle.currentMileage));
  const [showPolicies, setShowPolicies] = useState(false);
  const owner = members.find((member) => member.id === vehicle.ownerId);

  // A PUT a teljes járművet és a letöltött verziót várja; ha közben más módosította, 409 jön.
  const updateMileage = useAction(async () => {
    await api.updateVehicle(vehicle.id, {
      type: vehicle.type,
      brand: vehicle.brand,
      model: vehicle.model,
      licensePlate: vehicle.licensePlate,
      vin: vehicle.vin,
      productionYear: vehicle.productionYear,
      ownerId: vehicle.ownerId,
      currentMileage: Number(mileage),
      version: vehicle.version,
    });
    onChanged();
  });

  const remove = useAction(async () => {
    if (window.confirm(`Biztosan törlöd: ${vehicle.licensePlate}?`)) {
      await api.deleteVehicle(vehicle.id);
      onChanged();
    }
  });

  const onMileageSubmit = (event: FormEvent) => {
    event.preventDefault();
    void updateMileage.run();
  };

  return (
    <section className="card">
      <div className="section-header">
        <div>
          <h2>
            <span className="plate">{vehicle.licensePlate}</span> {vehicle.brand} {vehicle.model}
          </h2>
          <span className="muted small">
            {vehicleTypeLabels[vehicle.type]}
            {vehicle.productionYear && ` · ${vehicle.productionYear}`}
            {owner && ` · tulajdonos: ${owner.name}`}
            {vehicle.vin && ` · VIN: ${vehicle.vin}`}
          </span>
        </div>
        <button type="button" className="button danger small-button" onClick={() => void remove.run()} disabled={remove.pending}>
          Törlés
        </button>
      </div>

      <form onSubmit={onMileageSubmit} className="inline-form">
        <label className="inline">
          Km-óra
          <input type="number" min={0} value={mileage} onChange={(e) => setMileage(e.target.value)} />
          <span className="muted small">jelenleg {formatNumber(vehicle.currentMileage)} km</span>
        </label>
        <button type="submit" className="button secondary" disabled={updateMileage.pending}>
          Mentés
        </button>
      </form>
      <ProblemAlert error={updateMileage.error} />
      <ProblemAlert error={remove.error} />

      <button type="button" className="link-button" onClick={() => setShowPolicies((shown) => !shown)}>
        {showPolicies ? 'Kötvények elrejtése ▲' : 'Kötvények ▼'}
      </button>
      {showPolicies && <PoliciesPanel vehicleId={vehicle.id} />}
    </section>
  );
}

function NewVehicleForm({
  householdId,
  members,
  onCreated,
}: {
  householdId: number;
  members: Member[];
  onCreated: () => void;
}) {
  const empty = {
    type: 'CAR' as VehicleType,
    brand: '',
    model: '',
    licensePlate: '',
    vin: '',
    productionYear: '',
    currentMileage: '',
    ownerId: '',
    generateTaxDeadline: true,
  };
  const [form, setForm] = useState(empty);
  const set = (field: keyof typeof empty, value: string | boolean) => setForm((previous) => ({ ...previous, [field]: value }));

  const create = useAction(async () => {
    await api.createVehicle(householdId, {
      type: form.type,
      brand: form.brand,
      model: form.model,
      licensePlate: form.licensePlate,
      vin: form.vin.trim() === '' ? null : form.vin,
      productionYear: form.productionYear === '' ? null : Number(form.productionYear),
      currentMileage: form.currentMileage === '' ? null : Number(form.currentMileage),
      ownerId: form.ownerId === '' ? null : Number(form.ownerId),
      generateTaxDeadline: form.generateTaxDeadline,
    });
    setForm(empty);
    onCreated();
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void create.run();
  };

  return (
    <form onSubmit={onSubmit} className="card form">
      <h3>Új jármű</h3>
      <div className="form-grid">
        <label>
          Típus
          <select value={form.type} onChange={(e) => set('type', e.target.value)}>
            {Object.entries(vehicleTypeLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Rendszám
          <input value={form.licensePlate} onChange={(e) => set('licensePlate', e.target.value)} required maxLength={10} placeholder="ABC-123 vagy AA-BC-123" />
        </label>
        <label>
          Márka
          <input value={form.brand} onChange={(e) => set('brand', e.target.value)} required maxLength={50} />
        </label>
        <label>
          Modell
          <input value={form.model} onChange={(e) => set('model', e.target.value)} required maxLength={50} />
        </label>
        <label>
          Évjárat
          <input type="number" min={1900} max={2100} value={form.productionYear} onChange={(e) => set('productionYear', e.target.value)} />
        </label>
        <label>
          Km-óra
          <input type="number" min={0} value={form.currentMileage} onChange={(e) => set('currentMileage', e.target.value)} />
        </label>
        <label>
          Alvázszám (VIN)
          <input value={form.vin} onChange={(e) => set('vin', e.target.value)} maxLength={17} />
        </label>
        <label>
          Tulajdonos
          <select value={form.ownerId} onChange={(e) => set('ownerId', e.target.value)}>
            <option value="">– nincs –</option>
            {members.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="checkbox">
        <input type="checkbox" checked={form.generateTaxDeadline} onChange={(e) => set('generateTaxDeadline', e.target.checked)} />
        A következő gépjárműadó-határidő létrehozása (márc. 15. / szept. 15.)
      </label>
      <ProblemAlert error={create.error} />
      <button type="submit" className="button" disabled={create.pending}>
        Jármű felvétele
      </button>
    </form>
  );
}
