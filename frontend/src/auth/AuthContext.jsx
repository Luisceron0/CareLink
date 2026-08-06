import { createContext, useContext, useMemo, useState } from 'react';
import { api, setAccessToken, clearAccessToken, decodeToken } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);

  async function login(email, password) {
    const res = await api.post('/api/v1/auth/login', { email, password });
    setAccessToken(res.accessToken);
    const claims = decodeToken(res.accessToken);
    setSession({
      userId: claims?.sub,
      role: claims?.role,
      tenantId: claims?.tenant_id,
      serviceId: claims?.service_id ?? null,
      email,
    });
  }

  async function logout() {
    try {
      await api.post('/api/v1/auth/logout', {});
    } finally {
      clearAccessToken();
      setSession(null);
    }
  }

  const value = useMemo(() => ({ session, login, logout }), [session]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
