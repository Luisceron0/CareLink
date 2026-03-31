export interface AvailabilityBlock {
  id?: string;
  physicianId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export interface Appointment {
  id: string;
  tenantId: string;
  physicianId: string;
  patientId: string;
  slotStart: string;
  durationMinutes: number;
  status: AppointmentStatus;
}

export interface CreateAppointmentRequest {
  physicianId: string;
  patientId: string;
  slotStart: string;
  durationMinutes: number;
}

export class SlotConflictError extends Error {
  alternatives: string[];

  constructor(message: string, alternatives: string[]) {
    super(message);
    this.name = 'SlotConflictError';
    this.alternatives = alternatives;
  }
}

export interface ClinicalPatient {
  id: string;
  tenantId: string;
  fullName: string;
  documentType: string;
  documentValue: string;
  bloodType: string;
  phone: string;
  email: string;
  emergencyContact: string;
  createdAt: string;
  allergies?: string[];
  activeMedications?: string[];
}

export interface ClinicalPatientCreateRequest {
  fullName: string;
  documentType: string;
  documentValue: string;
  bloodType: string;
  phone: string;
  email: string;
  emergencyContact: string;
}

export interface ClinicalEncounter {
  id: string;
  tenantId: string;
  patientId: string;
  physicianId: string;
  chiefComplaint: string;
  physicalExam: string;
  treatmentPlan: string;
  followUpInstructions: string;
  signedAt: string | null;
  createdAt: string;
}

export interface ClinicalEncounterWriteInput {
  chiefComplaint: string;
  physicalExam: string;
  treatmentPlan: string;
  followUpInstructions: string;
}

export interface Icd10SearchResult {
  code: string;
  description: string;
}
