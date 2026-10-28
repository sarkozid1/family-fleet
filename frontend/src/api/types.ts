// A backend record DTO-inak TypeScript-megfelelői (a JSON-mezőnevek egyeznek).

export type Role = 'USER' | 'ADMIN';
export type VehicleType = 'CAR' | 'MOTORCYCLE';
export type PolicyType = 'KGFB' | 'CASCO';
export type PaymentFrequency = 'ANNUAL' | 'SEMI_ANNUAL' | 'QUARTERLY' | 'MONTHLY';
export type DeadlineStatus = 'OPEN' | 'DONE' | 'DISMISSED';
export type DeadlineType =
  | 'TECHNICAL_INSPECTION'
  | 'INSURANCE_PAYMENT'
  | 'KGFB_ANNIVERSARY'
  | 'VEHICLE_TAX'
  | 'MOTORWAY_VIGNETTE'
  | 'OIL_CHANGE'
  | 'TIRE_CHANGE'
  | 'DRIVING_LICENCE'
  | 'OTHER';

/** RFC 9457 ProblemDetail, a backend bővítményeivel (errors, requestId). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  requestId?: string;
  errors?: { field: string; message: string }[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  createdAt: string;
}

export interface Household {
  id: number;
  name: string;
  createdAt: string;
}

export interface Member {
  id: number;
  householdId: number;
  name: string;
  email: string | null;
  createdAt: string;
}

export interface Vehicle {
  id: number;
  householdId: number;
  ownerId: number | null;
  type: VehicleType;
  brand: string;
  model: string;
  licensePlate: string;
  vin: string | null;
  productionYear: number | null;
  currentMileage: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Insurer {
  id: number;
  name: string;
  phone: string | null;
  website: string | null;
}

export interface Policy {
  id: number;
  vehicleId: number;
  insurerId: number;
  insurerName: string;
  policyType: PolicyType;
  policyNumber: string | null;
  startDate: string;
  endDate: string | null;
  annualPremium: number;
  paymentFrequency: PaymentFrequency;
  active: boolean;
  version: number;
}

export interface Deadline {
  id: number;
  householdId: number;
  vehicleId: number | null;
  memberId: number | null;
  policyId: number | null;
  type: DeadlineType;
  title: string | null;
  dueDate: string;
  status: DeadlineStatus;
  daysUntilDue: number;
  completedAt: string | null;
  note: string | null;
  version: number;
}

export interface CompleteDeadlineResponse {
  completed: Deadline;
  next: Deadline | null;
}
