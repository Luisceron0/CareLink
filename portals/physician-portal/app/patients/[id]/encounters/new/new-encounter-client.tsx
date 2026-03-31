'use client';

import React, { useMemo, useState } from 'react';
import {
  createEncounter,
  getPatient,
  normalizeIcd10Code,
  searchIcd10,
  signEncounter,
  updateEncounter
} from '../../../../../lib/api/clinical';
import type { ClinicalEncounter } from '../../../../../lib/types';

type Props = {
  patientId: string;
  cookieHeader: string;
  allergies: string[];
  labels: {
    complaint: string;
    exam: string;
    diagnosis: string;
    diagnosisSearch: string;
    diagnosisSelected: string;
    plan: string;
    prescription: string;
    create: string;
    update: string;
    sign: string;
    signingTitle: string;
    signingBody: string;
    signingCancel: string;
    signingConfirm: string;
    readOnlyNotice: string;
    immutableError: string;
    genericError: string;
    createdOk: string;
    signedOk: string;
    allergyWarning: string;
    noDiagnosis: string;
  };
};

type FormState = {
  chiefComplaint: string;
  physicalExam: string;
  diagnosisCode: string;
  treatmentPlan: string;
  prescription: string;
};

const EMPTY_FORM: FormState = {
  chiefComplaint: '',
  physicalExam: '',
  diagnosisCode: '',
  treatmentPlan: '',
  prescription: ''
};

export default function NewEncounterClient({
  patientId,
  cookieHeader,
  allergies,
  labels
}: Props) {
  const [knownAllergies, setKnownAllergies] = useState<string[]>(allergies);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [encounter, setEncounter] = useState<ClinicalEncounter | null>(null);
  const [readOnly, setReadOnly] = useState(false);
  const [showSignModal, setShowSignModal] = useState(false);
  const [statusError, setStatusError] = useState('');
  const [statusOk, setStatusOk] = useState('');
  const [diagnosisQuery, setDiagnosisQuery] = useState('');

  React.useEffect(() => {
    let mounted = true;

    async function hydrateAllergies() {
      if (knownAllergies.length > 0) {
        return;
      }

      try {
        const patient = await getPatient(patientId, cookieHeader, 'PHYSICIAN');
        if (mounted) {
          setKnownAllergies(patient.allergies ?? []);
        }
      } catch {
        // Keep silent fallback; warning will remain hidden if allergies are unavailable.
      }
    }

    hydrateAllergies();

    return () => {
      mounted = false;
    };
  }, [patientId, cookieHeader, knownAllergies.length]);

  const diagnosisOptions = useMemo(() => {
    return searchIcd10(diagnosisQuery);
  }, [diagnosisQuery]);

  const normalizedAllergies = useMemo(
    () => knownAllergies.map(item => item.toLowerCase()),
    [knownAllergies]
  );

  const hasAllergyWarning = useMemo(() => {
    if (!form.prescription.trim()) {
      return false;
    }
    const prescription = form.prescription.toLowerCase();
    return normalizedAllergies.some(item => item && prescription.includes(item));
  }, [form.prescription, normalizedAllergies]);

  async function handleCreateOrUpdate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (readOnly) {
      return;
    }

    setStatusError('');
    setStatusOk('');

    if (!form.diagnosisCode) {
      setStatusError(labels.noDiagnosis);
      return;
    }

    const payload = {
      chiefComplaint: form.chiefComplaint,
      physicalExam: form.physicalExam,
      treatmentPlan: buildPlanWithDiagnosis(
        form.diagnosisCode,
        form.treatmentPlan
      ),
      followUpInstructions: form.prescription
    };

    try {
      if (!encounter) {
        const created = await createEncounter(
          patientId,
          payload,
          cookieHeader,
          'PHYSICIAN'
        );
        setEncounter(created);
        setStatusOk(labels.createdOk);
        return;
      }

      const updated = await updateEncounter(
        patientId,
        encounter.id,
        payload,
        cookieHeader,
        'PHYSICIAN'
      );
      setEncounter(updated);
      setStatusOk(labels.createdOk);
    } catch (error) {
      if (error instanceof Error && error.message === 'CLINICAL_ENCOUNTER_IMMUTABLE') {
        setReadOnly(true);
        setStatusError(labels.immutableError);
        return;
      }
      setStatusError(labels.genericError);
    }
  }

  async function handleSignConfirmed() {
    if (!encounter || readOnly) {
      return;
    }

    setStatusError('');
    setStatusOk('');

    try {
      await signEncounter(patientId, encounter.id, cookieHeader, 'PHYSICIAN');
      setReadOnly(true);
      setShowSignModal(false);
      setEncounter({
        ...encounter,
        signedAt: new Date().toISOString()
      });
      setStatusOk(labels.signedOk);
    } catch {
      setStatusError(labels.genericError);
      setShowSignModal(false);
    }
  }

  function selectDiagnosis(code: string) {
    setForm(prev => ({
      ...prev,
      diagnosisCode: normalizeIcd10Code(code)
    }));
    setDiagnosisQuery(code);
  }

  return (
    <section>
      {statusError ? <p style={{ color: '#a40000' }}>{statusError}</p> : null}
      {statusOk ? <p style={{ color: '#0c6c1b' }}>{statusOk}</p> : null}
      {readOnly ? <p>{labels.readOnlyNotice}</p> : null}

      <form onSubmit={handleCreateOrUpdate}>
        <div>
          <label htmlFor="chiefComplaint">{labels.complaint}</label>
          <textarea
            id="chiefComplaint"
            value={form.chiefComplaint}
            onChange={event => setForm(prev => ({
              ...prev,
              chiefComplaint: event.target.value
            }))}
            readOnly={readOnly}
            required
          />
        </div>

        <div>
          <label htmlFor="physicalExam">{labels.exam}</label>
          <textarea
            id="physicalExam"
            value={form.physicalExam}
            onChange={event => setForm(prev => ({
              ...prev,
              physicalExam: event.target.value
            }))}
            readOnly={readOnly}
            required
          />
        </div>

        <div>
          <label htmlFor="diagnosis-search">{labels.diagnosis}</label>
          <input
            id="diagnosis-search"
            value={diagnosisQuery}
            onChange={event => setDiagnosisQuery(event.target.value)}
            readOnly={readOnly}
            placeholder={labels.diagnosisSearch}
            autoComplete="off"
          />
          <p>
            {labels.diagnosisSelected}: {form.diagnosisCode || '-'}
          </p>
          {!readOnly && diagnosisOptions.length > 0 ? (
            <ul>
              {diagnosisOptions.map(item => (
                <li key={item.code}>
                  <button
                    type="button"
                    onClick={() => selectDiagnosis(item.code)}
                  >
                    {item.code} - {item.description}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>

        <div>
          <label htmlFor="treatmentPlan">{labels.plan}</label>
          <textarea
            id="treatmentPlan"
            value={form.treatmentPlan}
            onChange={event => setForm(prev => ({
              ...prev,
              treatmentPlan: event.target.value
            }))}
            readOnly={readOnly}
            required
          />
        </div>

        <div>
          <label htmlFor="prescription">{labels.prescription}</label>
          <textarea
            id="prescription"
            value={form.prescription}
            onChange={event => setForm(prev => ({
              ...prev,
              prescription: event.target.value
            }))}
            readOnly={readOnly}
            required
          />
        </div>

        {hasAllergyWarning ? (
          <p style={{ color: '#7a4d00' }}>{labels.allergyWarning}</p>
        ) : null}

        <div style={{ marginTop: 12 }}>
          <button type="submit" disabled={readOnly}>
            {encounter ? labels.update : labels.create}
          </button>
          <button
            type="button"
            disabled={!encounter || readOnly}
            onClick={() => setShowSignModal(true)}
            style={{ marginLeft: 8 }}
          >
            {labels.sign}
          </button>
        </div>
      </form>

      {showSignModal ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="sign-title"
          style={{
            marginTop: 16,
            border: '1px solid #7f7f7f',
            borderRadius: 8,
            padding: 12,
            background: '#fff8f3'
          }}
        >
          <h2 id="sign-title">{labels.signingTitle}</h2>
          <p>{labels.signingBody}</p>
          <button type="button" onClick={() => setShowSignModal(false)}>
            {labels.signingCancel}
          </button>
          <button
            type="button"
            onClick={handleSignConfirmed}
            style={{ marginLeft: 8 }}
          >
            {labels.signingConfirm}
          </button>
        </div>
      ) : null}
    </section>
  );
}

function buildPlanWithDiagnosis(code: string, plan: string): string {
  const normalizedCode = normalizeIcd10Code(code);
  const normalizedPlan = plan.trim();
  return `ICD10:${normalizedCode}\n${normalizedPlan}`;
}
