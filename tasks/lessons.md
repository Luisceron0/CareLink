# Lessons Learned — CareLink

## 2026-03-25

- Definir instrucciones de Agent mode en repositorio desde el inicio evita deriva de arquitectura.
- Mantener un `tasks/todo.md` vivo reduce retrabajo en sesiones largas o interrumpidas.

## 2026-03-31

- En módulos con Checkstyle estricto (80 cols), conviene validar longitud de línea durante cada parche para evitar bucles de corrección al final.
- Para conflictos de agenda, combinar validación previa + `@Version` + índice único parcial reduce riesgo de double booking bajo concurrencia.
- Para PHI en dominio, mantener cifrado detrás de `EncryptionPort` permite endurecer seguridad sin acoplar casos de uso a proveedores KMS/Vault.
- En Spring Boot con reglas `FinalClass`, usar `proxyBeanMethods = false` permite clases de configuración/fachada finales sin romper el `ApplicationContext`.
- Para puertos opcionales en entorno local, es más robusto registrar beans fallback explícitos y marcar implementaciones reales con prioridad.
