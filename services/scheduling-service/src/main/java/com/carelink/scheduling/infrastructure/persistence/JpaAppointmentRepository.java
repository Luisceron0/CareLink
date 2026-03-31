package com.carelink.scheduling.infrastructure.persistence;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import com.carelink.scheduling.domain.value.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación JPA del puerto de repositorio de citas.
 */
@Component
public final class JpaAppointmentRepository implements AppointmentRepository {

    /** Estados que ocupan un slot para validación de conflicto. */
    private static final List<String> ACTIVE_STATUSES = List.of(
            AppointmentStatus.PENDING.name(),
            AppointmentStatus.CONFIRMED.name(),
            AppointmentStatus.IN_PROGRESS.name()
    );

    /** Repositorio Spring Data subyacente. */
    private final SpringDataAppointmentRepository repository;

    /**
     * Constructor.
     *
     * @param repositoryArg repositorio Spring Data
     */
    public JpaAppointmentRepository(
            final SpringDataAppointmentRepository repositoryArg
    ) {
        this.repository = repositoryArg;
    }

    @Override
    public List<Appointment> findConflicts(final UUID physicianId,
                                           final LocalDateTime start,
                                           final LocalDateTime end) {
        return repository
            .findByPhysicianIdAndStatusIn(physicianId, ACTIVE_STATUSES)
                .stream()
            .filter(e -> overlaps(
                e.getSlotStart(),
                e.getSlotStart().plusMinutes(e.getDurationMinutes()),
                start,
                end
            ))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Appointment save(final Appointment appointment) {
        final AppointmentEntity saved = repository.save(toEntity(appointment));
        return toDomain(saved);
    }

    @Override
    public Optional<Appointment> findById(final UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Appointment> findByTenantAndId(final UUID tenantId,
                                                   final UUID id) {
        return repository.findByTenantIdAndId(tenantId, id).map(this::toDomain);
    }

    @Override
    public List<Appointment> listByTenant(final UUID tenantId,
                                          final UUID physicianId,
                                          final LocalDate date,
                                          final AppointmentStatus status) {
        return repository.findByTenantIdOrderBySlotStartAsc(tenantId)
                .stream()
                .map(this::toDomain)
                .filter(
                    a -> physicianId == null
                        || physicianId.equals(a.physicianId())
                )
                .filter(
                    a -> date == null || date.equals(a.start().toLocalDate())
                )
                .filter(a -> status == null || status == a.status())
                .collect(Collectors.toList());
    }

    private Appointment toDomain(final AppointmentEntity entity) {
        return new Appointment(
                entity.getId(),
                entity.getTenantId(),
                entity.getPhysicianId(),
                entity.getPatientId(),
                entity.getSlotStart(),
                Duration.ofMinutes(entity.getDurationMinutes()),
                AppointmentStatus.valueOf(entity.getStatus())
        );
    }

    private AppointmentEntity toEntity(final Appointment appointment) {
        final AppointmentEntity entity = new AppointmentEntity(
                appointment.id(),
                appointment.tenantId(),
                appointment.physicianId(),
                appointment.patientId(),
                appointment.start(),
                appointment.duration().toMinutes(),
                appointment.status().name()
        );
        return entity;
    }

    private boolean overlaps(final LocalDateTime currentStart,
                             final LocalDateTime currentEnd,
                             final LocalDateTime candidateStart,
                             final LocalDateTime candidateEnd) {
        return currentStart.isBefore(candidateEnd)
                && currentEnd.isAfter(candidateStart);
    }
}
