package com.carelink.scheduling.integration;

import com.carelink.scheduling.SchedulingServiceApplication;
import com.carelink.scheduling.application.AvailabilityService;
import com.carelink.scheduling.domain.AvailabilityBlock;
import com.carelink.scheduling.domain.Physician;
import com.carelink.scheduling.domain.value.SlotDuration;
import com.carelink.scheduling.infrastructure.persistence.JpaPhysicianRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SchedulingServiceApplication.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Transactional
public class AvailabilityIntegrationTest {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private JpaPhysicianRepository physicianRepo;

    @Test
    void createAndListAvailability() {
        UUID physicianId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Physician p = new Physician(physicianId, tenantId, "Dr Test", "General");
        physicianRepo.save(p);

        AvailabilityBlock block = new AvailabilityBlock(UUID.randomUUID(), physicianId, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(12,0));
        availabilityService.createAvailability(tenantId, block);

        List<AvailabilityBlock> blocks = availabilityService.listAvailability(tenantId, physicianId);
        assertEquals(1, blocks.size());
    }

    @Test
    void createAvailabilityWithWrongTenantDenied() {
        UUID physicianId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Physician p = new Physician(physicianId, tenantId, "Dr Test", "General");
        physicianRepo.save(p);

        AvailabilityBlock block = new AvailabilityBlock(UUID.randomUUID(), physicianId, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(12,0));
        UUID otherTenant = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> availabilityService.createAvailability(otherTenant, block));
    }
}
