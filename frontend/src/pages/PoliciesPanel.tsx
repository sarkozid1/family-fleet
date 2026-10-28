import { useState, type FormEvent } from 'react';
import * as api from '../api/endpoints';
import type { PaymentFrequency, PolicyType } from '../api/types';
import { ProblemAlert } from '../components/ProblemAlert';
import { useAction, useLoad } from '../hooks';
import { formatMoney, paymentFrequencyLabels, policyTypeLabels } from '../labels';

export function PoliciesPanel({ vehicleId }: { vehicleId: number }) {
  const policies = useLoad(() => api.listPolicies(vehicleId), [vehicleId]);
  const insurers = useLoad(() => api.listInsurers(), []);

  const empty = {
    insurerId: '',
    policyType: 'KGFB' as PolicyType,
    policyNumber: '',
    startDate: '',
    endDate: '',
    annualPremium: '',
    paymentFrequency: 'ANNUAL' as PaymentFrequency,
  };
  const [form, setForm] = useState(empty);
  const set = (field: keyof typeof empty, value: string) => setForm((previous) => ({ ...previous, [field]: value }));

  const create = useAction(async () => {
    await api.createPolicy(vehicleId, {
      insurerId: Number(form.insurerId),
      policyType: form.policyType,
      policyNumber: form.policyNumber.trim() === '' ? null : form.policyNumber,
      startDate: form.startDate,
      endDate: form.endDate === '' ? null : form.endDate,
      annualPremium: Number(form.annualPremium),
      paymentFrequency: form.paymentFrequency,
    });
    setForm(empty);
    policies.reload();
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void create.run();
  };

  return (
    <div className="subpanel">
      <ProblemAlert error={policies.error} />
      {policies.data?.length === 0 && <p className="muted small">Nincs kötvény.</p>}
      <ul className="list">
        {policies.data?.map((policy) => (
          <li key={policy.id} className="list-row">
            <span className={`badge ${policy.active ? 'ok' : 'neutral'}`}>{policy.active ? 'aktív' : 'nem aktív'}</span>
            <span className="grow">
              <strong>{policyTypeLabels[policy.policyType]}</strong> · {policy.insurerName}
              {policy.policyNumber && <span className="muted"> · {policy.policyNumber}</span>}
            </span>
            <span className="muted small">
              {policy.startDate} – {policy.endDate ?? 'határozatlan'} · {formatMoney(policy.annualPremium)}/év,{' '}
              {paymentFrequencyLabels[policy.paymentFrequency]}
            </span>
          </li>
        ))}
      </ul>

      <form onSubmit={onSubmit} className="form">
        <h4>Új kötvény</h4>
        <p className="muted small">
          Létrehozáskor a rendszer generálja a következő díjfizetést, KGFB-nél az évfordulót is (felmondási
          határidővel). Egy járműnek egyszerre csak egy érvényes KGFB-je lehet.
        </p>
        <div className="form-grid">
          <label>
            Biztosító
            <select value={form.insurerId} onChange={(e) => set('insurerId', e.target.value)} required>
              <option value="">– válassz –</option>
              {insurers.data?.map((insurer) => (
                <option key={insurer.id} value={insurer.id}>
                  {insurer.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Típus
            <select value={form.policyType} onChange={(e) => set('policyType', e.target.value)}>
              <option value="KGFB">KGFB</option>
              <option value="CASCO">CASCO</option>
            </select>
          </label>
          <label>
            Kötvényszám
            <input value={form.policyNumber} onChange={(e) => set('policyNumber', e.target.value)} maxLength={50} />
          </label>
          <label>
            Kezdete
            <input type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} required />
          </label>
          <label>
            Vége (nem kötelező)
            <input type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} />
          </label>
          <label>
            Éves díj (Ft)
            <input type="number" min={0} step="0.01" value={form.annualPremium} onChange={(e) => set('annualPremium', e.target.value)} required />
          </label>
          <label>
            Fizetés
            <select value={form.paymentFrequency} onChange={(e) => set('paymentFrequency', e.target.value)}>
              {Object.entries(paymentFrequencyLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        <ProblemAlert error={create.error} />
        <button type="submit" className="button secondary" disabled={create.pending}>
          Kötvény felvétele
        </button>
      </form>
    </div>
  );
}
