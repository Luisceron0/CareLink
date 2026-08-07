# Capturas — flujo real, no mockups

Sustituto provisional del video de [WALKTHROUGH.md](WALKTHROUGH.md) mientras no se graba: las
mismas escenas, capturadas contra el stack real (`docker compose up`) con `agent-browser`, un
tenant nuevo (`portafolio`) creado en el momento, y datos 100% sintéticos. Ningún paso está
simulado en el frontend — cada pantalla es la respuesta real de la API tras la acción anterior.

---

## 1–2. Arranque y aislamiento de roles

| | |
|---|---|
| ![login](screenshots/01-login.png) | Pantalla de login. |
| ![admin nav](screenshots/02-admin-full-nav.png) | `TENANT_ADMIN` recién logueado — nav completa, los nueve módulos. |
| ![mailpit token](screenshots/03-mailpit-invitation-token.png) | La invitación a un `PHYSICIAN` capturada por Mailpit, con el token real — mismo mecanismo que usaría un SMTP de producción. |
| ![physician nav](screenshots/04-physician-scoped-nav.png) | El mismo usuario, ya logueado como `PHYSICIAN`: nav reducida a **Pacientes, Encuentros, Conocimiento, Interconsultas, Laboratorio, Farmacia** — sin Admisiones, Diario ni Usuarios. Ocultar el botón es UX; el control real es que cada endpoint valida rol+tenant+servicio por su cuenta (ver más abajo el 409 y el 403 sobre el mismo JWT). |
| ![patient registered](screenshots/05-patient-registered.png) | Paciente registrado — la respuesta de la API confirma los campos PHI. |

## 3. Historia clínica inmutable

| | |
|---|---|
| ![encounter signed](screenshots/06-encounter-signed.png) | Encuentro firmado — `signed: true`, `signedAt` con timestamp real. |
| ![409](screenshots/07-encounter-edit-409.png) | El mismo `PHYSICIAN` intenta editarlo: **409**, rechazado por un trigger de PostgreSQL, no por una validación de la aplicación que un acceso directo a la base podría saltear. |

## 4. Motor de conocimiento y k-anonimato — la escena más fuerte para seguridad

| | |
|---|---|
| ![sin casos](screenshots/08-knowledge-sin-casos.png) | Diagnóstico sin ningún caso cargado → **"Sin casos previos"**. |
| ![datos insuficientes](screenshots/09-knowledge-datos-insuficientes.png) | Mismo diagnóstico, ahora con 3 pacientes distintos con la misma intervención (por debajo del umbral k=5) → **"Datos insuficientes"**, un estado distinto a propósito. El umbral se aplica en un `HAVING COUNT(DISTINCT patient_id) >= 5` dentro del SQL — no hay fila que "casi" se muestre y se filtre después. |

## 5. Revocación de acceso en tiempo real

| | |
|---|---|
| ![interconsulta 200→403](screenshots/10-interconsultation-revocation.png) | Terminal real: el `SPECIALIST` lee la interconsulta (200), el `PHYSICIAN` la cierra, y el **mismo JWT del especialista, sin volver a loguearse**, cae a 403 en el siguiente request. El acceso no vive en una tabla de permisos — cada request pregunta "¿hay una interconsulta abierta ahora mismo?". |

## 6. Laboratorio: valor crítico

| | |
|---|---|
| ![notificación crítica](screenshots/11-lab-critical-notification.png) | El `LAB_TECH` carga un resultado marcado como crítico; el `PHYSICIAN` solicitante ve la notificación pendiente al volver a Laboratorio — una obligación abierta hasta que la acusa recibo, no un evento que se pierde. |

---

**Nota de reproducibilidad:** todas las capturas usan un tenant (`portafolio`) y usuarios
(`admin@portafolio.test`, `medico@portafolio.test`, `enfermera@portafolio.test`,
`especialista@portafolio.test`, `labtech@portafolio.test`) creados exclusivamente para esta
sesión de capturas, con contraseñas sintéticas que no se reutilizan en ningún otro entorno.
