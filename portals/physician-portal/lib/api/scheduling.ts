/**
 * Wrapper para llamadas al backend de scheduling.
 * Todas las llamadas deben pasar por aquí y reciben la cookie del request.
 */
import type {
  Appointment,
  AppointmentStatus,
  AvailabilityBlock,
  CreateAppointmentRequest
} from '../types';
import { SlotConflictError } from '../types';

export async function getAvailability(
  physicianId: string,
  cookieHeader?: string
): Promise<AvailabilityBlock[]> {
  const url = `http://localhost:8081/api/v1/physicians/${physicianId}/availability`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };
  if (cookieHeader) {
    headers['cookie'] = cookieHeader;
    // intentar extraer tenant desde la cookie de sesión (server-side)
    const tenant = parseTenantFromCookieHeader(cookieHeader);
    if (tenant) {
      headers['X-Tenant-Id'] = tenant;
    }
  }
  const res = await fetch(url, { headers, cache: 'no-store' });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as AvailabilityBlock[];
}

export async function createAvailability(
  physicianId: string,
  block: AvailabilityBlock,
  cookieHeader?: string
): Promise<AvailabilityBlock> {
  const url = `http://localhost:8081/api/v1/physicians/${physicianId}/availability`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };
  if (cookieHeader) {
    headers['cookie'] = cookieHeader;
    const tenant = parseTenantFromCookieHeader(cookieHeader);
    if (tenant) {
      headers['X-Tenant-Id'] = tenant;
    }
  }
  const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(block) });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as AvailabilityBlock;
}

export async function createAppointment(
  request: CreateAppointmentRequest,
  cookieHeader?: string
): Promise<Appointment> {
  const url = 'http://localhost:8081/api/v1/appointments';
  const headers = buildHeaders(cookieHeader);
  headers['X-User-Role'] = 'RECEPTIONIST';

  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
    cache: 'no-store'
  });
  if (!res.ok) {
    if (res.status === 409) {
      const payload = (await res.json().catch(() => null)) as
        | { alternatives?: string[]; message?: string }
        | null;
      const alternatives = payload?.alternatives ?? [];
      throw new SlotConflictError(
        payload?.message ?? 'Slot already booked',
        alternatives.filter(Boolean)
      );
    }
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as Appointment;
}

export async function listAppointments(
  filters: {
    physicianId?: string;
    date?: string;
    status?: AppointmentStatus;
  },
  cookieHeader?: string
): Promise<Appointment[]> {
  const url = new URL('http://localhost:8081/api/v1/appointments');
  if (filters.physicianId) {
    url.searchParams.set('physicianId', filters.physicianId);
  }
  if (filters.date) {
    url.searchParams.set('date', filters.date);
  }
  if (filters.status) {
    url.searchParams.set('status', filters.status);
  }

  const headers = buildHeaders(cookieHeader);
  const res = await fetch(url.toString(), { headers, cache: 'no-store' });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as Appointment[];
}

export async function updateAppointmentStatus(
  id: string,
  status: AppointmentStatus,
  cookieHeader?: string
): Promise<Appointment> {
  const url = `http://localhost:8081/api/v1/appointments/${id}/status`;
  const headers = buildHeaders(cookieHeader);
  headers['X-User-Role'] =
    status === 'IN_PROGRESS' || status === 'COMPLETED'
      ? 'PHYSICIAN'
      : 'RECEPTIONIST';

  const res = await fetch(url, {
    method: 'PATCH',
    headers,
    body: JSON.stringify({ status }),
    cache: 'no-store'
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as Appointment;
}

export async function cancelAppointment(
  id: string,
  cookieHeader?: string
): Promise<Appointment> {
  const url = `http://localhost:8081/api/v1/appointments/${id}`;
  const headers = buildHeaders(cookieHeader);
  headers['X-User-Role'] = 'RECEPTIONIST';
  const res = await fetch(url, {
    method: 'DELETE',
    headers,
    cache: 'no-store'
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(txt || `HTTP ${res.status}`);
  }
  return (await res.json()) as Appointment;
}

function buildHeaders(cookieHeader?: string): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };

  if (!cookieHeader) {
    return headers;
  }

  headers['cookie'] = cookieHeader;
  const tenant = parseTenantFromCookieHeader(cookieHeader);
  if (tenant) {
    headers['X-Tenant-Id'] = tenant;
  }
  return headers;
}

function parseTenantFromCookieHeader(cookieHeader: string): string | null {
  try {
    const parts = cookieHeader.split(';').map(p => p.trim());
    for (const p of parts) {
      const eq = p.indexOf('=');
      if (eq === -1) continue;
      const k = decodeURIComponent(p.substring(0, eq).trim());
      const v = decodeURIComponent(p.substring(eq + 1).trim());
      if (k === 'X-Tenant-Id' || k.toLowerCase() === 'x-tenant-id') return v;
      if (k === 'tenantId' || k === 'tenant_id' || k === 'tenant') return v;
    }
  } catch (e) {
    // ignore
  }
  return null;
}
