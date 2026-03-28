package com.carelink.identity.domain;

import com.carelink.identity.domain.value.TenantSlug;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class TenantTest {
    @Test
    public void createTenantRecord() {
        Tenant t = new Tenant(UUID.randomUUID(), "Clinic", new TenantSlug("clinic-1"), OffsetDateTime.now());
        assertNotNull(t.id());
        assertEquals("clinic-1", t.slug().value());
    }
}
