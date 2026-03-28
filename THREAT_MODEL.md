THREAT MODEL — inicio

Superficie de ataque nueva (resumen):
- Endpoints de registro y login (public-facing)
- Proceso de provisionamiento de tenant (creación de esquemas DB)
- Exportación y manejo de PHI (lectura/descarga)

Amenazas identificadas y mitigaciones (inicio):
- Registro masivo / abuso → rate limiting por IP + captcha opcional
- Inyección SQL en queries dinámicas → uso de queries parametrizadas y PReparedStatements
- Acceso cross-tenant → validar tenant_id desde JWT en cada request
- Exposición de PHI en logs → sanitizar logs y evitar imprimir campos PHI

¿Requiere update del THREAT_MODEL global? SÍ — expandir con vectores por servicio y mitigaciones técnicas.

Notas: este documento es un starter; cada feature añadirá sus vectores específicos.
