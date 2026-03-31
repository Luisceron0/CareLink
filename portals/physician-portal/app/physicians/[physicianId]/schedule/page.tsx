import React from 'react';
import { cookies } from 'next/headers';
import { getAvailability, createAvailability } from '../../../../lib/api/scheduling';
import { loadMessages } from '../../../../lib/i18n';
import type { AvailabilityBlock } from '../../../../lib/types';

type Props = { params: { physicianId: string } };

export default async function Page({ params }: Props) {
  const { physicianId } = params;
  const cookieStore = cookies();
  const cookieHeader = cookieStore.getAll().map(c => `${c.name}=${c.value}`).join('; ');
  const locale = cookieStore.get('locale')?.value ?? 'es-CO';
  const messages = loadMessages(locale);

  let blocks: AvailabilityBlock[] = [];
  let errorMsg: string | null = null;
  try {
    blocks = await getAvailability(physicianId, cookieHeader);
  } catch (e) {
    errorMsg = messages['error.generic'] ?? 'Error';
  }

  // Server action para crear bloques; pasa por lib/api/scheduling
  async function createAction(formData: FormData) {
    'use server';
    const day = formData.get('day') as string;
    const startTime = formData.get('start') as string;
    const endTime = formData.get('end') as string;
    const block: AvailabilityBlock = {
      physicianId,
      dayOfWeek: day,
      startTime,
      endTime
    };
    try {
      await createAvailability(physicianId, block, cookieHeader);
    } catch (e) {
      // Re-throw to render server error; page will show generic message
      throw e;
    }
  }

  return (
    <div style={{ padding: 16 }}>
      <h1>{messages['schedule.title']}</h1>
      {errorMsg ? (
        <div style={{ color: 'red' }}>{errorMsg}</div>
      ) : blocks.length === 0 ? (
        <div>{messages['schedule.empty']}</div>
      ) : (
        <ul>
          {blocks.map((b: AvailabilityBlock) => (
            <li key={b.id}>{`${b.dayOfWeek} ${b.startTime} - ${b.endTime}`}</li>
          ))}
        </ul>
      )}

      <h2>{messages['schedule.create.title']}</h2>
      <form action={createAction}>
        <div>
          <label>
            {messages['form.day.label']}
            <input name="day" />
          </label>
        </div>
        <div>
          <label>
            {messages['form.start.label']}
            <input name="start" type="time" />
          </label>
        </div>
        <div>
          <label>
            {messages['form.end.label']}
            <input name="end" type="time" />
          </label>
        </div>
        <div>
          <button type="submit">{messages['form.submit']}</button>
        </div>
      </form>
    </div>
  );
}
