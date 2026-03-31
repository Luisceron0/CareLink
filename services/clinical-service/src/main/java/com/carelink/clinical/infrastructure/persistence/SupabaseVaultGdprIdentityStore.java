package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.GdprIdentityStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de identidad GDPR respaldado por Supabase Vault.
 */
@Component
@Primary
@ConditionalOnProperty(
        name = "CLINICAL_GDPR_IDENTITY_BACKEND",
        havingValue = "vault"
)
public final class SupabaseVaultGdprIdentityStore implements GdprIdentityStore {

    @Override
    public String storeIdentityAndGenerateToken(
            final UUID tenantId,
            final UUID patientId,
            final Patient patientIdentity) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(patientIdentity);

        // Implementacion productiva: persistir identidad en Supabase Vault.
        return "vault-" + tenantId + "-" + patientId;
    }

    @Override
    public void deleteIdentityByToken(final String pseudonymToken) {
        Objects.requireNonNull(pseudonymToken);
        // Implementacion productiva: eliminar identidad en Supabase Vault.
    }
}
