package com.carelink.scheduling.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SlotDurationTest {

    @Test
    void positiveDurationAllowed() {
        SlotDuration d = new SlotDuration(30);
        assertEquals(30, d.minutes());
    }

    @Test
    void zeroOrNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SlotDuration(0));
        assertThrows(IllegalArgumentException.class, () -> new SlotDuration(-15));
    }
}
