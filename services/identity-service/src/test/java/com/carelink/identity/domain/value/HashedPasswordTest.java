package com.carelink.identity.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HashedPasswordTest {
    @Test
    public void validHashedPasswordCreatesValue() {
        HashedPassword h = new HashedPassword("$argon2$hash");
        assertEquals("$argon2$hash", h.value());
    }

    @Test
    public void invalidHashedPasswordThrows() {
        assertThrows(IllegalArgumentException.class, () -> new HashedPassword(""));
    }
}
