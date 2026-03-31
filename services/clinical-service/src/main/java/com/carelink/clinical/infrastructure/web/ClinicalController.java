package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.CreateEncounterUseCase;
import com.carelink.clinical.application.EncounterContent;
import com.carelink.clinical.application.ExportPatientFhirUseCase;
import com.carelink.clinical.application.ExportPatientPdfUseCase;
import com.carelink.clinical.application.GetEncounterUseCase;
import com.carelink.clinical.application.GetPatientUseCase;
import com.carelink.clinical.application.HandleGdprRequestUseCase;
import com.carelink.clinical.application.RegisterPatientUseCase;
import com.carelink.clinical.application.SignEncounterUseCase;
import com.carelink.clinical.application.UpdateEncounterUseCase;
import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.event.PatientRegistered;
import com.carelink.clinical.domain.port.PatientRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API REST para pacientes y encuentros clinicos.
 */
@RestController
@RequestMapping("/api/v1")
public final class ClinicalController {

        /** Caso de uso de registro de pacientes. */
    @org.springframework.beans.factory.annotation.Autowired
    private RegisterPatientUseCase registerPatientUseCase;

        /** Caso de uso de consulta de pacientes. */
    @org.springframework.beans.factory.annotation.Autowired
    private GetPatientUseCase getPatientUseCase;

        /** Caso de uso de creacion de encuentros. */
    @org.springframework.beans.factory.annotation.Autowired
    private CreateEncounterUseCase createEncounterUseCase;

        /** Caso de uso de firma de encuentros. */
    @org.springframework.beans.factory.annotation.Autowired
    private SignEncounterUseCase signEncounterUseCase;

        /** Caso de uso de lectura de encuentros. */
    @org.springframework.beans.factory.annotation.Autowired
    private GetEncounterUseCase getEncounterUseCase;

        /** Caso de uso de actualizacion de encuentros. */
    @org.springframework.beans.factory.annotation.Autowired
    private UpdateEncounterUseCase updateEncounterUseCase;

        /** Caso de uso de exportacion FHIR. */
    @org.springframework.beans.factory.annotation.Autowired
    private ExportPatientFhirUseCase exportPatientFhirUseCase;

        /** Caso de uso de exportacion PDF. */
    @org.springframework.beans.factory.annotation.Autowired
    private ExportPatientPdfUseCase exportPatientPdfUseCase;

        /** Caso de uso de solicitudes GDPR/habeas data. */
    @org.springframework.beans.factory.annotation.Autowired
    private HandleGdprRequestUseCase handleGdprRequestUseCase;

        /** Repositorio de pacientes para validaciones de tenant. */
    @org.springframework.beans.factory.annotation.Autowired
    private PatientRepository patientRepository;

        /**
         * Crea un paciente en el tenant actual.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param request payload de paciente
         * @return paciente creado
         */
    @PostMapping("/patients")
    public ResponseEntity<ClinicalDtos.PatientResponse> createPatient(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @RequestBody final ClinicalDtos.CreatePatientRequest request) {
        requireAnyRole(role, "PHYSICIAN", "RECEPTIONIST", "TENANT_ADMIN");
        final Patient patient = ClinicalDtos.toPatient(
                UUID.randomUUID(),
                tenantId,
                request
        );
        final PatientRegistered event = registerPatientUseCase.register(
                actorUserId,
                patient
        );
        final Patient created = getPatientUseCase.getById(
                tenantId,
                actorUserId,
                event.patientId()
        );
        return ResponseEntity.ok(ClinicalDtos.toResponse(created));
    }

        /**
         * Obtiene un paciente por id en el tenant actual.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @return paciente encontrado
         */
    @GetMapping("/patients/{id}")
    public ResponseEntity<ClinicalDtos.PatientResponse> getPatient(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");

        if (isCrossTenantAccess(tenantId, patientId)) {
            throw new SecurityException("CROSS_TENANT_ACCESS_DENIED");
        }

        final Patient patient = getPatientUseCase.getById(
                tenantId,
                actorUserId,
                patientId
        );
        return ResponseEntity.ok(ClinicalDtos.toResponse(patient));
    }

        /**
         * Crea un encuentro clinico para un paciente.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @param request contenido clinico del encuentro
         * @return encuentro creado
         */
    @PostMapping("/patients/{id}/encounters")
    public ResponseEntity<ClinicalDtos.EncounterResponse> createEncounter(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId,
            @RequestBody final ClinicalDtos.EncounterWriteRequest request) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        final EncounterContent content = toEncounterContent(request);
        final Encounter encounter = createEncounterUseCase.create(
                tenantId,
                actorUserId,
                patientId,
                actorUserId,
                content
        );
        return ResponseEntity.ok(ClinicalDtos.toResponse(encounter));
    }

        /**
         * Consulta un encuentro clinico.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @param encounterId id del encuentro
         * @return encuentro solicitado
         */
    @GetMapping("/patients/{id}/encounters/{eid}")
    public ResponseEntity<ClinicalDtos.EncounterResponse> getEncounter(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId,
            @PathVariable("eid") final UUID encounterId) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        final Encounter encounter = getEncounterUseCase.get(
                tenantId,
                actorUserId,
                patientId,
                encounterId
        );
        return ResponseEntity.ok(ClinicalDtos.toResponse(encounter));
    }

        /**
         * Actualiza un encuentro no firmado.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @param encounterId id del encuentro
         * @param request contenido clinico actualizado
         * @return encuentro actualizado
         */
    @PutMapping("/patients/{id}/encounters/{eid}")
    public ResponseEntity<ClinicalDtos.EncounterResponse> updateEncounter(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId,
            @PathVariable("eid") final UUID encounterId,
            @RequestBody final ClinicalDtos.EncounterWriteRequest request) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        final EncounterContent content = toEncounterContent(request);
        final Encounter updated = updateEncounterUseCase.update(
                tenantId,
                actorUserId,
                patientId,
                encounterId,
                content
        );
        return ResponseEntity.ok(ClinicalDtos.toResponse(updated));
    }

        /**
         * Firma un encuentro clinico.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @param encounterId id del encuentro
         * @return respuesta vacia de exito
         */
    @PostMapping("/patients/{id}/encounters/{eid}/sign")
    public ResponseEntity<Void> signEncounter(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId,
            @PathVariable("eid") final UUID encounterId) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        signEncounterUseCase.sign(tenantId, encounterId, actorUserId);
        return ResponseEntity.ok().build();
    }

        /**
         * Exporta resumen clinico en PDF.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @return bytes del PDF
         */
    @GetMapping("/patients/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        final byte[] body = exportPatientPdfUseCase.export(
                tenantId,
                actorUserId,
                patientId
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=patient-" + patientId + ".pdf"
                )
                .body(body);
    }

        /**
         * Exporta informacion del paciente en FHIR JSON.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @return contenido FHIR serializado
         */
    @GetMapping("/patients/{id}/export/fhir")
    public ResponseEntity<String> exportFhir(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId) {
        requireAnyRole(role, "PHYSICIAN", "TENANT_ADMIN");
        final String body = exportPatientFhirUseCase.export(
                tenantId,
                actorUserId,
                patientId
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

        /**
         * Procesa solicitud GDPR/habeas data para un paciente.
         *
         * @param tenantId tenant de la solicitud
         * @param actorUserId usuario actor
         * @param role rol del usuario
         * @param patientId id del paciente
         * @param request payload GDPR con confirmacion explicita
         * @return decision aplicada con base legal
         */
        @PostMapping("/patients/{id}/gdpr-request")
        public ResponseEntity<ClinicalDtos.GdprResponse> handleGdprRequest(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @RequestHeader("X-User-Id") final UUID actorUserId,
            @RequestHeader("X-User-Role") final String role,
            @PathVariable("id") final UUID patientId,
            @RequestBody final ClinicalDtos.GdprRequest request) {
        requireAnyRole(role, "TENANT_ADMIN", "PHYSICIAN");

        if (isCrossTenantAccess(tenantId, patientId)) {
            throw new SecurityException("CROSS_TENANT_ACCESS_DENIED");
        }

        final HandleGdprRequestUseCase.GdprRequestDecision decision =
            handleGdprRequestUseCase.handle(
                tenantId,
                actorUserId,
                patientId,
                request.requestType(),
                request.jurisdiction(),
                request.confirmed()
            );
        return ResponseEntity.ok(ClinicalDtos.toResponse(decision));
        }

    private EncounterContent toEncounterContent(
            final ClinicalDtos.EncounterWriteRequest request) {
        return new EncounterContent(
                request.chiefComplaint(),
                request.physicalExam(),
                request.treatmentPlan(),
                request.followUpInstructions()
        );
    }

    private boolean isCrossTenantAccess(final UUID tenantId,
                                        final UUID patientId) {
        return patientRepository.existsById(patientId)
                && patientRepository.findByTenantAndId(tenantId, patientId)
                .isEmpty();
    }

    private void requireAnyRole(final String currentRole,
                                final String roleA,
                                final String roleB) {
        if (!(roleA.equalsIgnoreCase(currentRole)
                || roleB.equalsIgnoreCase(currentRole))) {
            throw new SecurityException("FORBIDDEN_ROLE");
        }
    }

    private void requireAnyRole(final String currentRole,
                                final String roleA,
                                final String roleB,
                                final String roleC) {
        if (!(roleA.equalsIgnoreCase(currentRole)
                || roleB.equalsIgnoreCase(currentRole)
                || roleC.equalsIgnoreCase(currentRole))) {
            throw new SecurityException("FORBIDDEN_ROLE");
        }
    }
}
