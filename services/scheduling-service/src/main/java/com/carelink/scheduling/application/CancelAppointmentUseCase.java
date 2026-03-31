package com.carelink.scheduling.application;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import com.carelink.scheduling.domain.value.AppointmentStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para cancelar una cita (soft delete por estado).
 */
@Service
public final class CancelAppointmentUseCase {

    /** Repositorio de citas. */
    private final AppointmentRepository appointmentRepository;

    /** Publicador de eventos de citas. */
    private final AppointmentEventPublisher eventPublisher;

    /**
     * Constructor.
     *
     * @param appointmentRepositoryArg repositorio de citas
     * @param eventPublisherArg        publicador de eventos
     */
    public CancelAppointmentUseCase(
            final AppointmentRepository appointmentRepositoryArg,
            final AppointmentEventPublisher eventPublisherArg) {
        this.appointmentRepository = appointmentRepositoryArg;
        this.eventPublisher = eventPublisherArg;
    }

    /**
     * Cancela una cita por tenant.
     *
     * @param tenantId identificador del tenant
     * @param id       identificador de la cita
     * @return cita cancelada
     */
    public Appointment cancel(final UUID tenantId, final UUID id) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(id);

        final Appointment current = appointmentRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(
                () -> new IllegalArgumentException("Appointment not found")
            );
        final Appointment cancelled = current.withStatus(
            AppointmentStatus.CANCELLED
        );
        final Appointment saved = appointmentRepository.save(cancelled);
        eventPublisher.publishCancelled(saved);
        return saved;
    }
}
