package com.carelink.identity.infrastructure.web;

import com.carelink.identity.application.dto.InviteUserRequest;
import com.carelink.identity.application.usecase.DeactivateUserUseCase;
import com.carelink.identity.application.usecase.InviteUserUseCase;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.exception.InvalidRoleException;
import com.carelink.identity.domain.exception.UserAlreadyExistsException;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-ID-02. Solo {@code TENANT_ADMIN} invita o desactiva usuarios, y siempre dentro
 * de su propio tenant — mismo patrón de {@code AuthenticatedPrincipal.tenantId()}
 * resuelto server-side que {@code PatientController}/{@code ClinicalEncounterController},
 * nunca un tenant que el cliente indique.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final InviteUserUseCase inviteUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final TenantRepository tenantRepository;

    public UserManagementController(InviteUserUseCase inviteUserUseCase,
                                     DeactivateUserUseCase deactivateUserUseCase,
                                     TenantRepository tenantRepository) {
        this.inviteUserUseCase = inviteUserUseCase;
        this.deactivateUserUseCase = deactivateUserUseCase;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping("/invite")
    public ResponseEntity<?> invite(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestBody InviteUserRequest req) {
        if (!TENANT_ADMIN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Tenant> tenant = resolveTenant(principal);
        if (tenant.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            User invited = inviteUserUseCase.execute(tenant.get(), req.getEmail(), req.getRole(), req.getServiceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", invited.id().toString(),
                    "email", invited.email().value(),
                    "role", invited.role()));
        } catch (InvalidRoleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                         @PathVariable UUID id) {
        if (!TENANT_ADMIN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Tenant> tenant = resolveTenant(principal);
        if (tenant.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return deactivateUserUseCase.execute(tenant.get(), id)
                .map(u -> ResponseEntity.ok().build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private Optional<Tenant> resolveTenant(AuthenticatedPrincipal principal) {
        if (principal == null || principal.tenantId() == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(principal.tenantId());
    }
}
