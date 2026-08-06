import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre, Select } from '../components/ui';

/** FR-CLN-04 / FR-CLN-05. */
export default function Diary() {
  const [entry, setEntry] = useState({ patientId: '', entryDate: '', shift: 'MANANA', observations: '' });
  const [vitals, setVitals] = useState({ systolicMmHg: '', diastolicMmHg: '', heartRateBpm: '', temperatureCelsius: '', oxygenSaturation: '' });
  const [intervention, setIntervention] = useState({ nandaCode: '', nicCode: '', diagnosisCie10: '', description: '' });
  const [created, setCreated] = useState(null);
  const [outcome, setOutcome] = useState({ nocCode: '', effectiveness: '4', notes: '' });
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const num = (v) => (v === '' ? null : Number(v));

  async function submit(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try {
      const body = {
        ...entry,
        vitalSigns: [{
          systolicMmHg: num(vitals.systolicMmHg), diastolicMmHg: num(vitals.diastolicMmHg),
          heartRateBpm: num(vitals.heartRateBpm), temperatureCelsius: num(vitals.temperatureCelsius),
          oxygenSaturation: num(vitals.oxygenSaturation),
        }],
        interventions: intervention.nicCode ? [intervention] : [],
      };
      setCreated(await api.post('/api/v1/diary/entries', body));
    } catch (err) { setError(err); }
  }

  async function recordOutcome() {
    setError(null); setNotice(null);
    const interventionId = created?.interventions?.[0]?.id;
    if (!interventionId) { setNotice('La entrada no tiene intervención a la que registrarle un resultado.'); return; }
    try {
      await api.post(`/api/v1/diary/interventions/${interventionId}/outcome`, {
        nocCode: outcome.nocCode, effectiveness: Number(outcome.effectiveness), notes: outcome.notes,
      });
      setCreated(await api.get(`/api/v1/diary/entries/${created.id}`));
      setNotice('Resultado registrado. Un segundo intento sobre la misma intervención será rechazado: ya alimentó agregados del Motor de Conocimiento.');
    } catch (err) { setError(err); }
  }

  return (
    <div className="space-y-6">
      <Card title="Diario de enfermería" subtitle="FR-CLN-04 — vinculado a paciente + fecha/turno, no a un encuentro abierto.">
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
          <Field label="ID de paciente" value={entry.patientId} onChange={(e) => setEntry({ ...entry, patientId: e.target.value })} required />
          <Field label="Fecha" type="date" value={entry.entryDate} onChange={(e) => setEntry({ ...entry, entryDate: e.target.value })} required />
          <Select label="Turno" value={entry.shift} onChange={(e) => setEntry({ ...entry, shift: e.target.value })} options={['MANANA', 'TARDE', 'NOCHE']} />
          <Field label="Observaciones" value={entry.observations} onChange={(e) => setEntry({ ...entry, observations: e.target.value })} />
          <Field label="TA sistólica" value={vitals.systolicMmHg} onChange={(e) => setVitals({ ...vitals, systolicMmHg: e.target.value })} />
          <Field label="TA diastólica" value={vitals.diastolicMmHg} onChange={(e) => setVitals({ ...vitals, diastolicMmHg: e.target.value })} />
          <Field label="FC (lpm)" value={vitals.heartRateBpm} onChange={(e) => setVitals({ ...vitals, heartRateBpm: e.target.value })} />
          <Field label="Temperatura (°C)" value={vitals.temperatureCelsius} onChange={(e) => setVitals({ ...vitals, temperatureCelsius: e.target.value })} />
          <Field label="SatO2 (%)" value={vitals.oxygenSaturation} onChange={(e) => setVitals({ ...vitals, oxygenSaturation: e.target.value })} />
          <Field label="Código NANDA" value={intervention.nandaCode} onChange={(e) => setIntervention({ ...intervention, nandaCode: e.target.value })} />
          <Field label="Código NIC" value={intervention.nicCode} onChange={(e) => setIntervention({ ...intervention, nicCode: e.target.value })} />
          <Field label="Diagnóstico CIE-10" value={intervention.diagnosisCie10} onChange={(e) => setIntervention({ ...intervention, diagnosisCie10: e.target.value })} />
          <div className="flex items-end"><Button type="submit">Registrar entrada</Button></div>
        </form>
        <ErrorNote error={error} />
        <Pre value={created} />
      </Card>

      {created?.interventions?.length > 0 && (
        <Card title="Resultado de la intervención (NOC)" subtitle="FR-CLN-05 — alimenta el Motor de Conocimiento.">
          <div className="grid gap-4 sm:grid-cols-4">
            <Field label="Código NOC" value={outcome.nocCode} onChange={(e) => setOutcome({ ...outcome, nocCode: e.target.value })} />
            <Select label="Efectividad (1–5)" value={outcome.effectiveness} onChange={(e) => setOutcome({ ...outcome, effectiveness: e.target.value })} options={['1', '2', '3', '4', '5']} />
            <Field label="Notas" value={outcome.notes} onChange={(e) => setOutcome({ ...outcome, notes: e.target.value })} />
            <div className="flex items-end"><Button type="button" onClick={recordOutcome}>Registrar resultado</Button></div>
          </div>
          {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
        </Card>
      )}
    </div>
  );
}
