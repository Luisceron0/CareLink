import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, ErrorNote, Field } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email, password);
    } catch (err) {
      // El backend devuelve el mismo mensaje para credenciales inválidas y cuenta
      // desactivada, a propósito. La UI no intenta adivinar cuál de los dos fue.
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto mt-16 max-w-sm">
      <Card title="CareLink" subtitle="Implementación de referencia — datos sintéticos únicamente">
        <form onSubmit={submit} className="space-y-4">
          <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Field label="Contraseña" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" disabled={busy}>{busy ? 'Ingresando…' : 'Ingresar'}</Button>
          <ErrorNote error={error} />
        </form>
      </Card>
    </div>
  );
}
