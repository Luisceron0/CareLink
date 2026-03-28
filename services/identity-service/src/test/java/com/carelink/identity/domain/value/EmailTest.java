package com.carelink.identity.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EmailTest {
    @Test
    public void validEmailCreatesValue() {
        Email e = new Email("User@Example.com");
        assertEquals("user@example.com", e.value());
    }

    @Test
    public void invalidEmailThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
    }
}
