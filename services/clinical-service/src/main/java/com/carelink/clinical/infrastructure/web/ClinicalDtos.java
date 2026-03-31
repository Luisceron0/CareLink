package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.HandleGdprRequestUseCase;
import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs para API de clinical-service.
 */
public final class ClinicalDtos {

    private ClinicalDtos() {
    }

    /**
     * Request para crear paciente.
     *
     * @param fullName nombre completo
     * @param documentType tipo documental
     * @param documentValue valor documental
     * @param bloodType tipo sanguineo
     * @param phone telefono
     * @param email correo
     * @param emergencyContact contacto de emergencia
     */
    public record CreatePatientRequest(String fullName,
                                       String documentType,
                                       String documentValue,
                                       BloodType bloodType,
                                       String phone,
                                       String email,
                                       String emergencyContact) {
    }

    /**
     * Response de paciente.
     *
     * @param id identificador
     * @param tenantId tenant propietario
     * @param fullName nombre completo
     * @param documentType tipo documental
     * @param documentValue valor documental
     * @param bloodType tipo sanguineo
     * @param phone telefono
     * @param email correo
     * @param emergencyContact contacto de emergencia
     * @param createdAt fecha de creacion
     */
    public record PatientResponse(UUID id,
                                  UUID tenantId,
                                  String fullName,
                                  String documentType,
                                  String documentValue,
                                  String bloodType,
                                  String phone,
                                  String email,
                                  String emergencyContact,
                                  Instant createdAt) {
    }

    /**
     * Request para crear/actualizar encounter.
     *
     * @param chiefComplaint motivo principal
     * @param physicalExam examen fisico
     * @param treatmentPlan plan terapeutico
     * @param followUpInstructions instrucciones de seguimiento
     */
    public record EncounterWriteRequest(String chiefComplaint,
                                        String physicalExam,
                                        String treatmentPlan,
                                        String followUpInstructions) {
    }

    /**
     * Response de encounter.
     *
     * @param id identificador
     * @param tenantId tenant propietario
     * @param patientId paciente asociado
     * @param physicianId medico asociado
     * @param chiefComplaint motivo principal
     * @param physicalExam examen fisico
     * @param treatmentPlan plan terapeutico
     * @param followUpInstructions instrucciones de seguimiento
     * @param signedAt fecha de firma
     * @param createdAt fecha de creacion
     */
    public record EncounterResponse(UUID id,
                                    UUID tenantId,
                                    UUID patientId,
                                    UUID physicianId,
                                    String chiefComplaint,
                                    String physicalExam,
                                    String treatmentPlan,
                                    String followUpInstructions,
                                    Instant signedAt,
                                    Instant createdAt) {
    }

    /**
     * Request de GDPR/habeas data para paciente.
     *
     * @param requestType tipo de solicitud
     * @param jurisdiction jurisdiccion del paciente
     * @param confirmed confirmacion explicita de irreversibilidad
     */
    public record GdprRequest(String requestType,
                              String jurisdiction,
                              boolean confirmed) {
    }

    /**
     * Response de solicitud GDPR procesada.
     *
     * @param result resultado de procesamiento
     * @param businessMessage mensaje de negocio
     * @param baseLegalApplied base legal aplicada
     * @param pseudonymToken token seudonimo si aplica
     * @param processedAt fecha de procesamiento
     */
    public record GdprResponse(String result,
                               String businessMessage,
                               String baseLegalApplied,
                               String pseudonymToken,
                               Instant processedAt) {
    }

    /**
     * Error de API sanitizado.
     *
     * @param code codigo de error
     * @param message mensaje generico
     * @param requestId identificador de trazabilidad
     */
    public record ApiError(String code,
                           String message,
                           String requestId) {
    }

    /**
     * Mapea paciente de dominio a response.
     *
     * @param patient entidad paciente
     * @return response serializable
     */
    public static PatientResponse toResponse(final Patient patient) {
        return new PatientResponse(
                patient.id(),
                patient.tenantId(),
                patient.fullName(),
                patient.documentId().type(),
                patient.documentId().value(),
                patient.bloodType().name(),
                patient.phone(),
                patient.email(),
                patient.emergencyContact(),
                patient.createdAt()
        );
    }

    /**
     * Construye paciente de dominio.
     *
     * @param id id paciente
     * @param tenantId tenant
     * @param request payload
     * @return paciente de dominio
     */
    public static Patient toPatient(final UUID id,
                                    final UUID tenantId,
                                    final CreatePatientRequest request) {
        return new Patient(
                id,
                tenantId,
                request.fullName(),
                new DocumentId(request.documentType(), request.documentValue()),
                request.bloodType(),
                request.phone(),
                request.email(),
                request.emergencyContact(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    /**
     * Mapea encounter de dominio a response.
     *
     * @param encounter encounter dominio
     * @return response serializable
     */
    public static EncounterResponse toResponse(final Encounter encounter) {
        return new EncounterResponse(
                encounter.id(),
                encounter.tenantId(),
                encounter.patientId(),
                encounter.physicianId(),
                encounter.chiefComplaint(),
                encounter.physicalExam(),
                encounter.treatmentPlan(),
                encounter.followUpInstructions(),
                encounter.signedAt(),
                encounter.createdAt()
        );
    }

    /**
     * Mapea decision GDPR a response serializable.
     *
     * @param decision resultado del caso de uso
     * @return response de salida
     */
    public static GdprResponse toResponse(
            final HandleGdprRequestUseCase.GdprRequestDecision decision) {
        return new GdprResponse(
                decision.result(),
                decision.businessMessage(),
                decision.baseLegalApplied(),
                decision.pseudonymToken(),
                decision.processedAt()
        );
    }
}
