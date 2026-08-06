import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre } from '../components/ui';

/** FR-CLN-12. Los conflictos ADVIERTEN: esta vista los muestra en ámbar, no bloquea nada. */
export default function Pharmacy() {
  const [conflictQuery, setConflictQuery] = useState({ patientId: '', medication: '', medicationClass: '' });
  const [conflicts, setConflicts] = useState(null);
  const [dispense, setDispense] = useState({ prescriptionId: '', patientId: '', dosesDispensed: '' });
  const [adherenceId, setAdherenceId] = useState('');
  const [adherence, setAdherence] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  async function checkConflicts(e) {
    e.preventDefault();
    setError(null);
    try { setConflicts(await api.get('/api/v1/pharmacy/conflicts', conflictQuery)); }
    catch (err) { setError(err); }
  }

  async function submitDispense(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try {
      await api.post('/api/v1/pharmacy/dispensations', { ...dispense, dosesDispensed: Number(dispense.dosesDispensed) });
      setNotice('Dispensación registrada.');
    } catch (err) { setError(err); }
  }

  async function loadAdherence(e) {
    e.preventDefault();
    setError(null);
    try { setAdherence(await api.get(`/api/v1/pharmacy/prescriptions/${adherenceId}/adherence`)); }
    catch (err) { setError(err); }
  }

  return (
    <div className="space-y-6">
      <Card title="Conflictos de prescripción" subtitle="FR-CLN-12 — advierten, nunca bloquean: la decisión es del prescriptor.">
        <form onSubmit={checkConflicts} className="grid gap-4 sm:grid-cols-4">
          <Field label="ID de paciente" value={conflictQuery.patientId} onChange={(e) => setConflictQuery({ ...conflictQuery, patientId: e.target.value })} required />
          <Field label="Medicamento" value={conflictQuery.medication} onChange={(e) => setConflictQuery({ ...conflictQuery, medication: e.target.value })} required />
          <Field label="Clase farmacológica" value={conflictQuery.medicationClass} onChange={(e) => setConflictQuery({ ...conflictQuery, medicationClass: e.target.value })} />
          <div className="flex items-end"><Button type="submit">Verificar</Button></div>
        </form>

        {conflicts && conflicts.conflicts.length > 0 && (
          <div className="mt-4 space-y-2">
            {conflicts.conflicts.map((c, i) => (
              <div key={i} className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                <strong>{c.type === 'ALLERGY' ? 'Alergia' : 'Misma clase activa'}:</strong> {c.detail}
              </div>
            ))}
            <p className="text-xs text-slate-500">
              Advertencia, no bloqueo — la prescripción se puede emitir igual si el criterio clínico lo indica.
            </p>
          </div>
        )}
        {conflicts && conflicts.conflicts.length === 0 && (
          <p className="mt-4 text-sm text-slate-600">Sin conflictos detectados.</p>
        )}
      </Card>

      <Card title="Dispensación" subtitle="FR-CLN-12 — registrada por farmacia.">
        <form onSubmit={submitDispense} className="grid gap-4 sm:grid-cols-4">
          <Field label="ID de prescripción" value={dispense.prescriptionId} onChange={(e) => setDispense({ ...dispense, prescriptionId: e.target.value })} required />
          <Field label="ID de paciente" value={dispense.patientId} onChange={(e) => setDispense({ ...dispense, patientId: e.target.value })} required />
          <Field label="Dosis dispensadas" value={dispense.dosesDispensed} onChange={(e) => setDispense({ ...dispense, dosesDispensed: e.target.value })} required />
          <div className="flex items-end"><Button type="submit">Registrar</Button></div>
        </form>
        {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
      </Card>

      <Card title="Índice de adherencia" subtitle="dispensadas / prescritas. Sin total registrado no es calculable — que no es lo mismo que 0%.">
        <form onSubmit={loadAdherence} className="flex gap-3">
          <div className="flex-1"><Field label="ID de prescripción" value={adherenceId} onChange={(e) => setAdherenceId(e.target.value)} required /></div>
          <div className="flex items-end"><Button type="submit" variant="secondary">Consultar</Button></div>
        </form>
        {adherence && !adherence.calculable && (
          <div className="mt-3 rounded border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-700">{adherence.note}</div>
        )}
        {adherence?.calculable && (
          <p className="mt-3 text-sm text-slate-800">
            {adherence.dispensedDoses} de {adherence.prescribedDoses} dosis —{' '}
            <strong>{(adherence.ratio * 100).toFixed(0)}%</strong>
            {adherence.ratio > 1 && <span className="ml-2 text-amber-700">(supera lo prescrito — revisar)</span>}
          </p>
        )}
        <ErrorNote error={error} />
      </Card>
    </div>
  );
}
