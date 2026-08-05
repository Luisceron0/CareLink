package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * FR-ID-02 — desactivar, nunca borrar: {@code users} tiene {@code ON DELETE
 * RESTRICT} a propósito (V1), y el historial de auditoría de un usuario
 * desactivado debe retenerse permanentemente. {@code @Component}/{@code @Auditable}
 * mismo motivo que {@link InviteUserUseCase}.
 *
 * <p>Devuelve {@code Optional.empty()} tanto si el usuario no existe como si
 * pertenece a otro tenant — mismo principio que {@code GetPatientUseCase}/AC-06: el
 * controller traduce ambos casos a la misma respuesta, para que un intento de
 * desactivar el usuario de otro tenant no confirme que ese id existe.
 */
@Component
public class DeactivateUserUseCase {

    private final UserRepository userRepository;

    public DeactivateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Auditable(action = "USER_DEACTIVATE", tenantSlugExpression = "#tenant.slug().value()")
    public Optional<User> execute(Tenant tenant, UUID targetUserId) {
        Optional<User> target = userRepository.findById(targetUserId)
                .filter(u -> u.tenantId().equals(tenant.id()));
        if (target.isEmpty()) {
            return Optional.empty();
        }

        User existing = target.get();
        User deactivated = new User(existing.id(), existing.tenantId(), existing.email(), existing.role(),
                existing.serviceId(), false, existing.password(), existing.createdAt());
        userRepository.save(deactivated);
        return Optional.of(deactivated);
    }
}
