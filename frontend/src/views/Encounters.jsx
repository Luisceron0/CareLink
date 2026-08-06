import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre } from '../components/ui';

/** FR-CLN-02 / AC-08. Tras firmar, el backend responde 409 a cualquier edición; la UI
 *  muestra ese conflicto como lo que es —una garantía cumplida—, no como un error. */
export default function Encounters() {
  const [form, setForm] = useState({ patientId: '', chiefComplaint: '', examFindings: '', diagnosisCie10: '', treatmentPlan: '', followUp: '' });
  const [encounter, setEncounter] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function create(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try {
      const res = await api.post('/api/v1/encounters', form);
      setEncounter(res);
    } catch (err) { setError(err); }
  }

  async function sign() {
    setError(null); setNotice(null);
    try {
      await api.post(`/api/v1/encounters/${encounter.id}/sign`, {});
      setEncounter(await api.get(`/api/v1/encounters/${encounter.id}`));
      setNotice('Encuentro firmado. A partir de ahora es inmutable a nivel de base de datos.');
    } catch (err) { setError(err); }
  }

  async function tryEdit() {
    setError(null); setNotice(null);
    try {
      await api.put(`/api/v1/encounters/${encounter.id}`, { ...form, chiefComplaint: form.chiefComplaint + ' (editado)' });
      setNotice('Edición aplicada (el encuentro no estaba firmado).');
    } catch (err) {
      if (err.status === 409) {
        setNotice('409 — el encuentro está firmado y es inmutable. Rechazado por un trigger de la base de datos, no solo por la aplicación.');
      } else { setError(err); }
    }
  }

  return (
    <Card title="Encuentro clínico" subtitle="FR-CLN-02 / AC-08 — firma electrónica e inmutabilidad posterior.">
      <form onSubmit={create} className="grid gap-4 sm:grid-cols-2">
        <Field label="ID de paciente" value={form.patientId} onChange={set('patientId')} required />
        <Field label="Diagnóstico CIE-10" value={form.diagnosisCie10} onChange={set('diagnosisCie10')} />
        <Field label="Motivo de consulta" value={form.chiefComplaint} onChange={set('chiefComplaint')} required />
        <Field label="Hallazgos del examen" value={form.examFindings} onChange={set('examFindings')} />
        <Field label="Plan de tratamiento" value={form.treatmentPlan} onChange={set('treatmentPlan')} />
        <Field label="Seguimiento" value={form.followUp} onChange={set('followUp')} />
        <div className="flex items-end gap-3">
          <Button type="submit">Crear</Button>
          {encounter && <Button type="button" variant="secondary" onClick={sign}>Firmar</Button>}
          {encounter && <Button type="button" variant="secondary" onClick={tryEdit}>Intentar editar</Button>}
        </div>
      </form>
      {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
      <ErrorNote error={error} />
      <Pre value={encounter} />
    </Card>
  );
}
