package com.carelink.scheduling.infrastructure.persistence;

import com.carelink.scheduling.domain.Physician;
import com.carelink.scheduling.domain.port.PhysicianRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del puerto PhysicianRepository.
 */
@Component
public final class JpaPhysicianRepository implements PhysicianRepository {

    /** Referencia al repositorio Spring Data. */
    private final SpringDataPhysicianRepository repo;

    /**
     * Constructor.
     *
     * @param repoArg repositorio Spring Data
     */
    public JpaPhysicianRepository(final SpringDataPhysicianRepository repoArg) {
        this.repo = repoArg;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Physician> findByTenantAndId(
            final UUID tenantId,
            final UUID physicianId) {
        return repo.findByIdAndTenantId(physicianId, tenantId)
                .map(e -> new Physician(
                        e.getId(),
                        e.getTenantId(),
                        e.getFullName(),
                        e.getSpecialty()
                ));
    }

    /** {@inheritDoc} */
    @Override
    public Physician save(final Physician physician) {
        final PhysicianEntity e = new PhysicianEntity(
                physician.id(),
                physician.tenantId(),
                physician.fullName(),
                physician.specialty()
        );
        final PhysicianEntity saved = repo.save(e);
        return new Physician(
                saved.getId(),
                saved.getTenantId(),
                saved.getFullName(),
                saved.getSpecialty()
        );
    }
}
