package com.carelink.scheduling.infrastructure.web;

import com.carelink.scheduling.application.BookAppointmentUseCase;
import com.carelink.scheduling.application.CancelAppointmentUseCase;
import com.carelink.scheduling.application.UpdateAppointmentStatusUseCase;
import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.port.AppointmentRepository;
import com.carelink.scheduling.domain.value.AppointmentStatus;
import com.carelink.scheduling.infrastructure.web.AppointmentDtos
    .AppointmentResponse;
import com.carelink.scheduling.infrastructure.web.AppointmentDtos
    .CreateAppointmentRequest;
import com.carelink.scheduling.infrastructure.web.AppointmentDtos
    .UpdateStatusRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API REST para gestión de citas.
 */
@RestController
@RequestMapping("/api/v1/appointments")
public final class AppointmentController {

    /** Caso de uso para reservar citas. */
    private final BookAppointmentUseCase bookAppointmentUseCase;

    /** Caso de uso para actualización de estado. */
    private final UpdateAppointmentStatusUseCase updateAppointmentStatusUseCase;

    /** Caso de uso para cancelación de citas. */
    private final CancelAppointmentUseCase cancelAppointmentUseCase;

    /** Repositorio para consultas con filtros. */
    private final AppointmentRepository appointmentRepository;

    /**
     * Constructor.
      *
      * @param bookAppointmentUseCaseArg caso de uso de reserva
      * @param updateAppointmentStatusUseCaseArg caso de uso de cambio de estado
      * @param cancelAppointmentUseCaseArg caso de uso de cancelación
      * @param appointmentRepositoryArg repositorio de consultas
     */
    public AppointmentController(
            final BookAppointmentUseCase bookAppointmentUseCaseArg,
            final UpdateAppointmentStatusUseCase
                updateAppointmentStatusUseCaseArg,
            final CancelAppointmentUseCase cancelAppointmentUseCaseArg,
            final AppointmentRepository appointmentRepositoryArg) {
        this.bookAppointmentUseCase = bookAppointmentUseCaseArg;
        this.updateAppointmentStatusUseCase = updateAppointmentStatusUseCaseArg;
        this.cancelAppointmentUseCase = cancelAppointmentUseCaseArg;
        this.appointmentRepository = appointmentRepositoryArg;
    }

    /**
     * Reserva una cita y publica evento `AppointmentBooked`.
      *
      * @param tenantId tenant de la solicitud
      * @param role rol del usuario autenticado
      * @param request payload de creación de cita
      * @return response con la cita creada
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Role") final String role,
            @RequestBody final CreateAppointmentRequest request) {
        requireAnyRole(role, "RECEPTIONIST", "TENANT_ADMIN");
        final Appointment appointment = bookAppointmentUseCase.bookAppointment(
                tenantId,
                request.physicianId(),
                request.patientId(),
                request.slotStart(),
                AppointmentDtos.toDuration(request.durationMinutes())
        );
        return ResponseEntity.ok(AppointmentDtos.toResponse(appointment));
    }

    /**
     * Lista citas por tenant con filtros opcionales.
      *
      * @param tenantId tenant de la solicitud
      * @param physicianId filtro opcional por médico
      * @param date filtro opcional por fecha
      * @param status filtro opcional por estado
      * @return response con lista de citas
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestParam(required = false) final UUID physicianId,
            @RequestParam(required = false) final LocalDate date,
            @RequestParam(required = false) final AppointmentStatus status) {
        final List<AppointmentResponse> items = appointmentRepository
                .listByTenant(tenantId, physicianId, date, status)
                .stream()
                .map(AppointmentDtos::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    /**
     * Actualiza estado de una cita.
      *
      * @param tenantId tenant de la solicitud
      * @param role rol del usuario autenticado
      * @param id id de la cita
      * @param request payload con estado objetivo
      * @return response con cita actualizada
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable final UUID id,
            @RequestBody final UpdateStatusRequest request) {
        validateStatusChangeRole(role, request.status());
        final Appointment updated = updateAppointmentStatusUseCase
                .updateStatus(tenantId, id, request.status());
        return ResponseEntity.ok(AppointmentDtos.toResponse(updated));
    }

    /**
     * Cancela una cita (soft delete por estado).
      *
      * @param tenantId tenant de la solicitud
      * @param role rol del usuario autenticado
      * @param id id de la cita
      * @return response con cita cancelada
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<AppointmentResponse> cancel(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable final UUID id) {
        requireAnyRole(role, "RECEPTIONIST", "TENANT_ADMIN");
        final Appointment cancelled = cancelAppointmentUseCase
            .cancel(tenantId, id);
        return ResponseEntity.ok(AppointmentDtos.toResponse(cancelled));
    }

    private void requireAnyRole(final String currentRole,
                                final String roleA,
                                final String roleB) {
        if (!(roleA.equalsIgnoreCase(currentRole)
                || roleB.equalsIgnoreCase(currentRole))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Forbidden role"
            );
        }
    }

    private void validateStatusChangeRole(final String role,
                                          final AppointmentStatus status) {
        if (status == AppointmentStatus.IN_PROGRESS
            || status == AppointmentStatus.COMPLETED) {
            if (!"PHYSICIAN".equalsIgnoreCase(role)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only PHYSICIAN can set IN_PROGRESS/COMPLETED"
                );
            }
            return;
        }

        if (status == AppointmentStatus.CANCELLED) {
            requireAnyRole(role, "RECEPTIONIST", "TENANT_ADMIN");
        }
    }
}
