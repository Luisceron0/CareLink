package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.CriticalValueNotification;
import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabRepository {

    void saveOrder(TenantSlug tenantSlug, LabOrder order);

    Optional<LabOrder> findOrderById(TenantSlug tenantSlug, UUID orderId, ServiceScope scope);

    /**
     * FR-CLN-11 — carga el resultado. Devuelve la notificación creada si el valor es
     * crítico, o vacío si no lo es (o si la orden no existe / ya tenía resultado).
     */
    Optional<CriticalValueNotification> recordResult(TenantSlug tenantSlug, UUID orderId, String value,
                                                      String units, boolean critical, UUID resultedByUserId,
                                                      ServiceScope scope);

    /** Notificaciones de valor crítico pendientes para este médico. */
    List<CriticalValueNotification> findPendingNotifications(TenantSlug tenantSlug, UUID userId);

    boolean acknowledgeNotification(TenantSlug tenantSlug, UUID notificationId, UUID userId);
}
