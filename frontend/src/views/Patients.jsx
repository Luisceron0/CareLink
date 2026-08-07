import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre, Select } from '../components/ui';

export default function Patients() {
  const [form, setForm] = useState({
    fullName: '', documentType: 'CEDULA_CIUDADANIA', documentNumber: '',
    dateOfBirth: '', sex: 'FEMALE', bloodType: 'O_POSITIVE', allergies: '',
  });
  const [created, setCreated] = useState(null);
  const [lookupId, setLookupId] = useState('');
  const [found, setFound] = useState(null);
  const [error, setError] = useState(null);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function register(e) {
    e.preventDefault();
    setError(null);
    try {
      const body = {
        ...form,
        allergies: form.allergies ? form.allergies.split(',').map((s) => s.trim()).filter(Boolean) : [],
      };
      const res = await api.post('/api/v1/patients', body);
      setCreated(res);
      setLookupId(res.id);
    } catch (err) { setError(err); }
  }

  async function lookup(e) {
    e.preventDefault();
    setError(null);
    setFound(null);
    try {
      setFound(await api.get(`/api/v1/patients/${lookupId}`));
    } catch (err) { setError(err); }
  }

  return (
    <div className="space-y-6">
      <Card title="Registrar paciente" subtitle="FR-CLN-01 — los campos PHI se cifran antes de guardarse.">
        <form onSubmit={register} className="grid gap-4 sm:grid-cols-2">
          <Field label="Nombre completo" value={form.fullName} onChange={set('fullName')} required />
          <Select label="Tipo de documento" value={form.documentType} onChange={set('documentType')}
            options={['CEDULA_CIUDADANIA', 'CEDULA_EXTRANJERIA', 'TARJETA_IDENTIDAD', 'PASAPORTE']} />
          <Field label="Número de documento" value={form.documentNumber} onChange={set('documentNumber')} required />
          <Field label="Fecha de nacimiento" type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} required />
          <Select label="Sexo" value={form.sex} onChange={set('sex')} options={['FEMALE', 'MALE', 'OTHER', 'UNKNOWN']} />
          <Select label="Grupo sanguíneo" value={form.bloodType} onChange={set('bloodType')}
            options={['O_POSITIVE', 'O_NEGATIVE', 'A_POSITIVE', 'A_NEGATIVE', 'B_POSITIVE', 'B_NEGATIVE', 'AB_POSITIVE', 'AB_NEGATIVE', 'UNKNOWN']} />
          <Field label="Alergias (separadas por coma)" value={form.allergies} onChange={set('allergies')} />
          <div className="flex items-end"><Button type="submit">Registrar</Button></div>
        </form>
        <Pre value={created} />
      </Card>

      <Card title="Consultar paciente" subtitle="AC-06/AC-06b — otro tenant u otro servicio devuelven 403.">
        <form onSubmit={lookup} className="flex gap-3">
          <div className="flex-1">
            <Field label="ID de paciente" value={lookupId} onChange={(e) => setLookupId(e.target.value)} required />
          </div>
          <div className="flex items-end"><Button type="submit" variant="secondary">Buscar</Button></div>
        </form>
        <ErrorNote error={error} />
        <Pre value={found} />
      </Card>
    </div>
  );
}
