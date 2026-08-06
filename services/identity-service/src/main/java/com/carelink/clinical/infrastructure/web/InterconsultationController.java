package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.CloseInterconsultationUseCase;
import com.carelink.clinical.application.usecase.GetInterconsultationUseCase;
import com.carelink.clinical.application.usecase.IssuePrescriptionUseCase;
import com.carelink.clinical.application.usecase.RequestInterconsultationUseCase;
import com.carelink.clinical.application.usecase.RespondInterconsultationUseCase;
import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-08, FR-CLN-09, FR-CLN-10.
 *
 * <p><b>Cómo se evalúa el acceso del especialista (FR-CLN-10, AC-13).</b> Un
 * {@code SPECIALIST} no accede por su servicio ni por un permiso guardado en ningún
 * lado: en CADA request se pregunta a la base si tiene una interconsulta abierta para
 * ese paciente ({@code specialistHasOpenAccess}). No hay caché, no hay una fila de
 * "permiso concedido", y no existe un momento donde ese resultado se persista para
 * reusarlo. Cerrar la interconsulta es un {@code UPDATE status} y con eso el siguiente
 * request cae — no hay un segundo paso de revocación que alguien pueda olvidar.
 */
@RestController
@RequestMapping("/api/v1/interconsultations")
public class InterconsultationController {

    private static final String PHYSICIAN_ROLE = "PHYSICIAN";
    private static final String SPECIALIST_ROLE = "SPECIALIST";

    private final RequestInterconsultationUseCase requestUseCase;
    private final RespondInterconsultationUseCase respondUseCase;
    private final CloseInterconsultationUseCase closeUseCase;
    private final GetInterconsultationUseCase getUseCase;
    private final IssuePrescriptionUseCase issuePrescriptionUseCase;
    private final InterconsultationRepository interconsultationRepository;
    private final ClinicalRequestScope requestScope;

    public InterconsultationController(RequestInterconsultationUseCase requestUseCase,
                                        RespondInterconsultationUseCase respondUseCase,
                                        CloseInterconsultationUseCase closeUseCase,
                                        GetInterconsultationUseCase getUseCase,
                                        IssuePrescriptionUseCase issuePrescriptionUseCase,
                                        InterconsultationRepository interconsultationRepository,
                                        ClinicalRequestScope requestScope) {
        this.requestUseCase = requestUseCase;
        this.respondUseCase = respondUseCase;
        this.closeUseCase = closeUseCase;
        this.getUseCase = getUseCase;
        this.issuePrescriptionUseCase = issuePrescriptionUseCase;
        this.interconsultationRepository = interconsultationRepository;
        this.requestScope = requestScope;
    }

    @PostMapping
    public ResponseEntity<?> request(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestBody RequestInterconsultationRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Interconsultation ic = requestUseCase.execute(
                    tenantSlug.get(), req.getPatientId(), req.getEncounterId(), principal.userId(),
                    req.getSpecialistUserId(), req.getQuestion(), principal.serviceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ic));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * FR-CLN-10 en su forma más directa: el especialista lee la interconsulta —y con
     * ella los datos del paciente que necesita— solo mientras esté abierta. La
     * comprobación es la misma consulta que decide todo lo demás de este controller.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (principal == null || tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Se lee con alcance irrestricto y se decide después: un SPECIALIST puede ser de
        // otro servicio que el que solicitó la interconsulta —es lo habitual, de hecho—
        // así que filtrar por servicio acá le negaría el acceso que la interconsulta le
        // acaba de conceder. El control de acceso lo hace la comprobación de abajo, que
        // es más estrecha: tiene que ser SU interconsulta y estar abierta.
        Optional<Interconsultation> found = getUseCase.execute(tenantSlug.get(), id, ServiceScope.allServices());
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Interconsultation ic = found.get();

        if (SPECIALIST_ROLE.equals(principal.role())) {
            // AC-13: reevaluado acá, en este request, contra el estado actual.
            boolean allowed = ic.specialistUserId().equals(principal.userId())
                    && interconsultationRepository.specialistHasOpenAccess(
                            tenantSlug.get(), principal.userId(), ic.patientId());
            if (!allowed) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(toResponse(ic));
        }

        // Para el resto de los roles vale el aislamiento por servicio de siempre (AC-06b).
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return getUseCase.execute(tenantSlug.get(), id, scope.get())
                .map(x -> ResponseEntity.ok(toResponse(x)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/{id}/response")
    public ResponseEntity<?> respond(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @PathVariable UUID id,
                                      @RequestBody RespondInterconsultationRequest req) {
        if (!SPECIALIST_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // El caso de uso solo escribe si la interconsulta está ABIERTA y dirigida a este
        // especialista — responder una cerrada sería escribir en una historia clínica a
        // la que ya no se tiene acceso.
        boolean saved = respondUseCase.execute(tenantSlug.get(), id, principal.userId(), req.getOpinion());
        return saved ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * FR-CLN-09 — prescripción emitida por el especialista a través de su respuesta. Se
     * vincula al encounter RAÍZ de la interconsulta, no a uno nuevo: el
     * {@code clinicalEncounterId} sale de la interconsulta, no del body, así que el
     * cliente no puede colgar la prescripción de otro encounter.
     */
    @PostMapping("/{id}/prescriptions")
    public ResponseEntity<?> prescribe(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @PathVariable UUID id,
                                        @RequestBody IssuePrescriptionRequest req) {
        if (!SPECIALIST_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Interconsultation> found = getUseCase.execute(tenantSlug.get(), id, ServiceScope.allServices());
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Interconsultation ic = found.get();

        // AC-13 otra vez, sobre una escritura: sin interconsulta abierta no se prescribe.
        boolean allowed = ic.specialistUserId().equals(principal.userId())
                && interconsultationRepository.specialistHasOpenAccess(
                        tenantSlug.get(), principal.userId(), ic.patientId());
        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Prescription p = issuePrescriptionUseCase.execute(
                tenantSlug.get(), ic.patientId(), ic.clinicalEncounterId(), ic.id(), principal.userId(),
                req.getMedication(), req.getDosage(), req.getInstructions(), ic.serviceId());

        Map<String, Object> body = new HashMap<>();
        body.put("id", p.id().toString());
        body.put("clinicalEncounterId", p.clinicalEncounterId().toString());
        body.put("interconsultationId", p.interconsultationId().toString());
        body.put("medication", p.medication());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @PathVariable UUID id) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean closed = closeUseCase.execute(tenantSlug.get(), id, scope.get());
        return closed ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private Map<String, Object> toResponse(Interconsultation ic) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", ic.id().toString());
        body.put("patientId", ic.patientId().toString());
        body.put("clinicalEncounterId", ic.clinicalEncounterId().toString());
        body.put("specialistUserId", ic.specialistUserId().toString());
        body.put("question", ic.question());
        body.put("status", ic.status().name());
        body.put("closedAt", ic.closedAt() == null ? null : ic.closedAt().toString());
        if (ic.response() != null) {
            body.put("opinion", ic.response().opinion());
            body.put("respondedAt", ic.response().respondedAt().toString());
        }
        return body;
    }
}
