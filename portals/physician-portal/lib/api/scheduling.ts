/**
 * Wrapper para llamadas al backend de scheduling.
 * Todas las llamadas deben pasar por aquí y reciben la cookie del request.
 */
import type { AvailabilityBlock } from '../types';

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
