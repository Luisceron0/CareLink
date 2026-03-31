package com.carelink.scheduling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad JPA para citas.
 */
@Entity
@Table(
    name = "appointments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_appointments_physician_slot",
            columnNames = {"physician_id", "slot_start"}
        )
    }
)
public final class AppointmentEntity {

    /** Identificador de la cita. */
    @Id
    private UUID id;

    /** Tenant propietario de la cita. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Identificador del médico. */
    @Column(name = "physician_id", nullable = false)
    private UUID physicianId;

    /** Identificador del paciente. */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Inicio del slot en UTC. */
    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    /** Duración del slot en minutos. */
    @Column(name = "duration_minutes", nullable = false)
    private long durationMinutes;

    /** Estado actual de la cita. */
    @Column(name = "status", nullable = false)
    private String status;

    /** Campo de control para optimistic locking. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Constructor por defecto para JPA. */
    public AppointmentEntity() {
    }

    /**
     * Constructor de conveniencia.
     *
     * @param idArg              identificador
     * @param tenantIdArg        tenant propietario
     * @param physicianIdArg     médico
     * @param patientIdArg       paciente
     * @param slotStartArg       inicio del slot
     * @param durationMinutesArg duración del slot en minutos
     * @param statusArg          estado de la cita
     */
    public AppointmentEntity(final UUID idArg,
                             final UUID tenantIdArg,
                             final UUID physicianIdArg,
                             final UUID patientIdArg,
                             final LocalDateTime slotStartArg,
                             final long durationMinutesArg,
                             final String statusArg) {
        this.id = idArg;
        this.tenantId = tenantIdArg;
        this.physicianId = physicianIdArg;
        this.patientId = patientIdArg;
        this.slotStart = slotStartArg;
        this.durationMinutes = durationMinutesArg;
        this.status = statusArg;
    }

    /**
     * @return identificador de la cita
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param idArg nuevo identificador de la cita
     */
    public void setId(final UUID idArg) {
        this.id = idArg;
    }

    /**
     * @return tenant propietario de la cita
     */
    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantIdArg nuevo tenant propietario
     */
    public void setTenantId(final UUID tenantIdArg) {
        this.tenantId = tenantIdArg;
    }

    /**
     * @return identificador del médico
     */
    public UUID getPhysicianId() {
        return physicianId;
    }

    /**
     * @param physicianIdArg nuevo identificador del médico
     */
    public void setPhysicianId(final UUID physicianIdArg) {
        this.physicianId = physicianIdArg;
    }

    /**
     * @return identificador del paciente
     */
    public UUID getPatientId() {
        return patientId;
    }

    /**
     * @param patientIdArg nuevo identificador del paciente
     */
    public void setPatientId(final UUID patientIdArg) {
        this.patientId = patientIdArg;
    }

    /**
     * @return inicio del slot de cita
     */
    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    /**
     * @param slotStartArg nuevo inicio del slot
     */
    public void setSlotStart(final LocalDateTime slotStartArg) {
        this.slotStart = slotStartArg;
    }

    /**
     * @return duración de la cita en minutos
     */
    public long getDurationMinutes() {
        return durationMinutes;
    }

    /**
     * @param durationMinutesArg nueva duración de la cita en minutos
     */
    public void setDurationMinutes(final long durationMinutesArg) {
        this.durationMinutes = durationMinutesArg;
    }

    /**
     * @return estado actual de la cita
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param statusArg nuevo estado de la cita
     */
    public void setStatus(final String statusArg) {
        this.status = statusArg;
    }

    /**
     * @return versión de optimistic locking
     */
    public long getVersion() {
        return version;
    }

    /**
     * @param versionArg nueva versión de optimistic locking
     */
    public void setVersion(final long versionArg) {
        this.version = versionArg;
    }
}
