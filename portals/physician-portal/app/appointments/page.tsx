import React from 'react';
import { cookies } from 'next/headers';
import { listAppointments } from '../../lib/api/scheduling';
import { loadMessages } from '../../lib/i18n';
import AppointmentsClient from './appointments-client';
import type { Appointment } from '../../lib/types';

export default async function AppointmentsPage() {
  const cookieStore = cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map(c => `${c.name}=${c.value}`)
    .join('; ');
  const locale = cookieStore.get('locale')?.value ?? 'es-CO';
  const messages = loadMessages(locale);

  const tenantId =
    cookieStore.get('X-Tenant-Id')?.value
    ?? cookieStore.get('tenantId')?.value
    ?? '';

  let items: Appointment[] = [];
  let error = '';
  try {
    items = await listAppointments({}, cookieHeader);
  } catch (e) {
    error = messages['error.generic'] ?? 'Error';
  }

  return (
    <div style={{ padding: 16 }}>
      <h1>{messages['appointments.title'] ?? 'Citas'}</h1>
      {error ? <p style={{ color: 'red' }}>{error}</p> : null}
      <AppointmentsClient initialItems={items} tenantId={tenantId} />
    </div>
  );
}
