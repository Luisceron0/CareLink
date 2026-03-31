import React from 'react';
import Link from 'next/link';
import { cookies } from 'next/headers';
import { getPatient } from '../../../lib/api/clinical';
import { loadMessages } from '../../../lib/i18n';
import type { ClinicalPatient } from '../../../lib/types';
import PatientProfileClient from './patient-profile-client';

type Props = {
  params: {
    id: string;
  };
};

export default async function PatientProfilePage({ params }: Props) {
  const cookieStore = cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map(cookie => `${cookie.name}=${cookie.value}`)
    .join('; ');
  const locale = cookieStore.get('locale')?.value ?? 'es-CO';
  const messages = loadMessages(locale);

  let patient: ClinicalPatient | null = null;
  let hasError = false;

  try {
    patient = await getPatient(params.id, cookieHeader, 'PHYSICIAN');
  } catch {
    hasError = true;
  }

  return (
    <div style={{ padding: 16 }}>
      <h1>{messages['clinical.patient.title'] ?? 'Paciente'}</h1>
      {hasError ? (
        <p style={{ color: '#a40000' }}>
          {messages['error.generic'] ?? 'Error'}
        </p>
      ) : null}
      {patient ? (
        <>
          <PatientProfileClient
            patient={patient}
            labels={{
              fullName: messages['clinical.patient.fullName'] ?? 'Nombre',
              document: messages['clinical.patient.document'] ?? 'Documento',
              bloodType: messages['clinical.patient.bloodType'] ?? 'Tipo sanguineo',
              phone: messages['clinical.patient.phone'] ?? 'Telefono',
              email: messages['clinical.patient.email'] ?? 'Email',
              emergencyContact:
                messages['clinical.patient.emergencyContact']
                ?? 'Contacto de emergencia',
              allergies: messages['clinical.patient.allergies'] ?? 'Alergias',
              medications:
                messages['clinical.patient.medications']
                ?? 'Medicamentos activos',
              emptyAllergies:
                messages['clinical.patient.emptyAllergies']
                ?? 'Sin alergias registradas',
              emptyMedications:
                messages['clinical.patient.emptyMedications']
                ?? 'Sin medicacion activa registrada'
            }}
          />
          <p style={{ marginTop: 16 }}>
            <Link href={`/patients/${params.id}/encounters/new`}>
              {messages['clinical.encounter.new.link']
                ?? 'Registrar nuevo encuentro'}
            </Link>
          </p>
        </>
      ) : null}
    </div>
  );
}
