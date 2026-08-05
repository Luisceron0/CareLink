package com.carelink.identity.domain.value;

import java.util.Set;

/**
 * Roles válidos definidos en el SRS §4 — única fuente de verdad, para no repetir
 * esta lista (y que diverja) en cada lugar que necesite validarla. {@code role}
 * sigue siendo {@code String} en {@link com.carelink.identity.domain.User} y en el
 * JWT — convertirlo a enum en todo el codebase es un refactor más grande que lo
 * que FR-ID-02 pide.
 */
public final class KnownRoles {

    public static final Set<String> ALL = Set.of(
            "TENANT_ADMIN", "PHYSICIAN", "NURSE", "SPECIALIST",
            "PHARMACIST", "LAB_TECH", "ADMISSIONS", "AUDITOR");

    private KnownRoles() {}

    public static boolean isValid(String role) {
        return role != null && ALL.contains(role);
    }
}
