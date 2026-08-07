package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.User;
import com.carelink.identity.domain.exception.InvalidInvitationTokenException;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import com.carelink.identity.domain.value.HashedPassword;

/**
 * FR-ID-02, segunda mitad — el usuario invitado usa el token de un solo uso que le
 * llegó por email para fijar su propia contraseña y activar la cuenta que
 * {@link InviteUserUseCase} creó con una contraseña aleatoria que nadie conoce.
 *
 * <p>No es {@code @Auditable}: corre sin autenticación (el invitado todavía no
 * tiene sesión) y no hay tenant resuelto desde un JWT en este punto — el mismo
 * motivo por el que {@code VerifyEmailUseCase} tampoco lo es.
 */
public class AcceptInvitationUseCase {

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AcceptInvitationUseCase(VerificationTokenRepository tokenRepository, UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void execute(String token, CharSequence newPassword) {
        var userId = tokenRepository.findUserIdByToken(token)
                .orElseThrow(() -> new InvalidInvitationTokenException("Token de invitación inválido o ya usado"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidInvitationTokenException("Token de invitación inválido o ya usado"));

        String hashed = passwordEncoder.encode(newPassword);
        User activated = new User(user.id(), user.tenantId(), user.email(), user.role(), user.serviceId(),
                user.active(), new HashedPassword(hashed), user.createdAt());
        userRepository.save(activated);

        tokenRepository.delete(token);
    }
}
