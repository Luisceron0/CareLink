import React from 'react';
import { cookies } from 'next/headers';
import { loadMessages } from '../../../lib/i18n';
import NewAppointmentClient from './new-appointment-client';

export default async function NewAppointmentPage() {
  const cookieStore = cookies();
  const locale = cookieStore.get('locale')?.value ?? 'es-CO';
  const messages = loadMessages(locale);
  const tenantId =
    cookieStore.get('X-Tenant-Id')?.value
    ?? cookieStore.get('tenantId')?.value
    ?? '';

  return (
    <NewAppointmentClient
      tenantId={tenantId}
      labels={{
        title: messages['appointments.new.title'] ?? 'Nueva cita',
        physicianId: messages['appointments.physicianId'] ?? 'Physician ID',
        patientId: messages['appointments.patientId'] ?? 'Patient ID',
        date: messages['appointments.date'] ?? 'Fecha',
        time: messages['appointments.time'] ?? 'Hora',
        duration: messages['appointments.duration'] ?? 'Duración',
        create: messages['appointments.create'] ?? 'Reservar',
        conflictTitle:
          messages['appointments.conflict.title'] ??
          'El slot ya fue reservado. Alternativas:',
        genericError: messages['error.generic'] ?? 'Error'
      }}
    />
  );
}
