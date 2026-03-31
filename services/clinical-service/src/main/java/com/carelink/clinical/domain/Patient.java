package com.carelink.clinical.domain;

import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Paciente del dominio clínico.
 *
 * @param id identificador
 * @param tenantId tenant propietario
 * @param fullName nombre completo
 * @param documentId documento de identidad
 * @param bloodType tipo sanguíneo
 * @param phone teléfono
 * @param email correo electrónico
 * @param emergencyContact contacto de emergencia
 * @param allergies alergias reportadas
 * @param activeMedications medicamentos activos
 * @param createdAt fecha de creación
 */
public record Patient(UUID id,
                      UUID tenantId,
                      String fullName,
                      DocumentId documentId,
                      BloodType bloodType,
                      String phone,
                      String email,
                      String emergencyContact,
                      List<Allergy> allergies,
                      List<ActiveMedication> activeMedications,
                      Instant createdAt) {

    /**
     * Constructor canónico.
     */
    public Patient {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(fullName);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(bloodType);
        Objects.requireNonNull(phone);
        Objects.requireNonNull(email);
        Objects.requireNonNull(emergencyContact);
        Objects.requireNonNull(allergies);
        Objects.requireNonNull(activeMedications);
        Objects.requireNonNull(createdAt);
    }

    /**
     * Cifra campos PHI antes de persistir.
     *
     * @param encryptionPort puerto de cifrado
     * @return paciente con PHI cifrado
     */
    public Patient encryptPhi(final EncryptionPort encryptionPort) {
        Objects.requireNonNull(encryptionPort);
        return new Patient(
                id,
                tenantId,
                encryptionPort.encrypt(tenantId, fullName),
                documentId,
                bloodType,
                encryptionPort.encrypt(tenantId, phone),
                encryptionPort.encrypt(tenantId, email),
                encryptionPort.encrypt(tenantId, emergencyContact),
                allergies,
                activeMedications,
                createdAt
        );
    }

    /**
     * Descifra campos PHI para uso de negocio/controlador.
     *
     * @param encryptionPort puerto de cifrado
     * @return paciente con PHI descifrado
     */
    public Patient decryptPhi(final EncryptionPort encryptionPort) {
        Objects.requireNonNull(encryptionPort);
        return new Patient(
                id,
                tenantId,
                encryptionPort.decrypt(tenantId, fullName),
                documentId,
                bloodType,
                encryptionPort.decrypt(tenantId, phone),
                encryptionPort.decrypt(tenantId, email),
                encryptionPort.decrypt(tenantId, emergencyContact),
                allergies,
                activeMedications,
                createdAt
        );
    }
}
