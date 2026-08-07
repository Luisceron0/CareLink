package com.carelink.identity.integration;

import com.carelink.identity.application.usecase.AcceptInvitationUseCase;
import com.carelink.identity.application.usecase.DeactivateUserUseCase;
import com.carelink.identity.application.usecase.InviteUserUseCase;
import com.carelink.identity.application.usecase.LoginUseCase;
import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.exception.InvalidRoleException;
import com.carelink.identity.domain.exception.UserAlreadyExistsException;
import com.carelink.identity.domain.port.*;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;

/**
 * FR-ID-02 de punta a punta, con los beans reales de Spring — mismo motivo que
 * {@code PatientLifecycleIT}: {@code InviteUserUseCase}/{@code DeactivateUserUseCase}
 * son {@code @Auditable}, y eso solo se intercepta a través del proxy AOP de beans
 * que Spring administra.
 *
 * <p>{@code EmailNotifier} se mockea (no hay servidor SMTP en este test) — mismo
 * patrón que {@code AuthControllerSecurityIT} — y el token de invitación se captura
 * del argumento con el que se llamó al mock, porque {@link InviteUserUseCase} nunca
 * lo devuelve directamente: solo lo "envía".
 */
@SpringBootTest(properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class UserManagementLifecycleIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SchemaProvisioner schemaProvisioner;

    @Autowired
    private InviteUserUseCase inviteUserUseCase;

    @Autowired
    private DeactivateUserUseCase deactivateUserUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @MockBean
    private EmailNotifier emailNotifier;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "usermgmt");
    }

    private Tenant createTenant(String slugStr) {
        TenantSlug slug = new TenantSlug(slugStr);
        Tenant tenant = new Tenant(UUID.randomUUID(), "Clinica " + slugStr, slug, OffsetDateTime.now());
        tenantRepository.save(tenant);
        schemaProvisioner.provisionSchema(slug);
        return tenant;
    }

    @Test
    @DisplayName("FR-ID-02 — invitar, aceptar, loguearse con el rol asignado, desactivar, ya no poder loguearse")
    void inviteAcceptLoginDeactivateLifecycle() {
        Tenant tenant = createTenant("invitelifecycle");

        User invited = inviteUserUseCase.execute(tenant, "medico@invitelifecycle.test", "PHYSICIAN", "Urgencias");
        assertThat(invited.role()).isEqualTo("PHYSICIAN");
        assertThat(invited.serviceId()).isEqualTo("Urgencias");
        assertThat(invited.active()).isTrue();

        var tokenCaptor = forClass(String.class);
        verify(emailNotifier).sendInvitationEmail(
                org.mockito.ArgumentMatchers.eq("medico@invitelifecycle.test"),
                tokenCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("PHYSICIAN"));
        String token = tokenCaptor.getValue();

        // Antes de aceptar la invitación: la contraseña es aleatoria e
        // inutilizable, ninguna contraseña conocida entra.
        assertThatThrownBy(() -> new LoginUseCase(userRepository, passwordEncoder, sessionRepository)
                .execute("medico@invitelifecycle.test", "cualquier-cosa"))
                .isInstanceOf(RuntimeException.class);

        new AcceptInvitationUseCase(verificationTokenRepository, userRepository, passwordEncoder)
                .execute(token, "NuevaClaveSegura#2026");

        // Aceptar la invitación activa el login con la contraseña recién fijada.
        Session session = new LoginUseCase(userRepository, passwordEncoder, sessionRepository)
                .execute("medico@invitelifecycle.test", "NuevaClaveSegura#2026");
        assertThat(session.userId()).isEqualTo(invited.id());

        // El token es de un solo uso.
        assertThat(verificationTokenRepository.findUserIdByToken(token)).isEmpty();

        Integer inviteAuditRows = adminJdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_invitelifecycle.audit_log WHERE action = 'USER_INVITE'",
                Integer.class);
        assertThat(inviteAuditRows).isEqualTo(1);

        // FR-ID-02: desactivar, no borrar.
        Optional<User> deactivated = deactivateUserUseCase.execute(tenant, invited.id());
        assertThat(deactivated).isPresent();
        assertThat(deactivated.get().active()).isFalse();

        assertThatThrownBy(() -> new LoginUseCase(userRepository, passwordEncoder, sessionRepository)
                .execute("medico@invitelifecycle.test", "NuevaClaveSegura#2026"))
                .isInstanceOf(RuntimeException.class);

        Integer deactivateAuditRows = adminJdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_invitelifecycle.audit_log WHERE action = 'USER_DEACTIVATE'",
                Integer.class);
        assertThat(deactivateAuditRows).isEqualTo(1);

        // La fila de users sigue existiendo — ON DELETE RESTRICT, nunca se borra.
        assertThat(userRepository.findById(invited.id())).isPresent();
    }

    @Test
    @DisplayName("invitar con un rol que no está en el §4 se rechaza")
    void inviteWithUnknownRoleIsRejected() {
        Tenant tenant = createTenant("invalidroletenant");

        assertThatThrownBy(() -> inviteUserUseCase.execute(tenant, "x@invalidroletenant.test", "SUPERUSER", null))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    @DisplayName("invitar dos veces el mismo email se rechaza")
    void inviteWithDuplicateEmailIsRejected() {
        Tenant tenant = createTenant("duplicateemailtenant");

        inviteUserUseCase.execute(tenant, "repetido@duplicateemailtenant.test", "NURSE", null);

        assertThatThrownBy(() -> inviteUserUseCase.execute(tenant, "repetido@duplicateemailtenant.test", "NURSE", null))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("AC-06 aplicado a gestión de usuarios — desactivar el usuario de otro tenant no hace nada, y no lo revela")
    void deactivatingUserFromAnotherTenantIsANoOp() {
        Tenant tenantA = createTenant("deactcrosstenanta");
        Tenant tenantB = createTenant("deactcrosstenantb");

        User userInA = inviteUserUseCase.execute(tenantA, "victima@deactcrosstenanta.test", "NURSE", null);

        Optional<User> result = deactivateUserUseCase.execute(tenantB, userInA.id());
        assertThat(result).isEmpty();

        // Y el usuario de A sigue activo — el intento cross-tenant no tuvo efecto.
        assertThat(userRepository.findById(userInA.id())).get()
                .extracting(User::active).isEqualTo(true);
    }
}
