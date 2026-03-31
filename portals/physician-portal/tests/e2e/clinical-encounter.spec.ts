import { test, expect } from '@playwright/test';

test('create, sign and block encounter editing', async ({ page, context }) => {
  await context.addCookies([
    {
      name: 'X-Tenant-Id',
      value: '11111111-1111-1111-1111-111111111111',
      domain: '127.0.0.1',
      path: '/'
    },
    {
      name: 'X-User-Id',
      value: '22222222-2222-2222-2222-222222222222',
      domain: '127.0.0.1',
      path: '/'
    },
    {
      name: 'locale',
      value: 'es-CO',
      domain: '127.0.0.1',
      path: '/'
    }
  ]);

  let signed = false;

  await page.route('**/api/v1/patients/*', async route => {
    const req = route.request();
    if (req.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'p-1',
          tenantId: '11111111-1111-1111-1111-111111111111',
          fullName: 'Ada Lovelace',
          documentType: 'CC',
          documentValue: '123456',
          bloodType: 'O_POSITIVE',
          phone: '3000000000',
          email: 'ada@example.com',
          emergencyContact: 'Charles',
          createdAt: new Date().toISOString(),
          allergies: ['penicillin'],
          activeMedications: ['ibuprofen']
        })
      });
      return;
    }

    await route.continue();
  });

  await page.route('**/api/v1/patients/*/encounters', async route => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'enc-1',
        tenantId: '11111111-1111-1111-1111-111111111111',
        patientId: 'p-1',
        physicianId: '22222222-2222-2222-2222-222222222222',
        chiefComplaint: 'Dolor',
        physicalExam: 'Normal',
        treatmentPlan: 'ICD10:R51\\nControl',
        followUpInstructions: 'Sin penicillin',
        signedAt: null,
        createdAt: new Date().toISOString()
      })
    });
  });

  await page.route('**/api/v1/patients/*/encounters/*/sign', async route => {
    signed = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ''
    });
  });

  await page.route('**/api/v1/patients/*/encounters/*', async route => {
    if (route.request().method() !== 'PUT') {
      await route.continue();
      return;
    }

    if (signed) {
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'IMMUTABLE' })
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'enc-1',
        tenantId: '11111111-1111-1111-1111-111111111111',
        patientId: 'p-1',
        physicianId: '22222222-2222-2222-2222-222222222222',
        chiefComplaint: 'Dolor actualizado',
        physicalExam: 'Normal',
        treatmentPlan: 'ICD10:R51\\nControl',
        followUpInstructions: 'Sin penicillin',
        signedAt: null,
        createdAt: new Date().toISOString()
      })
    });
  });

  await page.goto('/patients/p-1/encounters/new');

  await page.getByLabel('Motivo de consulta').fill('Dolor de cabeza');
  await page.getByLabel('Examen físico').fill('Paciente estable');
  await page.getByLabel('Diagnóstico ICD-10').fill('R51');
  await page.getByRole('button', { name: /R51/ }).click();
  await page.getByLabel('Plan terapéutico').fill('Observacion y control');
  await page.getByLabel('Receta').fill('No indicar penicillin');

  await expect(
    page.getByText(/Advertencia clínica|Advertencia clínica:/i)
  ).toBeVisible();

  await page.getByRole('button', { name: 'Guardar encuentro' }).click();
  await expect(page.getByText('Encuentro guardado correctamente.')).toBeVisible();

  await page.getByRole('button', { name: 'Firmar encuentro' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Esta acción es irreversible')).toBeVisible();

  await page.getByRole('button', { name: 'Firmar de forma definitiva' }).click();
  await expect(page.getByText('Encuentro firmado correctamente.')).toBeVisible();
  await expect(page.getByText('Encuentro firmado: modo solo lectura.')).toBeVisible();

  await expect(
    page.getByRole('button', { name: 'Actualizar encuentro' })
  ).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Firmar encuentro' })).toBeDisabled();
  await expect(page.getByLabel('Motivo de consulta')).toHaveJSProperty('readOnly', true);
  await expect(page.getByLabel('Motivo de consulta')).not.toBeEditable();
  await expect(page.getByLabel('Examen físico')).not.toBeEditable();
  await expect(page.getByLabel('Plan terapéutico')).not.toBeEditable();
  await expect(page.getByLabel('Receta')).not.toBeEditable();
});
