package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.exception.InvalidRoleException;
import com.carelink.identity.domain.exception.UserAlreadyExistsException;
import com.carelink.identity.domain.port.EmailNotifier;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.value.KnownRoles;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * FR-ID-02 — {@code TENANT_ADMIN} invita usuarios, les asigna rol (§4) y
 * {@code service_id}. {@code @Component}, no instanciado a mano — mismo motivo que
 * {@code RegisterPatientUseCase}: {@code @Auditable} solo intercepta beans que
 * administra Spring.
 *
 * <p>El usuario invitado se crea con una contraseña aleatoria que nadie conoce — ni
 * siquiera queda en un log — y solo se vuelve utilizable cuando el invitado la
 * reemplaza vía {@link AcceptInvitationUseCase}, usando el token de un solo uso que
 * este caso de uso genera y "envía". Hasta ese momento el usuario existe pero no
 * puede autenticarse con ninguna contraseña conocida — no hace falta un estado
 * "pendiente" aparte para bloquear el login.
 */
@Component
public class InviteUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository;
    private final EmailNotifier emailNotifier;
    private final SecureRandom random = new SecureRandom();

    public InviteUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              VerificationTokenRepository tokenRepository, EmailNotifier emailNotifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.emailNotifier = emailNotifier;
    }

    @Auditable(action = "USER_INVITE", tenantSlugExpression = "#tenant.slug().value()")
    public User execute(Tenant tenant, String email, String role, String serviceId) {
        if (!KnownRoles.isValid(role)) {
            throw new InvalidRoleException("Rol inválido: " + role);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese email");
        }

        byte[] unusablePassword = new byte[32];
        random.nextBytes(unusablePassword);
        String hashed = passwordEncoder.encode(Base64.getEncoder().encodeToString(unusablePassword));

        User invited = new User(UUID.randomUUID(), tenant.id(), new Email(email), role, serviceId, true,
                new HashedPassword(hashed), OffsetDateTime.now());
        userRepository.save(invited);

        String token = UUID.randomUUID().toString();
        tokenRepository.save(token, invited.id());
        emailNotifier.sendInvitationEmail(email, token, role);

        return invited;
    }
}
