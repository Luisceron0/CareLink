package com.carelink.scheduling.integration;

import com.carelink.scheduling.SchedulingServiceApplication;
import com.carelink.scheduling.application.BookAppointmentUseCase;
import com.carelink.scheduling.domain.exception.SlotAlreadyBookedException;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica colisiones de reserva bajo concurrencia.
 */
@SpringBootTest(classes = SchedulingServiceApplication.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AppointmentConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private BookAppointmentUseCase bookAppointmentUseCase;

    @Test
    void concurrentBookingsSingleSuccessAndNineConflicts()
            throws InterruptedException {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();
        final Duration duration = Duration.ofMinutes(30);
        final LocalDateTime start = LocalDateTime.now()
                .plusDays(2)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        final AtomicInteger successCount = new AtomicInteger();
        final AtomicInteger conflictCount = new AtomicInteger();
        final List<Throwable> unexpected = new ArrayList<>();

        final CountDownLatch ready = new CountDownLatch(THREADS);
        final CountDownLatch go = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(3, TimeUnit.SECONDS);
                    bookAppointmentUseCase.bookAppointment(
                            tenantId,
                            physicianId,
                            UUID.randomUUID(),
                            start,
                            duration
                    );
                    successCount.incrementAndGet();
                } catch (SlotAlreadyBookedException ex) {
                    conflictCount.incrementAndGet();
                } catch (Throwable ex) {
                    synchronized (unexpected) {
                        unexpected.add(ex);
                    }
                }
            });
        }

        assertTrue(ready.await(3, TimeUnit.SECONDS));
        go.countDown();

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get());
        assertEquals(9, conflictCount.get());
        assertEquals(0, unexpected.size());
    }
}
