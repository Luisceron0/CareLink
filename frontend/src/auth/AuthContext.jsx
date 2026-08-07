import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { api, setAccessToken, clearAccessToken, decodeToken } from '../api/client';

const AuthContext = createContext(null);

function sessionFromToken(accessToken, email) {
  const claims = decodeToken(accessToken);
  return {
    userId: claims?.sub,
    role: claims?.role,
    tenantId: claims?.tenant_id,
    serviceId: claims?.service_id ?? null,
    email: email ?? null,
  };
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  // Hallazgo de la auditoría (agent-browser, 2026-08-07): un F5 mostraba el login
  // aunque la cookie de refresh siguiera vigente — el comentario de client.js ya
  // prometía "hasta que /refresh la reconstruye", pero nada llamaba a /refresh al
  // montar la app. `restoring` evita el parpadeo de "no hay sesión" mientras se
  // resuelve ese intento silencioso.
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.post('/api/v1/auth/refresh', {})
      .then((res) => {
        if (cancelled) return;
        setAccessToken(res.accessToken);
        // El email no viaja en la respuesta de /refresh (no es un claim del JWT ni
        // un campo del body) — se omite en vez de mostrar un valor inventado; el
        // resto de la UI usa userId/role, no el email, para identificar la sesión.
        setSession(sessionFromToken(res.accessToken));
      })
      .catch(() => {
        // Sin cookie válida (primera visita, cookie expirada, o el usuario nunca iba
        // a tener sesión) — no es un error que mostrar, es el estado "hay que loguearse".
      })
      .finally(() => {
        if (!cancelled) setRestoring(false);
      });
    return () => { cancelled = true; };
  }, []);

  async function login(email, password) {
    const res = await api.post('/api/v1/auth/login', { email, password });
    setAccessToken(res.accessToken);
    setSession(sessionFromToken(res.accessToken, email));
  }

  async function logout() {
    try {
      await api.post('/api/v1/auth/logout', {});
    } finally {
      clearAccessToken();
      setSession(null);
    }
  }

  const value = useMemo(() => ({ session, login, logout, restoring }), [session, restoring]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
