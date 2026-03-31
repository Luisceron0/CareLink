package com.carelink.scheduling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "physicians")
public class PhysicianEntity {

    /** Identificador del médico. */
    @Id
    private UUID id;

    /** Tenant propietario del médico. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Nombre completo. */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Especialidad. */
    @Column(name = "specialty")
    private String specialty;

    /**
     * Constructor por defecto para JPA.
     */
    public PhysicianEntity() {
    }

    /**
     * Constructor de conveniencia.
     *
     * @param idArg       identificador
     * @param tenantIdArg tenant al que pertenece
     * @param fullNameArg nombre completo
     * @param specialtyArg especialidad
     */
    public PhysicianEntity(final UUID idArg,
                           final UUID tenantIdArg,
                           final String fullNameArg,
                           final String specialtyArg) {
        this.id = idArg;
        this.tenantId = tenantIdArg;
        this.fullName = fullNameArg;
        this.specialty = specialtyArg;
    }

    /**
     * @return id del entity
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param idArg nuevo id
     */
    public void setId(final UUID idArg) {
        this.id = idArg;
    }

    /**
     * @return tenant id
     */
    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantIdArg nuevo tenant id
     */
    public void setTenantId(final UUID tenantIdArg) {
        this.tenantId = tenantIdArg;
    }

    /**
     * @return nombre completo
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * @param fullNameArg nuevo nombre completo
     */
    public void setFullName(final String fullNameArg) {
        this.fullName = fullNameArg;
    }

    /**
     * @return especialidad
     */
    public String getSpecialty() {
        return specialty;
    }

    /**
     * @param specialtyArg nueva especialidad
     */
    public void setSpecialty(final String specialtyArg) {
        this.specialty = specialtyArg;
    }
}
