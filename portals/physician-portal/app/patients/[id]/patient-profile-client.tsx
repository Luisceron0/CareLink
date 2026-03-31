'use client';

import React from 'react';
import type { ClinicalPatient } from '../../../lib/types';

type Props = {
  patient: ClinicalPatient;
  labels: {
    fullName: string;
    document: string;
    bloodType: string;
    phone: string;
    email: string;
    emergencyContact: string;
    allergies: string;
    medications: string;
    emptyAllergies: string;
    emptyMedications: string;
  };
};

export default function PatientProfileClient({ patient, labels }: Props) {
  const allergies = patient.allergies ?? [];
  const medications = patient.activeMedications ?? [];

  return (
    <section>
      <dl>
        <dt>{labels.fullName}</dt>
        <dd>{patient.fullName}</dd>

        <dt>{labels.document}</dt>
        <dd>{patient.documentType} {patient.documentValue}</dd>

        <dt>{labels.bloodType}</dt>
        <dd>{patient.bloodType}</dd>

        <dt>{labels.phone}</dt>
        <dd>{patient.phone}</dd>

        <dt>{labels.email}</dt>
        <dd>{patient.email}</dd>

        <dt>{labels.emergencyContact}</dt>
        <dd>{patient.emergencyContact}</dd>
      </dl>

      <h2>{labels.allergies}</h2>
      {allergies.length === 0 ? (
        <p>{labels.emptyAllergies}</p>
      ) : (
        <ul>
          {allergies.map(item => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}

      <h2>{labels.medications}</h2>
      {medications.length === 0 ? (
        <p>{labels.emptyMedications}</p>
      ) : (
        <ul>
          {medications.map(item => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
