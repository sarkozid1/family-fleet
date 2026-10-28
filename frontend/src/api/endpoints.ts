import { apiRequest, queryString } from './client';
import type {
  CompleteDeadlineResponse,
  Deadline,
  DeadlineStatus,
  DeadlineType,
  Household,
  Insurer,
  Member,
  PageResponse,
  PaymentFrequency,
  Policy,
  PolicyType,
  TokenResponse,
  User,
  Vehicle,
  VehicleType,
} from './types';

// --- Hitelesítés ---------------------------------------------------------------------------

export const register = (body: { email: string; password: string; displayName: string }) =>
  apiRequest<User>('POST', '/auth/register', body);

export const login = (body: { email: string; password: string }) =>
  apiRequest<TokenResponse>('POST', '/auth/login', body);

export const me = () => apiRequest<User>('GET', '/auth/me');

// --- Háztartások és tagok -----------------------------------------------------------------

export const listHouseholds = () => apiRequest<Household[]>('GET', '/households');

export const getHousehold = (id: number) => apiRequest<Household>('GET', `/households/${id}`);

export const createHousehold = (name: string) => apiRequest<Household>('POST', '/households', { name });

export const renameHousehold = (id: number, name: string) =>
  apiRequest<Household>('PUT', `/households/${id}`, { name });

export const listMembers = (householdId: number) =>
  apiRequest<Member[]>('GET', `/households/${householdId}/members`);

export const addMember = (householdId: number, body: { name: string; email: string | null }) =>
  apiRequest<Member>('POST', `/households/${householdId}/members`, body);

// --- Járművek -----------------------------------------------------------------------------

export interface VehicleCreate {
  type: VehicleType;
  brand: string;
  model: string;
  licensePlate: string;
  vin: string | null;
  productionYear: number | null;
  currentMileage: number | null;
  ownerId: number | null;
  generateTaxDeadline: boolean;
}

export type VehicleUpdate = Omit<VehicleCreate, 'generateTaxDeadline' | 'currentMileage'> & {
  currentMileage: number;
  version: number;
};

export const listVehicles = (householdId: number) =>
  apiRequest<PageResponse<Vehicle>>('GET', `/households/${householdId}/vehicles${queryString({ size: 100 })}`);

export const createVehicle = (householdId: number, body: VehicleCreate) =>
  apiRequest<Vehicle>('POST', `/households/${householdId}/vehicles`, body);

export const updateVehicle = (id: number, body: VehicleUpdate) =>
  apiRequest<Vehicle>('PUT', `/vehicles/${id}`, body);

export const deleteVehicle = (id: number) => apiRequest<void>('DELETE', `/vehicles/${id}`);

// --- Biztosítók és kötvények --------------------------------------------------------------

export const listInsurers = () => apiRequest<Insurer[]>('GET', '/insurers');

export interface PolicyCreate {
  insurerId: number;
  policyType: PolicyType;
  policyNumber: string | null;
  startDate: string;
  endDate: string | null;
  annualPremium: number;
  paymentFrequency: PaymentFrequency;
}

export const listPolicies = (vehicleId: number) => apiRequest<Policy[]>('GET', `/vehicles/${vehicleId}/policies`);

export const createPolicy = (vehicleId: number, body: PolicyCreate) =>
  apiRequest<Policy>('POST', `/vehicles/${vehicleId}/policies`, body);

// --- Határidők ----------------------------------------------------------------------------

export interface DeadlineFilter {
  status?: DeadlineStatus | '';
  type?: DeadlineType | '';
  from?: string;
  to?: string;
  page?: number;
}

export const searchDeadlines = (householdId: number, filter: DeadlineFilter) =>
  apiRequest<PageResponse<Deadline>>(
    'GET',
    `/households/${householdId}/deadlines${queryString({ ...filter, size: 20 })}`,
  );

export const upcomingDeadlines = (householdId: number, days: number) =>
  apiRequest<Deadline[]>('GET', `/households/${householdId}/deadlines/upcoming${queryString({ days })}`);

export interface DeadlineCreate {
  type: DeadlineType;
  dueDate: string | null;
  title: string | null;
  note: string | null;
  vehicleId: number | null;
  memberId: number | null;
  policyId: number | null;
}

export const createDeadline = (householdId: number, body: DeadlineCreate) =>
  apiRequest<Deadline>('POST', `/households/${householdId}/deadlines`, body);

export const completeDeadline = (id: number, nextDueDate: string | null) =>
  apiRequest<CompleteDeadlineResponse>('POST', `/deadlines/${id}/complete`, nextDueDate ? { nextDueDate } : undefined);

export const dismissDeadline = (id: number) => apiRequest<Deadline>('POST', `/deadlines/${id}/dismiss`);
