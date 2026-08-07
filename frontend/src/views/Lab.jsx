import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field, Pre } from '../components/ui';

/** FR-CLN-11. */
export default function Lab() {
  const [form, setForm] = useState({ patientId: '', encounterId: '', testCode: '', testName: '' });
  const [order, setOrder] = useState(null);
  const [result, setResult] = useState({ value: '', units: '', criticalValue: false });
  const [pending, setPending] = useState([]);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function loadPending() {
    try { setPending((await api.get('/api/v1/lab/notifications')).pending); }
    catch { /* un rol sin acceso simplemente no ve la lista */ }
  }
  useEffect(() => { loadPending(); }, []);

  async function createOrder(e) {
    e.preventDefault();
    setError(null); setNotice(null);
    try { setOrder(await api.post('/api/v1/lab/orders', form)); }
    catch (err) { setError(err); }
  }

  async function recordResult() {
    setError(null); setNotice(null);
    try {
      const res = await api.post(`/api/v1/lab/orders/${order.id}/result`, result);
      setNotice(res.notificationCreated
        ? 'Valor CRÍTICO: se generó una notificación pendiente para el médico solicitante.'
        : 'Resultado cargado (no crítico): sin notificación.');
      setOrder(await api.get(`/api/v1/lab/orders/${order.id}`));
      loadPending();
    } catch (err) { setError(err); }
  }

  async function acknowledge(id) {
    try { await api.post(`/api/v1/lab/notifications/${id}/acknowledge`, {}); loadPending(); }
    catch (err) { setError(err); }
  }

  return (
    <div className="space-y-6">
      <Card title="Laboratorio" subtitle="FR-CLN-11 — el flag de valor crítico lo declara el laboratorio, no lo deriva el sistema.">
        <form onSubmit={createOrder} className="grid gap-4 sm:grid-cols-2">
          <Field label="ID de paciente" value={form.patientId} onChange={set('patientId')} required />
          <Field label="ID de encuentro" value={form.encounterId} onChange={set('encounterId')} required />
          <Field label="Código del estudio" value={form.testCode} onChange={set('testCode')} required />
          <Field label="Nombre del estudio" value={form.testName} onChange={set('testName')} required />
          <div className="flex items-end"><Button type="submit">Ordenar</Button></div>
        </form>

        {order && (
          <div className="mt-6 grid gap-4 sm:grid-cols-4">
            <Field label="Valor" value={result.value} onChange={(e) => setResult({ ...result, value: e.target.value })} />
            <Field label="Unidades" value={result.units} onChange={(e) => setResult({ ...result, units: e.target.value })} />
            <label className="flex items-end gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={result.criticalValue}
                onChange={(e) => setResult({ ...result, criticalValue: e.target.checked })} />
              Valor crítico
            </label>
            <div className="flex items-end"><Button type="button" onClick={recordResult}>Cargar resultado</Button></div>
          </div>
        )}

        {notice && <div className="mt-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">{notice}</div>}
        <ErrorNote error={error} />
        <Pre value={order} />
      </Card>

      <Card title="Valores críticos pendientes" subtitle="La notificación es una obligación abierta hasta que el médico acusa recibo.">
        {pending.length === 0
          ? <p className="text-sm text-slate-500">Sin notificaciones pendientes.</p>
          : (
            <ul className="space-y-2">
              {pending.map((n) => (
                <li key={n.id} className="flex items-center justify-between rounded border border-red-200 bg-red-50 px-3 py-2 text-sm">
                  <span>Orden {n.labOrderId.slice(0, 8)}… · paciente {n.patientId.slice(0, 8)}…</span>
                  <Button variant="secondary" onClick={() => acknowledge(n.id)}>Acusar recibo</Button>
                </li>
              ))}
            </ul>
          )}
      </Card>
    </div>
  );
}
