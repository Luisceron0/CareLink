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

public class RegisterTenantUseCase {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SchemaProvisioner schemaProvisioner;
    private final EmailNotifier emailNotifier;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository;

    public RegisterTenantUseCase(TenantRepository tenantRepository,
                                 UserRepository userRepository,
                                 SchemaProvisioner schemaProvisioner,
                                 EmailNotifier emailNotifier,
                                 PasswordEncoder passwordEncoder,
                                 VerificationTokenRepository tokenRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.schemaProvisioner = schemaProvisioner;
        this.emailNotifier = emailNotifier;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
    }

    public Tenant execute(String name, String slugStr, String taxIdStr, String adminEmail, CharSequence rawPassword) {
        TenantSlug slug = new TenantSlug(slugStr);
        if (tenantRepository.findBySlug(slug.value()).isPresent()) {
            throw new TenantAlreadyExistsException("Tenant exists");
        }
        TaxId taxId = new TaxId(taxIdStr);
        Tenant tenant = new Tenant(UUID.randomUUID(), name, slug, OffsetDateTime.now());
        tenantRepository.save(tenant);

        // Provision tenant schema (adapter implemented in F1-T02)
        schemaProvisioner.provisionSchema(slug.value());

        // Create initial TENANT_ADMIN user
        String hashed = passwordEncoder.encode(rawPassword);
        User admin = new User(UUID.randomUUID(), tenant.id(), new Email(adminEmail), "TENANT_ADMIN", new HashedPassword(hashed), OffsetDateTime.now());
        userRepository.save(admin);

        // Create verification token and send email
        String token = UUID.randomUUID().toString();
        tokenRepository.save(token, admin.id());
        emailNotifier.sendVerificationEmail(adminEmail, token);

        return tenant;
    }
}
