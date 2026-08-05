package com.carelink.identity.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FR-ID-03: 5 intentos fallidos / 60s / IP → lockout 15 min + alerta.
 *
 * <p>En memoria, por instancia — no Redis. Consistente con §9: nada en el stack de este
 * milestone requiere Redis, y no hay entorno de producción con más de una instancia
 * corriendo (§1.6, ADR-015) que necesitaría un contador compartido. Si un milestone
 * futuro despliega múltiples instancias, esto necesita un store compartido — no antes.
 *
 * <p>"Alerta" es un log estructurado en WARN, no un canal de notificación externo:
 * §16.4 excluye integraciones vivas de terceros este milestone, y §14 ya declara que
 * los logs se inspeccionan manualmente en esta etapa del proyecto — es el mecanismo de
 * alerta que este milestone realmente tiene, no uno inventado para la ocasión.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, IpState> states = new ConcurrentHashMap<>();

    private static final class IpState {
        final Deque<Instant> failures = new ArrayDeque<>();
        volatile Instant lockedUntil;
    }

    public boolean isLocked(String ip) {
        IpState state = states.get(ip);
        if (state == null) {
            return false;
        }
        Instant until = state.lockedUntil;
        return until != null && Instant.now().isBefore(until);
    }

    public void recordFailure(String ip) {
        IpState state = states.computeIfAbsent(ip, k -> new IpState());
        Instant now = Instant.now();
        synchronized (state) {
            state.failures.addLast(now);
            while (!state.failures.isEmpty()
                    && Duration.between(state.failures.peekFirst(), now).compareTo(WINDOW) > 0) {
                state.failures.pollFirst();
            }
            if (state.failures.size() >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCKOUT);
                log.warn("ALERTA: IP {} bloqueada {} min tras {} intentos de login fallidos en {}s",
                        ip, LOCKOUT.toMinutes(), MAX_FAILURES, WINDOW.getSeconds());
            }
        }
    }

    public void recordSuccess(String ip) {
        IpState state = states.get(ip);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.failures.clear();
            state.lockedUntil = null;
        }
    }

    /**
     * Solo para aislar tests entre sí — este bean es un singleton con estado
     * mutable, y varios tests de {@code AuthControllerSecurityIT} comparten la
     * misma IP simulada de MockMvc. Ninguna ruta de producción llama esto.
     */
    public void resetForTests() {
        states.clear();
    }
}
