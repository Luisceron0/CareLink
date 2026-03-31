'use client';

import React, { useState } from 'react';
import { createAppointment } from '../../../lib/api/scheduling';
import { SlotConflictError } from '../../../lib/types';

type Props = {
  tenantId: string;
  labels: {
    title: string;
    physicianId: string;
    patientId: string;
    date: string;
    time: string;
    duration: string;
    create: string;
    conflictTitle: string;
    genericError: string;
  };
};

export default function NewAppointmentClient({ tenantId, labels }: Props) {
  const [alternatives, setAlternatives] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setOk('');
    setAlternatives([]);

    const form = new FormData(event.currentTarget);
    const physicianId = String(form.get('physicianId') ?? '');
    const patientId = String(form.get('patientId') ?? '');
    const date = String(form.get('date') ?? '');
    const time = String(form.get('time') ?? '');
    const durationMinutes = Number(form.get('durationMinutes') ?? '30');
    const slotStart = `${date}T${time}:00`;

    try {
      await createAppointment(
        { physicianId, patientId, slotStart, durationMinutes },
        `X-Tenant-Id=${encodeURIComponent(tenantId)}`
      );
      setOk('OK');
      (event.currentTarget as HTMLFormElement).reset();
    } catch (e) {
      if (e instanceof SlotConflictError) {
        setAlternatives(e.alternatives.slice(0, 3));
      } else {
        setError(labels.genericError);
      }
    }
  }

  return (
    <div style={{ padding: 16 }}>
      <h1>{labels.title}</h1>
      {error ? <p style={{ color: 'red' }}>{error}</p> : null}
      {ok ? <p style={{ color: 'green' }}>{ok}</p> : null}

      <form onSubmit={onSubmit}>
        <div>
          <label>
            {labels.physicianId}
            <input name="physicianId" required />
          </label>
        </div>
        <div>
          <label>
            {labels.patientId}
            <input name="patientId" required />
          </label>
        </div>
        <div>
          <label>
            {labels.date}
            <input name="date" type="date" required />
          </label>
        </div>
        <div>
          <label>
            {labels.time}
            <input name="time" type="time" required />
          </label>
        </div>
        <div>
          <label>
            {labels.duration}
            <input name="durationMinutes" type="number" defaultValue={30} min={15} step={5} />
          </label>
        </div>
        <button type="submit">{labels.create}</button>
      </form>

      {alternatives.length > 0 ? (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            marginTop: 16,
            border: '1px solid #999',
            borderRadius: 8,
            padding: 12,
            background: '#fff7ef'
          }}
        >
          <h2>{labels.conflictTitle}</h2>
          <ul>
            {alternatives.map((alt, index) => (
              <li key={`${alt}-${index}`}>{alt}</li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
