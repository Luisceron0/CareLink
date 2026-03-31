package com.carelink.scheduling.infrastructure.messaging;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publicador no-op para ambientes sin Kafka.
 */
@Component
@org.springframework.boot.autoconfigure.condition
    .ConditionalOnMissingBean(AppointmentEventPublisher.class)
public final class NoOpAppointmentEventPublisher
        implements AppointmentEventPublisher {

    @Override
    public void publishBooked(final Appointment appointment) {
        // no-op
    }

    @Override
    public void publishCancelled(final Appointment appointment) {
        // no-op
    }

    @Override
    public void publishStatusChanged(final Appointment appointment) {
        // no-op
    }
}
