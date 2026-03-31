package com.carelink.scheduling.application;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.exception.SlotAlreadyBookedException;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para reservar una cita.
 *
 * Valida conflictos y persiste la {@link Appointment} a través del puerto
 * {@link AppointmentRepository}.
 */
@Service
public class BookAppointmentUseCase {

    /** Mínimo paso en minutos para generar alternativas. */
    private static final int MIN_STEP_MINUTES = 15;

    /** Número máximo de alternativas sugeridas. */
    private static final int ALTERNATIVE_COUNT = 3;

    /** Puerto de persistencia para {@link Appointment}. */
    private final AppointmentRepository appointmentRepository;

    /** Publicador de eventos de citas. */
    private final AppointmentEventPublisher eventPublisher;

    /**
     * Constructor.
     *
        * @param repo repositorio de citas (no nulo)
     * @param eventPublisherArg publicador de eventos
     */
    public BookAppointmentUseCase(final AppointmentRepository repo,
                                  final AppointmentEventPublisher
                                          eventPublisherArg) {
        this.appointmentRepository = Objects.requireNonNull(repo);
        this.eventPublisher = Objects.requireNonNull(eventPublisherArg);
    }

    /**
     * Reserva una cita para tenant, physician y patient.
     *
     * Valida conflictos y crea la cita en el instante indicado.
     *
     * @param tenantId    identificador del tenant
     * @param physicianId identificador del médico
     * @param patientId   identificador del paciente
     * @param start       instante de inicio
     * @param duration    duración de la cita
     * @return la appointment creada y persistida
     */
    public Appointment bookAppointment(
            final UUID tenantId,
            final UUID physicianId,
            final UUID patientId,
            final LocalDateTime start,
            final Duration duration) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(physicianId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(start);
        Objects.requireNonNull(duration);

        final LocalDateTime end = start.plus(duration);
        final List<Appointment> conflicts =
                appointmentRepository.findConflicts(physicianId, start, end);
        if (!conflicts.isEmpty()) {
            throw new SlotAlreadyBookedException(
                    findAlternatives(start, duration, physicianId)
            );
        }

        final Appointment appt = Appointment.create(
                tenantId,
                physicianId,
                patientId,
                start,
                duration
        );
        try {
            final Appointment saved = appointmentRepository.save(appt);
            eventPublisher.publishBooked(saved);
            return saved;
        } catch (ObjectOptimisticLockingFailureException
            | DataIntegrityViolationException ex) {
            throw new SlotAlreadyBookedException(
                    findAlternatives(start, duration, physicianId)
            );
        }
    }

    private List<LocalDateTime> findAlternatives(final LocalDateTime start,
                                                 final Duration duration,
                                                 final UUID physicianId) {
        final List<LocalDateTime> alts = new ArrayList<>();
        final long step = Math.max(MIN_STEP_MINUTES, duration.toMinutes());
        LocalDateTime candidate = start.plusMinutes(step);
        while (alts.size() < ALTERNATIVE_COUNT) {
            final LocalDateTime candEnd = candidate.plus(duration);
            final List<Appointment> c2 = appointmentRepository.findConflicts(
                    physicianId,
                    candidate,
                    candEnd
            );
            if (c2.isEmpty()) {
                alts.add(candidate);
            }
            candidate = candidate.plusMinutes(step);
        }
        return alts;
    }
}
