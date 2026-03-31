package com.carelink.scheduling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_blocks")
public class AvailabilityBlockEntity {

    /** Identificador del bloque. */
    @Id
    private UUID id;

    /** Physician propietario del bloque. */
    @Column(name = "physician_id", nullable = false)
    private UUID physicianId;

    /** Día de la semana como entero. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    /** Hora de inicio. */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** Hora de fin. */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Constructor por defecto para JPA.
     */
    public AvailabilityBlockEntity() {
    }

    /**
     * Constructor de conveniencia.
     *
     * @param idArg          id del bloque
     * @param physicianIdArg id del physician
     * @param dayOfWeekArg   día de la semana
     * @param startTimeArg   hora de inicio
     * @param endTimeArg     hora de fin
     */
    public AvailabilityBlockEntity(final UUID idArg,
                                    final UUID physicianIdArg,
                                    final DayOfWeek dayOfWeekArg,
                                    final LocalTime startTimeArg,
                                    final LocalTime endTimeArg) {
        this.id = idArg;
        this.physicianId = physicianIdArg;
        this.dayOfWeek = dayOfWeekArg.getValue();
        this.startTime = startTimeArg;
        this.endTime = endTimeArg;
    }

    /** @return id del bloque */
    public UUID getId() {
        return id;
    }

    /** @param idArg nuevo id */
    public void setId(final UUID idArg) {
        this.id = idArg;
    }

    /** @return physician id */
    public UUID getPhysicianId() {
        return physicianId;
    }

    /** @param physicianIdArg nuevo physician id */
    public void setPhysicianId(final UUID physicianIdArg) {
        this.physicianId = physicianIdArg;
    }

    /** @return día de la semana en int */
    public int getDayOfWeek() {
        return dayOfWeek;
    }

    /** @param dayOfWeekArg nuevo día de la semana */
    public void setDayOfWeek(final int dayOfWeekArg) {
        this.dayOfWeek = dayOfWeekArg;
    }

    /** @return hora de inicio */
    public LocalTime getStartTime() {
        return startTime;
    }

    /** @param startTimeArg nueva hora de inicio */
    public void setStartTime(final LocalTime startTimeArg) {
        this.startTime = startTimeArg;
    }

    /** @return hora de fin */
    public LocalTime getEndTime() {
        return endTime;
    }

    /** @param endTimeArg nueva hora de fin */
    public void setEndTime(final LocalTime endTimeArg) {
        this.endTime = endTimeArg;
    }
}
