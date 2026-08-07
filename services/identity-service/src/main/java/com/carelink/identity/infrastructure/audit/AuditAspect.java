package com.carelink.identity.infrastructure.audit;

import com.carelink.identity.domain.audit.AuditEntry;
import com.carelink.identity.domain.audit.AuditResult;
import com.carelink.identity.domain.port.AuditEntryPort;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Intercepta {@link Auditable} y persiste una {@link AuditEntry} por cada invocación
 * — {@code SUCCESS} si el método interceptado termina normalmente, {@code ERROR} si
 * lanza, en ambos casos vía {@link AuditEntryPort} (FR-CLN-13).
 *
 * <p><b>Alcance de esta sub-fase, explícito:</b> este aspecto persiste la fila con el
 * mismo {@code JdbcTemplate} (rol de aplicación) que usaría cualquier operación JPA
 * dentro del mismo método interceptado, así que participa de la misma transacción de
 * base de datos cuando el método interceptado es {@code @Transactional} — pero eso
 * todavía no está ejercitado contra un caso de uso real con escritura JPA, porque
 * ninguno existe todavía (Patient es Sub-fase 2). La garantía "persiste
 * transaccionalmente con la operación principal" se reverifica con un caso de uso
 * real de lectura de PHI en Sub-fase 2, no se da por probada acá con un método de
 * prueba sintético.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditEntryPort auditEntryPort;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditEntryPort auditEntryPort) {
        this.auditEntryPort = auditEntryPort;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        EvaluationContext context = buildContext(joinPoint);
        String tenantSlug = evaluate(context, auditable.tenantSlugExpression(), String.class);

        try {
            Object result = joinPoint.proceed();
            record(auditable, context, tenantSlug, AuditResult.SUCCESS);
            return result;
        } catch (Throwable t) {
            record(auditable, context, tenantSlug, AuditResult.ERROR);
            throw t;
        }
    }

    private void record(Auditable auditable, EvaluationContext context, String tenantSlug, AuditResult result) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        UUID userId = currentUserId(authentication);
        String role = currentRole(authentication);
        UUID patientId = evaluateOptionalUuid(context, auditable.patientIdExpression());
        String sourceIp = evaluateOptionalString(context, auditable.sourceIpExpression());
        UUID sessionId = evaluateOptionalUuid(context, auditable.sessionIdExpression());

        auditEntryPort.record(new AuditEntry(
                tenantSlug, userId, role, patientId, auditable.action(),
                null, sourceIp, sessionId, result, OffsetDateTime.now()));
    }

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] names = parameterNames.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        return context;
    }

    private <T> T evaluate(EvaluationContext context, String spel, Class<T> type) {
        Expression expression = parser.parseExpression(spel);
        return expression.getValue(context, type);
    }

    private String evaluateOptionalString(EvaluationContext context, String spel) {
        return spel.isBlank() ? null : evaluate(context, spel, String.class);
    }

    private UUID evaluateOptionalUuid(EvaluationContext context, String spel) {
        return spel.isBlank() ? null : evaluate(context, spel, UUID.class);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private String currentRole(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .orElse(null);
    }
}
