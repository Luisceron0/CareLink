package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Patient;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de pacientes.
 */
public interface PatientRepository {

    /**
     * Guarda un paciente.
     *
     * @param patient paciente
     * @return paciente persistido
     */
    Patient save(Patient patient);

    /**
     * Busca paciente por tenant e id.
     *
     * @param tenantId tenant
     * @param patientId paciente
     * @return paciente si existe
     */
    Optional<Patient> findByTenantAndId(UUID tenantId, UUID patientId);

    /**
     * Verifica existencia de paciente sin filtrar tenant.
     *
     * @param patientId id del paciente
     * @return true si existe en cualquier tenant
     */
    default boolean existsById(final UUID patientId) {
        return false;
    }
}
