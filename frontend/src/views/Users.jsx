import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre, Select } from '../components/ui';

/** FR-ID-02 — solo TENANT_ADMIN. */
export default function Users() {
  const [form, setForm] = useState({ email: '', role: 'PHYSICIAN', serviceId: '' });
  const [created, setCreated] = useState(null);
  const [deactivateId, setDeactivateId] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  async function invite(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try {
      const res = await api.post('/api/v1/users/invite', form);
      setCreated(res);
      setDeactivateId(res.id);
      setNotice('Invitación enviada. El usuario fija su contraseña con el token que recibe por correo.');
    } catch (err) { setError(err); }
  }

  async function deactivate() {
    setError(null); setNotice(null);
    try {
      await api.post(`/api/v1/users/${deactivateId}/deactivate`, {});
      setNotice('Usuario desactivado. No se borra: su historial de auditoría se retiene permanentemente.');
    } catch (err) { setError(err); }
  }

  return (
    <Card title="Gestión de usuarios" subtitle="FR-ID-02 — invitación con rol y servicio; la baja desactiva, no borra.">
      <form onSubmit={invite} className="grid gap-4 sm:grid-cols-4">
        <Field label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        <Select label="Rol" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}
          options={['PHYSICIAN', 'NURSE', 'SPECIALIST', 'PHARMACIST', 'LAB_TECH', 'ADMISSIONS', 'AUDITOR', 'TENANT_ADMIN']} />
        <Field label="Servicio" value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })} placeholder="Urgencias" />
        <div className="flex items-end"><Button type="submit">Invitar</Button></div>
      </form>

      <div className="mt-6 flex gap-3">
        <div className="flex-1"><Field label="ID de usuario a desactivar" value={deactivateId} onChange={(e) => setDeactivateId(e.target.value)} /></div>
        <div className="flex items-end"><Button type="button" variant="secondary" onClick={deactivate}>Desactivar</Button></div>
      </div>

      {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
      <ErrorNote error={error} />
      <Pre value={created} />
    </Card>
  );
}
