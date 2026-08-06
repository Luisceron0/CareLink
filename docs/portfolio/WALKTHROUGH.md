# Guion de walkthrough — checklist para grabar

Sustituye al demo público que ADR-015 decidió no desplegar. El objetivo no es mostrar
"que funciona" — es mostrar las decisiones de seguridad que un stack CRUD normal no
tiene, con evidencia en pantalla, no en un README que hay que creerle.

**Duración objetivo: 6–9 minutos.** Más largo que eso pierde a quien lo mira antes de
llegar a la parte de seguridad, que es la más valiosa.

---

## 0. Antes de grabar

- [ ] `git status` limpio, en la rama que vas a mostrar.
- [ ] `docker compose down -v && docker compose up -d --build` — desde cero, para que el
      primer paciente/tenant que se cree en cámara sea real, no uno que ya estaba.
- [ ] Confirmar los cuatro servicios `healthy`: `docker compose ps`.
- [ ] Tener a mano, en pestañas separadas: `http://localhost:5173` (SPA),
      `http://localhost:8025` (Mailpit), y una terminal.
- [ ] Grabación de pantalla en 1080p mínimo. Si el texto de la terminal no se lee, es
      mejor cortar la terminal que bajar la resolución.
- [ ] Silenciar notificaciones del sistema.
- [ ] Un guion hablado corto por escena (abajo) — no leerlo palabra por palabra en
      cámara, pero tenerlo memorizado en la idea.

---

## 1. Apertura (20 s)

**Mostrar:** el README, scrolleado hasta la tabla de sub-fases.

**Decir:** "CareLink es una implementación de referencia de una plataforma clínica
multi-tenant — el dominio de un hospital público colombiano construido sobre una
arquitectura multi-tenant. Nueve sub-fases, cada una cerrada con su criterio de
aceptación verificado contra el sistema real corriendo, no solo con tests en verde."

---

## 2. Arranque y aislamiento de roles (60 s)

**Mostrar:** `docker compose ps` con los cuatro contenedores `healthy`. Cambiar a la SPA,
pantalla de login.

**Hacer:**
1. Login como `TENANT_ADMIN` (usuario sembrado o recién registrado).
2. Ir a **Usuarios**, invitar un `PHYSICIAN` con servicio "Urgencias".
3. Cambiar a la pestaña de **Mailpit** — mostrar el correo real capturado, con el token.
4. Volver a la SPA, aceptar la invitación con ese token, loguearse como el nuevo
   `PHYSICIAN`.
5. Señalar la barra de navegación: **solo** las secciones que le corresponden a ese rol.

**Decir:** "La invitación de usuarios manda un correo de verdad — acá no hay SMTP real,
así que un catcher local lo captura. Es el mismo mecanismo que se usaría en producción,
sin fingir la parte de 'envía un correo'. Y la navegación por rol es solo UX: cada
endpoint valida el rol, el tenant y el servicio por su cuenta — ocultar un botón no es el
control de seguridad."

---

## 3. Historia clínica inmutable (60 s)

**Mostrar:** vista **Encuentros**, como el `PHYSICIAN` recién logueado.

**Hacer:**
1. Registrar un paciente (vista **Pacientes**).
2. Crear un encuentro clínico para ese paciente.
3. Click en **Firmar**.
4. Click en **Intentar editar** — mostrar el 409 con el mensaje explícito en pantalla.

**Decir:** "Después de firmado, el encuentro es inmutable — no por una validación de la
aplicación que alguien con acceso directo a la base podría saltear, sino por un trigger
de PostgreSQL que rechaza el UPDATE para cualquier rol, incluido el administrador. Ley
527 de 1999 lo exige así."

---

## 4. El motor de conocimiento y k-anonimato (60 s) — la escena más fuerte para seguridad

**Mostrar:** vista **Conocimiento**.

**Hacer:**
1. Buscar por un diagnóstico con pocos casos cargados (o cargar 1–2 antes, vía Diario) →
   mostrar **"Datos insuficientes"** con la aclaración de que no es lo mismo que "sin
   casos".
2. Buscar por un diagnóstico sin ningún caso → mostrar **"Sin casos previos"** — el
   contraste visual entre los dos estados.

**Decir:** "Esto es lo más distintivo del proyecto: el motor de conocimiento agrega
intervenciones de enfermería por efectividad, pero nunca muestra un resultado con menos
de cinco pacientes distintos — el umbral se aplica dentro de la consulta SQL, no
filtrando en el frontend. Y estos dos mensajes son estados distintos a propósito: uno
dice 'hay datos pero no te los puedo mostrar sin riesgo de identificar a alguien', el
otro dice 'no hay nada'. Confundirlos sería una afirmación clínica falsa."

---

## 5. Revocación de acceso en tiempo real (75 s) — la segunda escena fuerte

**Mostrar:** terminal con dos requests curl preparados (más contundente que la UI acá,
porque se ve el mismo token pasando de 200 a 403 sin re-login).

**Hacer (guion de terminal, adaptar IDs):**
```bash
# 1. Como PHYSICIAN: solicitar interconsulta a un SPECIALIST
curl -s -X POST localhost:8080/api/v1/interconsultations \
  -H "Authorization: Bearer $PHYS_TOKEN" -H "Content-Type: application/json" \
  -d '{"patientId":"...","encounterId":"...","specialistUserId":"...","question":"..."}'

# 2. Como SPECIALIST: leer la interconsulta -> 200
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/v1/interconsultations/$IC_ID \
  -H "Authorization: Bearer $SPEC_TOKEN"

# 3. Como PHYSICIAN: cerrar la interconsulta
curl -s -X POST localhost:8080/api/v1/interconsultations/$IC_ID/close \
  -H "Authorization: Bearer $PHYS_TOKEN"

# 4. Como SPECIALIST, MISMO TOKEN: reintentar -> 403
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/v1/interconsultations/$IC_ID \
  -H "Authorization: Bearer $SPEC_TOKEN"
```

**Decir:** "Este es el mismo JWT del especialista, sin volver a loguearse, en los dos
requests. El acceso no se guarda en ninguna tabla de permisos — cada request pregunta
'¿tiene una interconsulta abierta para este paciente ahora mismo?'. Cerrar la
interconsulta es un solo UPDATE, y el siguiente request cae. No hay un segundo paso de
revocación que alguien pueda olvidarse de ejecutar."

---

## 6. Laboratorio: valor crítico (45 s)

**Hacer:**
1. Como `PHYSICIAN`, ordenar un estudio.
2. Como `LAB_TECH`, cargar el resultado marcando **"Valor crítico"**.
3. Volver como `PHYSICIAN`, mostrar la notificación pendiente en la vista de
   Laboratorio.

**Decir:** "Sin integración de email en este milestone, así que 'notificar' es una fila
que el médico consulta — una obligación abierta hasta que la acusa recibo, no un evento
que se pierde si nadie estaba mirando."

---

## 7. Cierre (20 s)

**Mostrar:** `docs/security/AUDIT-2026-08-06.md` scrolleado brevemente, y
`tasks/lessons.md`.

**Decir:** "El repo tiene una auditoría de seguridad completa, con un hallazgo real de
severidad alta que se encontró y se corrigió — un secreto con valor por defecto que el
propio gate de CI escrito para prevenirlo no detectaba. Y `lessons.md` documenta ese y
otros defectos reales encontrados durante la construcción, no solo el código final."

---

## Después de grabar

- [ ] Recortar tiempos muertos (cargas de Docker, esperas de red).
- [ ] Exportar una versión corta como GIF (15–20 s, la escena 5 o la 4) para el README —
      un GIF se ve antes de que alguien decida si vale la pena mirar el video completo.
- [ ] Subir el video completo donde corresponda (YouTube no listado, o adjunto al README
      si el hosting lo permite) y enlazarlo desde el README, sección "Qué hay construido
      hoy" o una nueva sección "Demo".
- [ ] Si se sube a algún lado público, revisar que ningún token/secreto real quede visible
      en cámara — los de este walkthrough son todos sintéticos y locales, pero conviene
      el hábito de revisar antes de publicar.
