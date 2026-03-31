package com.carelink.scheduling.application;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import com.carelink.scheduling.domain.value.AppointmentStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para actualizar el estado de una cita.
 */
@Service
public final class UpdateAppointmentStatusUseCase {

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
    public UpdateAppointmentStatusUseCase(
            final AppointmentRepository appointmentRepositoryArg,
            final AppointmentEventPublisher eventPublisherArg) {
        this.appointmentRepository = appointmentRepositoryArg;
        this.eventPublisher = eventPublisherArg;
    }

    /**
     * Actualiza el estado de una cita dentro de un tenant.
     *
     * @param tenantId identificador del tenant
     * @param id       identificador de la cita
     * @param status   nuevo estado
     * @return cita actualizada
     */
    public Appointment updateStatus(final UUID tenantId,
                                    final UUID id,
                                    final AppointmentStatus status) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);

        final Appointment current = appointmentRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(
                () -> new IllegalArgumentException("Appointment not found")
            );
        final Appointment updated = current.withStatus(status);
        final Appointment saved = appointmentRepository.save(updated);
        eventPublisher.publishStatusChanged(saved);
        return saved;
    }
}
