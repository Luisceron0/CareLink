'use client';

import React, { useEffect, useMemo, useState } from 'react';
import type { Appointment, AppointmentStatus } from '../../lib/types';

type Props = {
  initialItems: Appointment[];
  tenantId: string;
};

const API_BASE = 'http://localhost:8081/api/v1/appointments';

export default function AppointmentsClient({ initialItems, tenantId }: Props) {
  const [items, setItems] = useState<Appointment[]>(initialItems);
  const [error, setError] = useState<string>('');

  const headers = useMemo(
    () => ({
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId
    }),
    [tenantId]
  );

  useEffect(() => {
    let isActive = true;

    async function poll() {
      try {
        const res = await fetch(API_BASE, {
          headers,
          cache: 'no-store'
        });
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data = (await res.json()) as Appointment[];
        if (isActive) {
          setItems(data);
        }
      } catch (e) {
        if (isActive) {
          setError('No se pudo actualizar la lista.');
        }
      }
    }

    const id = setInterval(poll, 30_000);
    poll();

    return () => {
      isActive = false;
      clearInterval(id);
    };
  }, [headers]);

  async function updateStatus(id: string, status: AppointmentStatus) {
    const role = status === 'IN_PROGRESS' || status === 'COMPLETED'
      ? 'PHYSICIAN'
      : 'RECEPTIONIST';

    const res = await fetch(`${API_BASE}/${id}/status`, {
      method: 'PATCH',
      headers: {
        ...headers,
        'X-User-Role': role
      },
      body: JSON.stringify({ status })
    });
    if (!res.ok) {
      setError('No fue posible cambiar el estado.');
      return;
    }
    const updated = (await res.json()) as Appointment;
    setItems(prev => prev.map(it => (it.id === id ? updated : it)));
  }

  return (
    <div>
      {error ? <p style={{ color: 'red' }}>{error}</p> : null}
      <ul>
        {items.map(item => (
          <li key={item.id}>
            <strong>{item.status}</strong> - {item.slotStart}
            <div>
              <button type="button" onClick={() => updateStatus(item.id, 'CONFIRMED')}>
                Confirmar
              </button>
              <button type="button" onClick={() => updateStatus(item.id, 'IN_PROGRESS')}>
                Iniciar
              </button>
              <button type="button" onClick={() => updateStatus(item.id, 'COMPLETED')}>
                Completar
              </button>
              <button type="button" onClick={() => updateStatus(item.id, 'CANCELLED')}>
                Cancelar
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
