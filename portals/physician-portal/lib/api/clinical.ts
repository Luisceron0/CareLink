import type {
  ClinicalEncounter,
  ClinicalEncounterWriteInput,
  ClinicalPatient,
  ClinicalPatientCreateRequest,
  Icd10SearchResult
} from '../types';

const CLINICAL_BASE = 'http://localhost:8082/api/v1';

type Role = 'PHYSICIAN' | 'TENANT_ADMIN' | 'RECEPTIONIST';

export async function getPatient(
  patientId: string,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<ClinicalPatient> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(`${CLINICAL_BASE}/patients/${patientId}`, {
    headers,
    cache: 'no-store'
  });
  if (!res.ok) {
    throw new Error('CLINICAL_GET_PATIENT_FAILED');
  }
  return (await res.json()) as ClinicalPatient;
}

export async function createPatient(
  payload: ClinicalPatientCreateRequest,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<ClinicalPatient> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(`${CLINICAL_BASE}/patients`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    cache: 'no-store'
  });
  if (!res.ok) {
    throw new Error('CLINICAL_CREATE_PATIENT_FAILED');
  }
  return (await res.json()) as ClinicalPatient;
}

export async function createEncounter(
  patientId: string,
  input: ClinicalEncounterWriteInput,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<ClinicalEncounter> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(`${CLINICAL_BASE}/patients/${patientId}/encounters`, {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    cache: 'no-store'
  });
  if (!res.ok) {
    throw new Error('CLINICAL_CREATE_ENCOUNTER_FAILED');
  }
  return (await res.json()) as ClinicalEncounter;
}

export async function getEncounter(
  patientId: string,
  encounterId: string,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<ClinicalEncounter> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(
    `${CLINICAL_BASE}/patients/${patientId}/encounters/${encounterId}`,
    {
      headers,
      cache: 'no-store'
    }
  );
  if (!res.ok) {
    throw new Error('CLINICAL_GET_ENCOUNTER_FAILED');
  }
  return (await res.json()) as ClinicalEncounter;
}

export async function updateEncounter(
  patientId: string,
  encounterId: string,
  input: ClinicalEncounterWriteInput,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<ClinicalEncounter> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(
    `${CLINICAL_BASE}/patients/${patientId}/encounters/${encounterId}`,
    {
      method: 'PUT',
      headers,
      body: JSON.stringify(input),
      cache: 'no-store'
    }
  );

  if (res.status === 409) {
    throw new Error('CLINICAL_ENCOUNTER_IMMUTABLE');
  }

  if (!res.ok) {
    throw new Error('CLINICAL_UPDATE_ENCOUNTER_FAILED');
  }

  return (await res.json()) as ClinicalEncounter;
}

export async function signEncounter(
  patientId: string,
  encounterId: string,
  cookieHeader?: string,
  role: Role = 'PHYSICIAN'
): Promise<void> {
  const headers = buildHeaders(cookieHeader, role);
  const res = await fetch(
    `${CLINICAL_BASE}/patients/${patientId}/encounters/${encounterId}/sign`,
    {
      method: 'POST',
      headers,
      cache: 'no-store'
    }
  );

  if (!res.ok) {
    throw new Error('CLINICAL_SIGN_ENCOUNTER_FAILED');
  }
}

export function searchIcd10(query: string): Icd10SearchResult[] {
  const normalized = normalizeDiagnosisQuery(query);
  if (!normalized) {
    return [];
  }

  return ICD10_CATALOG.filter(item => {
    return (
      item.code.includes(normalized)
      || item.description.toLowerCase().includes(normalized.toLowerCase())
    );
  }).slice(0, 8);
}

export function normalizeIcd10Code(raw: string): string {
  return raw.trim().toUpperCase();
}

function normalizeDiagnosisQuery(raw: string): string {
  return raw.trim();
}

function buildHeaders(cookieHeader?: string, role: Role = 'PHYSICIAN') {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-User-Role': role
  };

  if (!cookieHeader) {
    return headers;
  }

  headers.cookie = cookieHeader;
  const tenant = parseCookieValue(
    cookieHeader,
    ['X-Tenant-Id', 'x-tenant-id', 'tenantId', 'tenant_id', 'tenant']
  );
  const userId = parseCookieValue(
    cookieHeader,
    ['X-User-Id', 'x-user-id', 'userId', 'user_id']
  );

  if (tenant) {
    headers['X-Tenant-Id'] = tenant;
  }
  if (userId) {
    headers['X-User-Id'] = userId;
  }

  return headers;
}

function parseCookieValue(
  cookieHeader: string,
  acceptedNames: string[]
): string | null {
  const names = new Set(acceptedNames.map(name => name.toLowerCase()));
  const parts = cookieHeader.split(';').map(part => part.trim());
  for (const part of parts) {
    const eqIndex = part.indexOf('=');
    if (eqIndex < 0) {
      continue;
    }
    const key = decodeURIComponent(part.slice(0, eqIndex).trim()).toLowerCase();
    if (!names.has(key)) {
      continue;
    }
    return decodeURIComponent(part.slice(eqIndex + 1).trim());
  }
  return null;
}

const ICD10_CATALOG: Icd10SearchResult[] = [
  { code: 'A09', description: 'Infectious gastroenteritis and colitis' },
  { code: 'E11', description: 'Type 2 diabetes mellitus' },
  { code: 'I10', description: 'Essential (primary) hypertension' },
  { code: 'J06.9', description: 'Acute upper respiratory infection, unspecified' },
  { code: 'J45.9', description: 'Asthma, unspecified' },
  { code: 'K21.9', description: 'Gastro-esophageal reflux disease without esophagitis' },
  { code: 'M54.5', description: 'Low back pain' },
  { code: 'R51', description: 'Headache' }
];
