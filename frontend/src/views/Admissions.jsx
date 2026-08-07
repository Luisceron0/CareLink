import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre, Select } from '../components/ui';

/** FR-CLN-03. El campo de triage se deshabilita para CONSULTA_EXTERNA: Manchester es
 *  una herramienta de urgencias, y el backend rechaza la combinación igual. */
export default function Admissions() {
  const [patientId, setPatientId] = useState('');
  const [admissionType, setType] = useState('URGENCIAS');
  const [triagePriority, setTriage] = useState('3');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const esUrgencias = admissionType === 'URGENCIAS';

  async function submit(e) {
    e.preventDefault();
    setError(null);
    try {
      setResult(await api.post('/api/v1/admissions', {
        patientId,
        admissionType,
        triagePriority: esUrgencias ? Number(triagePriority) : null,
      }));
    } catch (err) { setError(err); }
  }

  return (
    <Card title="Admisiones y Triage" subtitle="FR-CLN-03 — Triage Manchester (1–5) obligatorio en urgencias.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="ID de paciente" value={patientId} onChange={(e) => setPatientId(e.target.value)} required />
        <Select label="Tipo de admisión" value={admissionType} onChange={(e) => setType(e.target.value)}
          options={['URGENCIAS', 'CONSULTA_EXTERNA']} />
        <Select label="Prioridad Triage" value={triagePriority} onChange={(e) => setTriage(e.target.value)}
          options={['1', '2', '3', '4', '5']} disabled={!esUrgencias} />
        <div className="flex items-end"><Button type="submit">Registrar ingreso</Button></div>
      </form>
      {!esUrgencias && (
        <p className="mt-2 text-xs text-slate-500">
          Consulta externa no lleva clasificación Manchester — el campo queda deshabilitado.
        </p>
      )}
      <ErrorNote error={error} />
      <Pre value={result} />
    </Card>
  );
}
