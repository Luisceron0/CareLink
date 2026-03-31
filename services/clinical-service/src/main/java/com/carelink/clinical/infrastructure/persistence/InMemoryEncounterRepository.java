package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.port.EncounterRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistencia en memoria para encuentros.
 */
@Component
public final class InMemoryEncounterRepository
        implements EncounterRepository {

    /** Almacen en memoria por id de encuentro. */
    private final Map<UUID, Encounter> storage = new ConcurrentHashMap<>();

    @Override
    public Encounter save(final Encounter encounter) {
        storage.put(encounter.id(), encounter);
        return encounter;
    }

    @Override
    public Optional<Encounter> findByTenantAndId(final UUID tenantId,
                                                 final UUID encounterId) {
        final Encounter encounter = storage.get(encounterId);
        if (encounter == null) {
            return Optional.empty();
        }
        if (!tenantId.equals(encounter.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(encounter);
    }
}
