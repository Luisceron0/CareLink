package com.carelink.scheduling.infrastructure.persistence;

import com.carelink.scheduling.domain.AvailabilityBlock;
import com.carelink.scheduling.domain.port.AvailabilityRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación JPA del puerto AvailabilityRepository.
 */
@Component
public final class JpaAvailabilityRepository implements AvailabilityRepository {

    /** Spring Data repository usado internamente. */
    private final SpringDataAvailabilityRepository repo;

    /**
     * Constructor.
     *
     * @param repoArg repositorio Spring Data
     */
    public JpaAvailabilityRepository(
            final SpringDataAvailabilityRepository repoArg
    ) {
        this.repo = repoArg;
    }

    /** {@inheritDoc} */
    @Override
    public List<AvailabilityBlock> findByPhysicianId(final UUID physicianId) {
        return repo.findByPhysicianId(physicianId)
                .stream()
                .map(e -> new AvailabilityBlock(
                        e.getId(),
                        e.getPhysicianId(),
                        DayOfWeek.of(e.getDayOfWeek()),
                        e.getStartTime(),
                        e.getEndTime()
                ))
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public AvailabilityBlock save(final AvailabilityBlock block) {
        final AvailabilityBlockEntity e = new AvailabilityBlockEntity(
                block.id(),
                block.physicianId(),
                block.dayOfWeek(),
                block.startTime(),
                block.endTime()
        );
        final AvailabilityBlockEntity saved = repo.save(e);
        return new AvailabilityBlock(
                saved.getId(),
                saved.getPhysicianId(),
                DayOfWeek.of(saved.getDayOfWeek()),
                saved.getStartTime(),
                saved.getEndTime()
        );
    }
}
