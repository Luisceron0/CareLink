package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.GdprIdentityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador en memoria para identidad GDPR desacoplada.
 */
public final class InMemoryGdprIdentityStore implements GdprIdentityStore {

    // TEST ONLY: Este adaptador es exclusivo para tests/local.
    // En produccion debe usarse un adaptador real sobre Supabase Vault.

    /** Almacen temporal de identidad por token. */
        private final Map<String, Patient> identityByToken =
            new ConcurrentHashMap<>();

    @Override
    public String storeIdentityAndGenerateToken(
            final UUID tenantId,
            final UUID patientId,
            final Patient patientIdentity) {
        final String token = "gdpr-" + tenantId + "-" + patientId;
        identityByToken.put(token, patientIdentity);
        return token;
    }

    @Override
    public void deleteIdentityByToken(final String pseudonymToken) {
        identityByToken.remove(pseudonymToken);
    }
}
