package com.carelink.scheduling.domain;

import com.carelink.scheduling.domain.value.AppointmentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AppointmentStatusTest {

    @Test
    void transitionsAllowed() {
        assertTrue(AppointmentStatus.PENDING.isValidTransition(
                AppointmentStatus.CONFIRMED));
        assertTrue(AppointmentStatus.CONFIRMED.isValidTransition(
                AppointmentStatus.IN_PROGRESS));
        assertTrue(AppointmentStatus.IN_PROGRESS.isValidTransition(
                AppointmentStatus.COMPLETED));
    }

    @Test
    void transitionsForbidden() {
        assertFalse(AppointmentStatus.PENDING.isValidTransition(
                AppointmentStatus.COMPLETED));
        assertFalse(AppointmentStatus.COMPLETED.isValidTransition(
                AppointmentStatus.PENDING));
    }
}
