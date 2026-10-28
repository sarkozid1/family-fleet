import type { DeadlineStatus, DeadlineType, PaymentFrequency, PolicyType, VehicleType } from './api/types';

// Az enum-értékek magyar megjelenítése (a backend az angol konstansokat küldi).

export const vehicleTypeLabels: Record<VehicleType, string> = {
  CAR: 'Személyautó',
  MOTORCYCLE: 'Motor',
};

export const policyTypeLabels: Record<PolicyType, string> = {
  KGFB: 'KGFB',
  CASCO: 'CASCO',
};

export const paymentFrequencyLabels: Record<PaymentFrequency, string> = {
  ANNUAL: 'éves',
  SEMI_ANNUAL: 'féléves',
  QUARTERLY: 'negyedéves',
  MONTHLY: 'havi',
};

export const deadlineStatusLabels: Record<DeadlineStatus, string> = {
  OPEN: 'Nyitott',
  DONE: 'Teljesítve',
  DISMISSED: 'Elvetve',
};

export const deadlineTypeLabels: Record<DeadlineType, string> = {
  TECHNICAL_INSPECTION: 'Műszaki vizsga',
  INSURANCE_PAYMENT: 'Biztosítási díj',
  KGFB_ANNIVERSARY: 'KGFB-évforduló',
  VEHICLE_TAX: 'Gépjárműadó',
  MOTORWAY_VIGNETTE: 'Autópálya-matrica',
  OIL_CHANGE: 'Olajcsere',
  TIRE_CHANGE: 'Gumicsere',
  DRIVING_LICENCE: 'Jogosítvány',
  OTHER: 'Egyéb',
};

/** Melyik kapcsolat kötelező a típushoz (a backend DeadlineType.requiredLink() tükre). */
export const requiredLink: Record<DeadlineType, 'vehicle' | 'member' | 'policy' | 'none'> = {
  TECHNICAL_INSPECTION: 'vehicle',
  OIL_CHANGE: 'vehicle',
  TIRE_CHANGE: 'vehicle',
  VEHICLE_TAX: 'vehicle',
  MOTORWAY_VIGNETTE: 'vehicle',
  DRIVING_LICENCE: 'member',
  INSURANCE_PAYMENT: 'policy',
  KGFB_ANNIVERSARY: 'policy',
  OTHER: 'none',
};

/** Ezeknél a backend kiszámolja az esedékességet, ha nincs megadva (ADR-0002). */
export const dueDateOptional: ReadonlySet<DeadlineType> = new Set<DeadlineType>([
  'VEHICLE_TAX',
  'MOTORWAY_VIGNETTE',
  'KGFB_ANNIVERSARY',
  'INSURANCE_PAYMENT',
]);

export function daysLabel(daysUntilDue: number): string {
  if (daysUntilDue === 0) return 'ma';
  if (daysUntilDue === 1) return 'holnap';
  if (daysUntilDue > 1) return `${daysUntilDue} nap múlva`;
  return `lejárt ${-daysUntilDue} napja`;
}

/** Sürgősség a színezéshez: lejárt, 7 napon belül, 30 napon belül, később. */
export function urgency(daysUntilDue: number): 'overdue' | 'soon' | 'upcoming' | 'later' {
  if (daysUntilDue < 0) return 'overdue';
  if (daysUntilDue <= 7) return 'soon';
  if (daysUntilDue <= 30) return 'upcoming';
  return 'later';
}

export const formatNumber = (value: number) => new Intl.NumberFormat('hu-HU').format(value);

export const formatMoney = (value: number) =>
  new Intl.NumberFormat('hu-HU', { style: 'currency', currency: 'HUF', maximumFractionDigits: 0 }).format(value);
