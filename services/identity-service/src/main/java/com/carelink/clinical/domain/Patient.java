package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;
import com.carelink.clinical.domain.value.Sex;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FR-CLN-01. El dominio maneja texto plano — {@code fullName} acá es "Juan Pérez", no
 * un valor cifrado. Cifrar es responsabilidad del adaptador de persistencia
 * ({@code JdbcPatientRepository}), no de este record: el dominio no debería saber ni
 * importarle CÓMO se guarda un dato, solo qué es.
 *
 * <p><b>Alcance de esta sub-fase, campos diferidos a propósito, no olvidados:</b>
 * contacto, contacto de emergencia, medicación activa y afiliación EPS/SISBEN
 * (FR-CLN-01 los pide) quedan fuera de este primer corte. El objetivo acá es
 * demostrar el patrón completo (value objects, cifrado por campo, aislamiento por
 * tenant) con un subconjunto real, no construir el formulario de admisión completo de
 * una vez — agregar esos campos después es extender esta clase y su adaptador, no
 * rediseñarlos.
 */
public record Patient(
        UUID id,
        String fullName,
        DocumentId documentId,
        LocalDate dateOfBirth,
        Sex sex,
        BloodType bloodType,
        List<String> allergies,
        /** AC-06b — servicio al que pertenece el paciente. Nullable: lo creó un rol exento (§4). */
        String serviceId,
        OffsetDateTime createdAt
) {
    public Patient {
        if (id == null) throw new IllegalArgumentException("Patient requiere id");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Patient requiere fullName");
        if (documentId == null) throw new IllegalArgumentException("Patient requiere documentId");
        if (sex == null) throw new IllegalArgumentException("Patient requiere sex");
        if (bloodType == null) throw new IllegalArgumentException("Patient requiere bloodType");
        allergies = allergies == null ? List.of() : List.copyOf(allergies);
        if (createdAt == null) throw new IllegalArgumentException("Patient requiere createdAt");
    }
}
