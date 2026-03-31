package com.carelink.scheduling.infrastructure.messaging;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador Kafka para publicación de eventos de appointments.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public final class KafkaAppointmentEventPublisher
    implements AppointmentEventPublisher {

    /** Topic de eventos de citas. */
    private static final String TOPIC = "appointments";

    /** Template Kafka para publicación. */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Mapper JSON para serialización de payloads. */
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     *
     * @param kafkaTemplateArg template de Kafka
     * @param objectMapperArg  mapper JSON
     */
    public KafkaAppointmentEventPublisher(
            final KafkaTemplate<String, String> kafkaTemplateArg,
            final ObjectMapper objectMapperArg) {
        this.kafkaTemplate = kafkaTemplateArg;
        this.objectMapper = objectMapperArg;
    }

    @Override
    public void publishBooked(final Appointment appointment) {
        publish("AppointmentBooked", appointment);
    }

    @Override
    public void publishCancelled(final Appointment appointment) {
        publish("AppointmentCancelled", appointment);
    }

    @Override
    public void publishStatusChanged(final Appointment appointment) {
        publish("AppointmentStatusChanged", appointment);
    }

    private void publish(
            final String eventName,
            final Appointment appointment
    ) {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("event_name", eventName);
        payload.put("appointment_id", appointment.id());
        payload.put("tenant_id", appointment.tenantId());
        payload.put("physician_id", appointment.physicianId());
        payload.put("patient_id", appointment.patientId());
        payload.put("slot_start", appointment.start().toString());
        payload.put("duration_minutes", appointment.duration().toMinutes());
        payload.put("status", appointment.status().name());

        try {
            final String body = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, appointment.id().toString(), body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize appointment event",
                    e
            );
        }
    }
}
