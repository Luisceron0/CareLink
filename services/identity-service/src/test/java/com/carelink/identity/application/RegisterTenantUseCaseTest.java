package com.carelink.identity.application;

import com.carelink.identity.application.usecase.RegisterTenantUseCase;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.Tenant;
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

public class RegisterTenantUseCaseTest {
    private InMemoryTenantRepo repo;
    private RegisterTenantUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = new InMemoryTenantRepo();
        useCase = new RegisterTenantUseCase(repo);
    }

    @Test
    void registerCreatesTenant() {
        Tenant t = useCase.execute("Clinic", "clinic-1", "123456789");
        assertNotNull(t.id());
        assertEquals("clinic-1", t.slug().value());
    }

    @Test
    void duplicateSlugThrows() {
        useCase.execute("Clinic", "clinic-1", "1234");
        assertThrows(RuntimeException.class, () -> useCase.execute("Other", "clinic-1", "999"));
    }
}
