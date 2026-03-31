import React from 'react';
import { cookies } from 'next/headers';
import { getPatient } from '../../../../../lib/api/clinical';
import { loadMessages } from '../../../../../lib/i18n';
import type { ClinicalPatient } from '../../../../../lib/types';
import NewEncounterClient from './new-encounter-client';

type Props = {
  params: {
    id: string;
  };
};

export default async function NewEncounterPage({ params }: Props) {
  const cookieStore = cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map(cookie => `${cookie.name}=${cookie.value}`)
    .join('; ');
  const locale = cookieStore.get('locale')?.value ?? 'es-CO';
  const messages = loadMessages(locale);

  let patient: ClinicalPatient | null = null;
  try {
    patient = await getPatient(params.id, cookieHeader, 'PHYSICIAN');
  } catch {
    patient = null;
  }

  return (
    <div style={{ padding: 16 }}>
      <h1>{messages['clinical.encounter.new.title'] ?? 'Nuevo encuentro'}</h1>
      <p>
        {(messages['clinical.encounter.patientLabel'] ?? 'Paciente')}
        : {patient?.fullName ?? params.id}
      </p>
      <NewEncounterClient
        patientId={params.id}
        cookieHeader={cookieHeader}
        allergies={patient?.allergies ?? []}
        labels={{
          complaint: messages['clinical.encounter.complaint'] ?? 'Motivo de consulta',
          exam: messages['clinical.encounter.exam'] ?? 'Examen fisico',
          diagnosis: messages['clinical.encounter.diagnosis'] ?? 'Diagnostico ICD-10',
          diagnosisSearch:
            messages['clinical.encounter.diagnosis.search']
            ?? 'Buscar por codigo o descripcion',
          diagnosisSelected:
            messages['clinical.encounter.diagnosis.selected']
            ?? 'Diagnostico seleccionado',
          plan: messages['clinical.encounter.plan'] ?? 'Plan terapeutico',
          prescription: messages['clinical.encounter.prescription'] ?? 'Receta',
          create: messages['clinical.encounter.create'] ?? 'Guardar encuentro',
          update: messages['clinical.encounter.update'] ?? 'Actualizar encuentro',
          sign: messages['clinical.encounter.sign'] ?? 'Firmar encuentro',
          signingTitle:
            messages['clinical.encounter.sign.modal.title']
            ?? 'Confirmar firma',
          signingBody:
            messages['clinical.encounter.sign.modal.body']
            ?? 'Esta accion es irreversible. El encuentro quedara bloqueado para edicion.',
          signingCancel:
            messages['clinical.encounter.sign.modal.cancel'] ?? 'Cancelar',
          signingConfirm:
            messages['clinical.encounter.sign.modal.confirm'] ?? 'Confirmar firma',
          readOnlyNotice:
            messages['clinical.encounter.readonly']
            ?? 'El encuentro firmado es de solo lectura.',
          immutableError:
            messages['clinical.encounter.immutableError']
            ?? 'No se puede editar un encuentro firmado.',
          genericError: messages['error.generic'] ?? 'Error',
          createdOk:
            messages['clinical.encounter.created.ok']
            ?? 'Encuentro guardado.',
          signedOk:
            messages['clinical.encounter.signed.ok']
            ?? 'Encuentro firmado exitosamente.',
          allergyWarning:
            messages['clinical.encounter.allergy.warning']
            ?? 'Advertencia: la receta puede entrar en conflicto con alergias conocidas.',
          noDiagnosis:
            messages['clinical.encounter.diagnosis.required']
            ?? 'Debes seleccionar un codigo ICD-10 valido.'
        }}
      />
    </div>
  );
}
