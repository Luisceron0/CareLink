package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.InterconsultationResponse;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public interface InterconsultationRepository {

    void save(TenantSlug tenantSlug, Interconsultation interconsultation);

    Optional<Interconsultation> findById(TenantSlug tenantSlug, UUID id, ServiceScope scope);

    /**
     * FR-CLN-10, AC-13 — la consulta que decide, EN CADA REQUEST, si este especialista
     * puede ver a este paciente ahora mismo.
     *
     * <p>No recibe un {@code ServiceScope}: el acceso del especialista no viene de su
     * servicio sino de tener una interconsulta abierta dirigida a él, que es un permiso
     * más estrecho y explícito. Un especialista sin interconsulta abierta no ve al
     * paciente aunque comparta servicio.
     */
    boolean specialistHasOpenAccess(TenantSlug tenantSlug, UUID specialistUserId, UUID patientId);

    /** Cierra la interconsulta. El acceso del especialista cae con esto, sin ningún otro paso. */
    boolean close(TenantSlug tenantSlug, UUID id, ServiceScope scope);

    /** Guarda la respuesta del especialista. false si la interconsulta no existe o ya no está abierta. */
    boolean saveResponse(TenantSlug tenantSlug, InterconsultationResponse response);
}
