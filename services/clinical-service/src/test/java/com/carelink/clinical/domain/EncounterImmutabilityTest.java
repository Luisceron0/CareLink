package com.carelink.clinical.domain;

import com.carelink.clinical.domain.exception.ImmutableRecordException;
import com.carelink.clinical.domain.value.ICD10Code;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test de inmutabilidad de encuentros firmados.
 */
public class EncounterImmutabilityTest {

    @Test
    void signedEncounterCannotBeModified() {
        final Encounter encounter = new Encounter(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Headache",
                "Normal",
                "Hydration",
                "Return if pain persists",
                List.of(new Prescription(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ICD10Code("R51"),
                        "Ibuprofen",
                        "200mg"
                )),
                Instant.now(),
                Instant.now()
        );

        assertThrows(
                ImmutableRecordException.class,
                () -> encounter.updateClinicalContent(
                        "New complaint",
                        "New exam",
                        "New plan",
                        "New follow-up"
                )
        );
    }
}
