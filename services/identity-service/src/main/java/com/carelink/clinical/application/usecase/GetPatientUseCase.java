package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code @Component} — mismo motivo que {@link RegisterPatientUseCase}: sin ser un bean
 * de Spring, {@code @Auditable} no se intercepta.
 *
 * <p>{@code tenantSlug} siempre viene del tenant del caller autenticado, nunca de un
 * parámetro que el cliente controle — es lo que hace que un intento de lectura
 * cross-tenant no encuentre nada: la consulta ni siquiera mira el schema ajeno
 * (AC-06). No hay una comparación "¿el tenant pedido es el tuyo?" que pueda tener un
 * bug — no existe la posibilidad de pedir el tenant de otro en primer lugar.
 *
 * <p>Este es el primer caso de uso real que audita una lectura de PHI — AC-07 ("1
 * lectura de PHI → 1 fila de audit_log") queda evidenciable acá, después de haber
 * quedado como placeholder desde la Sub-fase 1 a falta de un caso de uso real.
 */
@Component
public class GetPatientUseCase {
    private final PatientRepository patientRepository;

    public GetPatientUseCase(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Auditable(action = "PATIENT_READ", tenantSlugExpression = "#tenantSlug.value()", patientIdExpression = "#patientId")
    public Optional<Patient> execute(TenantSlug tenantSlug, UUID patientId, ServiceScope scope) {
        return patientRepository.findById(tenantSlug, patientId, scope);
    }
}
