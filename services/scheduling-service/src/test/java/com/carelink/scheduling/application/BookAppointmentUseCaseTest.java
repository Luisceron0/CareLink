package com.carelink.scheduling.application;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.exception.SlotAlreadyBookedException;
import com.carelink.scheduling.domain.port.AppointmentEventPublisher;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import com.carelink.scheduling.domain.value.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BookAppointmentUseCaseTest {

    @Test
    void booksWhenNoConflicts() {
        final UUID tenant = UUID.randomUUID();
        final UUID physician = UUID.randomUUID();
        final UUID patient = UUID.randomUUID();
        final LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        final Duration duration = Duration.ofMinutes(30);

        final List<Appointment> saved = new ArrayList<>();

        final AppointmentRepository repo = new AppointmentRepository() {
            @Override
            public List<Appointment> findConflicts(final UUID physicianId, final LocalDateTime s, final LocalDateTime e) {
                return List.of();
            }

            @Override
            public Appointment save(final Appointment appointment) {
                saved.add(appointment);
                return appointment;
            }

            @Override
            public Optional<Appointment> findById(final UUID id) { return Optional.empty(); }

            @Override
            public Optional<Appointment> findByTenantAndId(final UUID tenantId,
                                                           final UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Appointment> listByTenant(final UUID tenantId,
                                                  final UUID physicianId,
                                                  final LocalDate date,
                                                  final AppointmentStatus status) {
                return List.of();
            }
        };

        final BookAppointmentUseCase uc = new BookAppointmentUseCase(repo, noopPublisher());
        final Appointment a = uc.bookAppointment(tenant, physician, patient, start, duration);

        assertNotNull(a.id());
        assertEquals(tenant, a.tenantId());
        assertEquals(AppointmentStatus.PENDING, a.status());
        assertEquals(1, saved.size());
    }

    @Test
    void throwsWithThreeAlternativesOnConflict() {
        final UUID tenant = UUID.randomUUID();
        final UUID physician = UUID.randomUUID();
        final UUID patient = UUID.randomUUID();
        final LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        final Duration duration = Duration.ofMinutes(30);

        final Appointment existing = Appointment.create(tenant, physician, UUID.randomUUID(), start, duration);

        final AppointmentRepository repo = new AppointmentRepository() {
            @Override
            public List<Appointment> findConflicts(final UUID physicianId, final LocalDateTime s, final LocalDateTime e) {
                if (s.equals(start)) return List.of(existing);
                return List.of();
            }

            @Override
            public Appointment save(final Appointment appointment) { return appointment; }

            @Override
            public Optional<Appointment> findById(final UUID id) { return Optional.empty(); }

            @Override
            public Optional<Appointment> findByTenantAndId(final UUID tenantId,
                                                           final UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Appointment> listByTenant(final UUID tenantId,
                                                  final UUID physicianId,
                                                  final LocalDate date,
                                                  final AppointmentStatus status) {
                return List.of();
            }
        };

        final BookAppointmentUseCase uc = new BookAppointmentUseCase(repo, noopPublisher());

        final SlotAlreadyBookedException ex = assertThrows(SlotAlreadyBookedException.class, () ->
                uc.bookAppointment(tenant, physician, patient, start, duration)
        );

        final String msg = ex.getMessage();
        assertTrue(msg.contains("Alternatives:"));
        final String[] parts = msg.split("Alternatives:\\s*")[1].split(",\\s*");
        assertEquals(3, parts.length);
    }

    private AppointmentEventPublisher noopPublisher() {
        return new AppointmentEventPublisher() {
            @Override
            public void publishBooked(final Appointment appointment) {
            }

            @Override
            public void publishCancelled(final Appointment appointment) {
            }

            @Override
            public void publishStatusChanged(final Appointment appointment) {
            }
        };
    }
}
