package com.carelink.identity.infrastructure.security;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Adelanta al ARRANQUE la validación de {@code REFRESH_TOKEN_HMAC_SECRET}.
 *
 * <p>Sin esto, la validación de {@link TokenHasher} recién dispara cuando se carga esa
 * clase — es decir, en el primer login o refresh. Una aplicación mal configurada
 * respondería {@code /actuator/health} con UP, pasaría cualquier smoke test que no haga
 * login, y fallaría con el primer usuario real. El mismo criterio que
 * {@code DemoModeGuard} aplica a la contención: si una condición de seguridad no se
 * cumple, el arranque falla, no el primer request.
 */
@Component
public class RefreshSecretGuard {

    @PostConstruct
    public void verify() {
        TokenHasher.requireSecret();
    }
}
