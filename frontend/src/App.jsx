import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Login from './views/Login';
import Patients from './views/Patients';
import Admissions from './views/Admissions';
import Encounters from './views/Encounters';
import Diary from './views/Diary';
import Knowledge from './views/Knowledge';
import Interconsultations from './views/Interconsultations';
import Lab from './views/Lab';
import Pharmacy from './views/Pharmacy';
import Users from './views/Users';

/**
 * ADR-014 — una sola SPA con vistas por rol, no apps separadas.
 *
 * La navegación se filtra por rol, pero eso es UX y no seguridad: ocultar un link no
 * impide llamar al endpoint. Cada endpoint del backend valida el rol por su cuenta (y
 * el tenant, y el servicio), así que un usuario que fuerce la URL de una vista que no
 * le toca verá la vista vacía con 403 del backend, no datos. La regla que se sigue acá
 * es que el frontend nunca es la capa que decide un permiso.
 */
const NAV = [
  { to: '/patients', label: 'Pacientes', roles: ['PHYSICIAN', 'NURSE', 'ADMISSIONS', 'TENANT_ADMIN'] },
  { to: '/admissions', label: 'Admisiones', roles: ['ADMISSIONS', 'TENANT_ADMIN'] },
  { to: '/encounters', label: 'Encuentros', roles: ['PHYSICIAN', 'TENANT_ADMIN'] },
  { to: '/diary', label: 'Diario', roles: ['NURSE', 'TENANT_ADMIN'] },
  { to: '/knowledge', label: 'Conocimiento', roles: ['PHYSICIAN', 'NURSE', 'SPECIALIST', 'PHARMACIST', 'LAB_TECH', 'TENANT_ADMIN'] },
  { to: '/interconsultations', label: 'Interconsultas', roles: ['PHYSICIAN', 'SPECIALIST', 'TENANT_ADMIN'] },
  { to: '/lab', label: 'Laboratorio', roles: ['PHYSICIAN', 'LAB_TECH', 'TENANT_ADMIN'] },
  { to: '/pharmacy', label: 'Farmacia', roles: ['PHYSICIAN', 'PHARMACIST', 'NURSE', 'TENANT_ADMIN'] },
  { to: '/users', label: 'Usuarios', roles: ['TENANT_ADMIN'] },
];

export default function App() {
  const { session, logout } = useAuth();

  if (!session) return <Login />;

  const visible = NAV.filter((n) => n.roles.includes(session.role));

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div>
            <span className="text-lg font-semibold text-slate-900">CareLink</span>
            <span className="ml-3 rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900">
              Datos sintéticos — implementación de referencia
            </span>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <span className="text-slate-600">
              {session.email} · <strong>{session.role}</strong>
              {session.serviceId && <> · {session.serviceId}</>}
            </span>
            <button onClick={logout} className="text-slate-600 underline hover:text-slate-900">Salir</button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 pb-2">
          {visible.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              className={({ isActive }) =>
                `whitespace-nowrap rounded px-3 py-1.5 text-sm ${
                  isActive ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'
                }`
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/patients" element={<Patients />} />
          <Route path="/admissions" element={<Admissions />} />
          <Route path="/encounters" element={<Encounters />} />
          <Route path="/diary" element={<Diary />} />
          <Route path="/knowledge" element={<Knowledge />} />
          <Route path="/interconsultations" element={<Interconsultations />} />
          <Route path="/lab" element={<Lab />} />
          <Route path="/pharmacy" element={<Pharmacy />} />
          <Route path="/users" element={<Users />} />
          <Route path="*" element={<Navigate to={visible[0]?.to ?? '/knowledge'} replace />} />
        </Routes>
      </main>
    </div>
  );
}
