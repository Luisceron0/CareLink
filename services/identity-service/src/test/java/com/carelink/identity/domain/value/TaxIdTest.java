package com.carelink.identity.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TaxIdTest {
    @Test
    public void validTaxIdCreatesValue() {
        TaxId t = new TaxId(" 12345 ");
        assertEquals("12345", t.value());
    }

    @Test
    public void invalidTaxIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TaxId("   "));
    }
}
