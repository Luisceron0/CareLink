import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre } from '../components/ui';

/** FR-CLN-08 / FR-CLN-10 / AC-13. */
export default function Interconsultations() {
  const [form, setForm] = useState({ patientId: '', encounterId: '', specialistUserId: '', question: '' });
  const [ic, setIc] = useState(null);
  const [lookupId, setLookupId] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function create(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try {
      const res = await api.post('/api/v1/interconsultations', form);
      setIc(res); setLookupId(res.id);
    } catch (err) { setError(err); }
  }

  async function read() {
    setError(null); setNotice(null);
    try { setIc(await api.get(`/api/v1/interconsultations/${lookupId}`)); }
    catch (err) {
      setError(err);
      if (err.status === 403) {
        setNotice('403 — sin interconsulta abierta no hay acceso. Se reevalúa en cada request: no hay permiso guardado que revocar.');
      }
    }
  }

  async function close() {
    setError(null); setNotice(null);
    try {
      await api.post(`/api/v1/interconsultations/${lookupId}/close`, {});
      setNotice('Cerrada. El acceso del especialista cae con esto, sin ningún paso adicional de revocación.');
      setIc(await api.get(`/api/v1/interconsultations/${lookupId}`));
    } catch (err) { setError(err); }
  }

  return (
    <Card title="Interconsultas" subtitle="FR-CLN-10 / AC-13 — el acceso del especialista se revalida en cada request.">
      <form onSubmit={create} className="grid gap-4 sm:grid-cols-2">
        <Field label="ID de paciente" value={form.patientId} onChange={set('patientId')} required />
        <Field label="ID de encuentro (raíz)" value={form.encounterId} onChange={set('encounterId')} required />
        <Field label="ID del especialista" value={form.specialistUserId} onChange={set('specialistUserId')} required />
        <Field label="Pregunta" value={form.question} onChange={set('question')} required />
        <div className="flex items-end gap-3"><Button type="submit">Solicitar</Button></div>
      </form>

      <div className="mt-6 flex gap-3">
        <div className="flex-1"><Field label="ID de interconsulta" value={lookupId} onChange={(e) => setLookupId(e.target.value)} /></div>
        <div className="flex items-end gap-3">
          <Button type="button" variant="secondary" onClick={read}>Leer</Button>
          <Button type="button" variant="secondary" onClick={close}>Cerrar</Button>
        </div>
      </div>

      {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
      <ErrorNote error={error} />
      <Pre value={ic} />
    </Card>
  );
}
