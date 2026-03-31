import React from 'react';
import type { ReactNode } from 'react';
import { cookies } from 'next/headers';
import { loadMessages } from '../lib/i18n';

export default async function RootLayout({ children }: { children: ReactNode }) {
  // Determinamos locale desde cookies del servidor; por simplicidad usamos es-CO por defecto
  const cookieStore = cookies();
  const localeCookie = cookieStore.get('locale');
  const locale = localeCookie?.value ?? 'es-CO';
  const messages = loadMessages(locale);

  return (
    <html lang={locale}>
      <body>
        {/* Proveer messages a la app a través de window.__MESSAGES__ como fallback */}
        <script dangerouslySetInnerHTML={{ __html: `window.__MESSAGES__=${JSON.stringify(messages)}` }} />
        {children}
      </body>
    </html>
  );
}
