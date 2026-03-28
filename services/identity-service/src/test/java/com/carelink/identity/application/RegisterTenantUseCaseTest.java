package com.carelink.identity.application;

import com.carelink.identity.application.usecase.RegisterTenantUseCase;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.port.EmailNotifier;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryTenantRepo implements TenantRepository {
    private java.util.Map<String, Tenant> map = new java.util.HashMap<>();
    @Override public Optional<Tenant> findBySlug(String slug) { return Optional.ofNullable(map.get(slug)); }
    @Override public void save(Tenant tenant) { map.put(tenant.slug().value(), tenant); }
}

class InMemoryUserRepo implements UserRepository {
    private java.util.Map<String, User> map = new java.util.HashMap<>();
    @Override public Optional<User> findByEmail(String email) { return Optional.ofNullable(map.get(email)); }
    @Override public Optional<User> findById(java.util.UUID id) { return map.values().stream().filter(u -> u.id().equals(id)).findFirst(); }
    @Override public void save(User user) { map.put(user.email().value(), user); }
}

class InMemorySchemaProvisioner implements SchemaProvisioner {
    public boolean provisioned = false;
    @Override public void provisionSchema(String tenantSlug) { this.provisioned = true; }
}

class InMemoryEmailNotifier implements EmailNotifier {
    public String lastTo;
    public String lastToken;
    @Override public void sendVerificationEmail(String to, String token) { this.lastTo = to; this.lastToken = token; }
}

class InMemoryPasswordEncoder implements PasswordEncoder {
    @Override public String encode(CharSequence rawPassword) { return "encoded:" + rawPassword; }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return encodedPassword.equals("encoded:" + rawPassword); }
}

class InMemoryVerificationTokenRepo implements VerificationTokenRepository {
    private java.util.Map<String, UUID> map = new java.util.HashMap<>();
    @Override public void save(String token, UUID userId) { map.put(token, userId); }
    @Override public Optional<UUID> findUserIdByToken(String token) { return Optional.ofNullable(map.get(token)); }
    @Override public void delete(String token) { map.remove(token); }
}

public class RegisterTenantUseCaseTest {
    private InMemoryTenantRepo repo;
    private InMemoryUserRepo userRepo;
    private InMemorySchemaProvisioner provisioner;
    private InMemoryEmailNotifier emailNotifier;
    private InMemoryPasswordEncoder passwordEncoder;
    private InMemoryVerificationTokenRepo tokenRepo;
    private RegisterTenantUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = new InMemoryTenantRepo();
        userRepo = new InMemoryUserRepo();
        provisioner = new InMemorySchemaProvisioner();
        emailNotifier = new InMemoryEmailNotifier();
        passwordEncoder = new InMemoryPasswordEncoder();
        tokenRepo = new InMemoryVerificationTokenRepo();
        useCase = new RegisterTenantUseCase(repo, userRepo, provisioner, emailNotifier, passwordEncoder, tokenRepo);
    }

    @Test
    void registerCreatesTenant() {
        Tenant t = useCase.execute("Clinic", "clinic-1", "123456789", "admin@clinic.test", "secret");
        assertNotNull(t.id());
        assertEquals("clinic-1", t.slug().value());
        assertTrue(provisioner.provisioned);
        assertEquals("admin@clinic.test", emailNotifier.lastTo);
        assertNotNull(emailNotifier.lastToken);
        assertTrue(tokenRepo.findUserIdByToken(emailNotifier.lastToken).isPresent());
        assertEquals(userRepo.findByEmail("admin@clinic.test").get().id(), tokenRepo.findUserIdByToken(emailNotifier.lastToken).get());
    }

    @Test
    void duplicateSlugThrows() {
        useCase.execute("Clinic", "clinic-1", "1234", "admin@clinic.test", "pwd");
        assertThrows(RuntimeException.class, () -> useCase.execute("Other", "clinic-1", "999", "other@clinic.test", "pwd2"));
    }
}
