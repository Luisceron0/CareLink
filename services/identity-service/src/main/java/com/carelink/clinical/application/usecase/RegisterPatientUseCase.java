package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code @Component}, no instanciada a mano en el controller como el resto de los casos
 * de uso de este repo (ver {@code AuthController}) — deliberado, no una inconsistencia.
 * {@code @Auditable} lo intercepta {@code AuditAspect} vía proxy AOP de Spring, y Spring
 * solo aplica esos proxies a beans que administra. Un caso de uso instanciado con
 * {@code new} nunca pasa por el proxy — el {@code @Auditable} sería una anotación
 * decorativa, no un intercept real. Cualquier caso de uso futuro que necesite auditoría
 * necesita este mismo tratamiento.
 */
@Component
public class RegisterPatientUseCase {
    private final PatientRepository patientRepository;

    public RegisterPatientUseCase(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /**
     * Sin {@code patientIdExpression}: el id se genera DENTRO de este método, no es un
     * argumento — {@code AuditAspect} evalúa SpEL contra los parámetros de entrada, no
     * contra el valor de retorno, así que no hay forma de referenciarlo desde la
     * anotación. La fila de auditoría de un alta de paciente queda sin patient_id;
     * ampliar el aspecto para leer también el retorno es una mejora futura, no algo que
     * esta tarea necesite.
     */
    @Auditable(action = "PATIENT_CREATE", tenantSlugExpression = "#tenantSlug.value()")
    public Patient execute(TenantSlug tenantSlug, String fullName, DocumentType documentType, String documentNumber,
                            LocalDate dateOfBirth, Sex sex, BloodType bloodType, List<String> allergies) {
        Patient patient = new Patient(
                UUID.randomUUID(),
                fullName,
                new DocumentId(documentType, documentNumber),
                dateOfBirth,
                sex,
                bloodType,
                allergies,
                OffsetDateTime.now());
        patientRepository.save(tenantSlug, patient);
        return patient;
    }
}
