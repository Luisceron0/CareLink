# ADR-016 — Infra: Railway / Supabase / Upstash / Confluent Cloud

## Estado
**Superado por ADR-012 y ADR-015.** No implementar. Se conserva como registro de la
decisión y de por qué se revirtió — un ADR superado no se borra.

## Nota de renumeración (2026-08-04)
Este documento se publicó originalmente como *ADR-008*, número que el SRS §17 ya
tenía asignado a "GDPR Erasure vs. Colombian Retention (Res. 1995/1999)". Dos ADR
distintos con el mismo número hacen que la referencia `ADR-008` sea ambigua, así que
este —el que llegó después— pasa a ADR-016. El contenido queda intacto.

## Por qué quedó superado
- **Sin Docker → con Docker.** La premisa central de este ADR ("simplificar el flujo
  de desarrollo sin Docker") fue revertida por **ADR-012**: el stack local es Docker
  Compose (backend + PostgreSQL 16 + frontend). Con tres componentes en juego, un
  comando reproducible vale más que evitar el runtime de contenedores.
- **Sin demo público.** **ADR-015** elimina todo entorno desplegado. Railway, Vercel y
  los límites de plan gratuito dejan de ser restricciones sobre las que decidir: los
  únicos entornos son local y CI.
- **Kafka y Redis salen del stack.** SRS §9 es explícito: nada de §5 los requiere.
  Confluent Cloud y Upstash dejan de tener función.
- **Segundo runtime backend.** Este ADR asumía servicios FastAPI junto a Spring Boot.
  §9 descarta un segundo runtime backend, y ADR-010 ya eliminó el que existía.

Lo único que sobrevive conceptualmente es el uso de un Vault para material de claves,
que se resuelve en **ADR-004** (JWKS con proveedor de claves respaldado por Vault) y
**ADR-017**, sin acoplarse a Supabase.

---

*Contenido original, sin modificar, desde acá:*

## Contexto
El repositorio y la documentación inicial referenciaban infraestructura AWS (ECS, RDS, ElastiCache, MSK, KMS, S3) y herramientas como Trivy para SCA. Se decidió un cambio de proveedor para simplificar el flujo de desarrollo sin Docker y usar plataformas serverless/managed más alineadas al equipo: Railway para despliegue de servicios, Supabase para Postgres + Vault + Storage, Upstash para Redis serverless y Confluent Cloud para Kafka.

## Decisión
Adoptar la siguiente composición de infraestructura gestionada:
- Servicios (Spring Boot, FastAPI): Railway (JARs / Nixpacks, sin Dockerfile)
- Base de datos y Vault: Supabase (Proyectos/Postgres + Supabase Vault)
- Redis: Upstash (serverless)
- Kafka: Confluent Cloud (KRaft mode)
- Almacenamiento de archivos: Supabase Storage
- CI: semgrep, `mvn dependency-check`, `pip-audit`, Gitleaks (sin Trivy ni uso del daemon Docker)
- PHI keys: almacenadas por tenant en Supabase Vault

## Alternativas consideradas
- Mantener AWS (ECS/RDS/ElastiCache/MSK/KMS): pros: madurez y control operativo; cons: mayor complejidad operativa, coste inicial, mayor fricción para desarrolladores locales.
- Usar DigitalOcean/App Platform + Managed PostgreSQL + Redis: pros: menor vendor lock-in; cons: falta de Vault integrado y experiencia previa del equipo con Supabase.

## Consecuencias
- Positivas:
  - Desarrollo local y CI sin Docker; tests integrados con Zonky/@EmbeddedKafka funcionan en runners nativos.
  - Despliegue simplificado (Railway, Vercel) y menor fricción en onboarding.
  - PHI keys centralizadas en Supabase Vault, coherente con política de secretos en Railway/Vercel.
- Negativas / trade-offs:
  - Dependencia de múltiples proveedores gestionados (vendor lock-in parcial).
  - Límites en planes gratuitos (Confluent/Upstash/Railway) pueden requerir upgrades para staging/CI a escala.
  - IaC y documentación deben adaptarse para usar los providers correspondientes (proveedor Supabase, Confluent, Upstash).

## Trigger de revisión
Revisar esta decisión si alguno de los siguientes ocurre:
- Requerimientos de rendimiento o aislamiento que superen capacidades de los proveedores seleccionados.
- Costos operativos en producción que hagan preferible migrar a infra propia o a otro proveedor.
- Si el equipo decide adoptar Docker/contenerización, evaluar migración a Testcontainers y orquestador (p.ej. ECS, Kubernetes).
