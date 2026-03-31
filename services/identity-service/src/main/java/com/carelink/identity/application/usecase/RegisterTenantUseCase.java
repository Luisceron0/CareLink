package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.domain.value.TaxId;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.port.EmailNotifier;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import com.carelink.identity.domain.exception.TenantAlreadyExistsException;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registers tenant, provisions schema, and creates admin user. */
public final class RegisterTenantUseCase {

    /** Tenant repository port. */
    private final TenantRepository tenantRepository;

    /** User repository port. */
    private final UserRepository userRepository;

    /** Schema provisioning port. */
    private final SchemaProvisioner schemaProvisioner;

    /** Email notifier port. */
    private final EmailNotifier emailNotifier;

    /** Password encoder port. */
    private final PasswordEncoder passwordEncoder;

    /** Verification token repository port. */
    private final VerificationTokenRepository tokenRepository;

    /**
     * Builds register-tenant use case.
     *
     * @param tenantRepositoryPort tenant repository
     * @param userRepositoryPort user repository
     * @param schemaProvisionerPort schema provisioner
     * @param emailNotifierPort email notifier
     * @param passwordEncoderPort password encoder
     * @param tokenRepositoryPort verification token repository
     */
    public RegisterTenantUseCase(
            final TenantRepository tenantRepositoryPort,
            final UserRepository userRepositoryPort,
            final SchemaProvisioner schemaProvisionerPort,
            final EmailNotifier emailNotifierPort,
            final PasswordEncoder passwordEncoderPort,
            final VerificationTokenRepository tokenRepositoryPort) {
        this.tenantRepository = tenantRepositoryPort;
        this.userRepository = userRepositoryPort;
        this.schemaProvisioner = schemaProvisionerPort;
        this.emailNotifier = emailNotifierPort;
        this.passwordEncoder = passwordEncoderPort;
        this.tokenRepository = tokenRepositoryPort;
    }

    /**
     * Executes tenant registration flow.
     *
     * @param name tenant name
     * @param slugStr tenant slug
     * @param taxIdStr tax id value
     * @param adminEmail admin email
     * @param rawPassword admin raw password
     * @return created tenant
     */
    public Tenant execute(
            final String name,
            final String slugStr,
            final String taxIdStr,
            final String adminEmail,
            final CharSequence rawPassword) {
        final TenantSlug slug = new TenantSlug(slugStr);
        if (tenantRepository.findBySlug(slug.value()).isPresent()) {
            throw new TenantAlreadyExistsException("Tenant exists");
        }
        new TaxId(taxIdStr);
        final Tenant tenant = new Tenant(
            UUID.randomUUID(),
            name,
            slug,
            OffsetDateTime.now()
        );
        tenantRepository.save(tenant);

        schemaProvisioner.provisionSchema(slug.value());

        final String hashed = passwordEncoder.encode(rawPassword);
        final User admin = new User(
            UUID.randomUUID(),
            tenant.id(),
            new Email(adminEmail),
            "TENANT_ADMIN",
            new HashedPassword(hashed),
            OffsetDateTime.now()
        );
        userRepository.save(admin);

        final String token = UUID.randomUUID().toString();
        tokenRepository.save(token, admin.id());
        emailNotifier.sendVerificationEmail(adminEmail, token);

        return tenant;
    }
}
