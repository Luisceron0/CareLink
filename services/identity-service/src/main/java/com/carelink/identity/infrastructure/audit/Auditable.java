package com.carelink.identity.infrastructure.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de caso de uso cuya ejecución debe producir una fila de
 * {@code audit_log} (FR-CLN-13). Lo intercepta {@link AuditAspect} — nunca se invoca
 * el logging a mano desde un controller o caso de uso: "Auditoría vía AOP, no por
 * disciplina" (copilot-instructions.md). Un endpoint nuevo que se olvida de anotar
 * no debería poder auditar por accidente — y uno que sí se anota no debería poder
 * saltearse el registro aunque falle, porque el aspecto registra el resultado
 * {@code ERROR} en ese caso, no se lo salta.
 *
 * <p>Los atributos {@code *Expression} son SpEL evaluado contra los parámetros del
 * método interceptado — por nombre, con el mismo mecanismo que
 * {@code @PreAuthorize}. Ejemplo:
 *
 * <pre>{@code
 * @Auditable(action = "PATIENT_READ", tenantSlugExpression = "#tenantSlug",
 *            patientIdExpression = "#patientId")
 * Patient readPatient(String tenantSlug, UUID patientId) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Acción registrada en {@code audit_log.action} — texto corto y estable, ej. "PATIENT_READ". */
    String action();

    /**
     * SpEL requerida: de qué tenant es esta operación, sin el prefijo {@code tenant_}.
     * Sin tenant no hay a qué schema escribir la fila.
     */
    String tenantSlugExpression();

    /** SpEL opcional: UUID del paciente afectado, si la operación tiene uno. */
    String patientIdExpression() default "";

    /** SpEL opcional: IP de origen del request, si el caller la tiene disponible. */
    String sourceIpExpression() default "";

    /** SpEL opcional: ID de sesión, si el caller lo tiene disponible. */
    String sessionIdExpression() default "";
}
