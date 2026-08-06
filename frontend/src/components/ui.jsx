export function Card({ title, subtitle, children }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      {title && <h2 className="text-lg font-semibold text-slate-900">{title}</h2>}
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function Field({ label, ...props }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <input
        className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        {...props}
      />
    </label>
  );
}

export function Select({ label, options, ...props }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <select
        className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        {...props}
      >
        {options.map((o) => (
          <option key={o} value={o}>{o}</option>
        ))}
      </select>
    </label>
  );
}

export function Button({ children, variant = 'primary', ...props }) {
  const styles = {
    primary: 'bg-slate-900 text-white hover:bg-slate-800 disabled:bg-slate-400',
    secondary: 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50',
  };
  return (
    <button
      className={`rounded px-4 py-2 text-sm font-medium transition ${styles[variant]}`}
      {...props}
    >
      {children}
    </button>
  );
}

/** Un error de API mostrado tal cual lo devolvió el backend, sin reinterpretarlo. */
export function ErrorNote({ error }) {
  if (!error) return null;
  return (
    <div className="mt-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      {error.status ? `HTTP ${error.status} — ` : ''}{error.message}
    </div>
  );
}

export function Pre({ value }) {
  if (value === null || value === undefined) return null;
  return (
    <pre className="mt-3 max-h-80 overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-100">
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}
