package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.PatientRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistencia en memoria para pacientes.
 */
@Component
public final class InMemoryPatientRepository implements PatientRepository {

    /** Almacen en memoria por id de paciente. */
    private final Map<UUID, Patient> storage = new ConcurrentHashMap<>();

    @Override
    public Patient save(final Patient patient) {
        storage.put(patient.id(), patient);
        return patient;
    }

    @Override
    public Optional<Patient> findByTenantAndId(final UUID tenantId,
                                               final UUID patientId) {
        final Patient patient = storage.get(patientId);
        if (patient == null) {
            return Optional.empty();
        }
        if (!tenantId.equals(patient.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(patient);
    }

    @Override
    public boolean existsById(final UUID patientId) {
        return storage.containsKey(patientId);
    }
}
