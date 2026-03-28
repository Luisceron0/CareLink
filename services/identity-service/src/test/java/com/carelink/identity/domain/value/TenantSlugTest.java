package com.carelink.identity.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TenantSlugTest {
    @Test
    public void validSlugCreatesValue() {
        TenantSlug s = new TenantSlug("clinic-1");
        assertEquals("clinic-1", s.value());
    }

    @Test
    public void invalidSlugThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TenantSlug("Invalid Slug"));
    }
}
