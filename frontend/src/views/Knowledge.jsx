import { useState } from 'react';
import { api } from '../api/client';
import { Button, Card, ErrorNote, Field } from '../components/ui';

/**
 * FR-CLN-06 + FR-CLN-07.
 *
 * El requisito de UX de FR-CLN-07 es explícito y es la razón de que esta vista tenga
 * TRES estados de resultado y no dos: "suprimido por k-anonimato" se muestra como un
 * aviso propio, distinto de "no hay casos previos". El texto del requisito lo pide con
 * todas las letras — "nunca un resultado que parezca vacío y se pueda leer como 'no hay
 * casos previos'" — porque confundirlos lleva a una conclusión clínica falsa: creer que
 * una intervención nunca se usó cuando en realidad sí, solo que sobre pocos pacientes.
 */
export default function Knowledge() {
  const [diagnosisCie10, setDiagnosis] = useState('');
  const [nandaCode, setNanda] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function search(e) {
    e.preventDefault();
    setError(null);
    setResult(null);
    setBusy(true);
    try {
      setResult(await api.get('/api/v1/knowledge/search', { diagnosisCie10, nandaCode }));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card
      title="Motor de Conocimiento"
      subtitle="Intervenciones previas y su efectividad, agregadas sobre casos similares."
    >
      <form onSubmit={search} className="grid gap-4 sm:grid-cols-3">
        <Field label="Diagnóstico CIE-10" value={diagnosisCie10} onChange={(e) => setDiagnosis(e.target.value)} placeholder="J45.9" />
        <Field label="Código NANDA" value={nandaCode} onChange={(e) => setNanda(e.target.value)} placeholder="00032" />
        <div className="flex items-end">
          <Button type="submit" disabled={busy}>{busy ? 'Buscando…' : 'Buscar'}</Button>
        </div>
      </form>

      <ErrorNote error={error} />

      {result?.suppressed && (
        <div className="mt-4 rounded border border-amber-300 bg-amber-50 p-4">
          <h3 className="font-semibold text-amber-900">Datos insuficientes</h3>
          <p className="mt-1 text-sm text-amber-900">{result.message}</p>
          <p className="mt-2 text-xs text-amber-800">
            Esto <strong>no</strong> significa que no haya casos previos: significa que los que hay
            son demasiado pocos ({'<'} {result.kAnonymityThreshold} pacientes distintos) como para
            mostrarlos sin riesgo de identificar a una persona.
          </p>
        </div>
      )}

      {result && !result.suppressed && result.results.length === 0 && (
        <div className="mt-4 rounded border border-slate-300 bg-slate-50 p-4">
          <h3 className="font-semibold text-slate-800">Sin casos previos</h3>
          <p className="mt-1 text-sm text-slate-600">
            No hay intervenciones registradas que coincidan con estos criterios.
          </p>
        </div>
      )}

      {result && !result.suppressed && result.results.length > 0 && (
        <table className="mt-4 w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-slate-600">
              <th className="py-2">NIC</th>
              <th>NOC</th>
              <th className="text-right">Intervenciones</th>
              <th className="text-right">Pacientes</th>
              <th className="text-right">Efectividad media</th>
            </tr>
          </thead>
          <tbody>
            {result.results.map((r) => (
              <tr key={`${r.nicCode}-${r.nocCode}`} className="border-b border-slate-100">
                <td className="py-2 font-medium">{r.nicCode}</td>
                <td>{r.nocCode}</td>
                <td className="text-right">{r.interventionCount}</td>
                <td className="text-right">{r.distinctPatients}</td>
                <td className="text-right">{r.averageEffectiveness?.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
