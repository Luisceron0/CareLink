package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica auditoría en lectura de paciente.
 */
public class GetPatientUseCaseTest {

    @Test
    void readingPatientCreatesExactlyOneAuditEntry() {
        final UUID tenantId = UUID.randomUUID();
        final UUID actorId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final EncryptionPort encryptionPort = new EncryptionPort() {
            @Override
            public String encrypt(final UUID currentTenant,
                                  final String plaintext) {
                return "enc:" + plaintext;
            }

            @Override
            public String decrypt(final UUID currentTenant,
                                  final String ciphertext) {
                return ciphertext.replace("enc:", "");
            }
        };

        final Patient patient = new Patient(
                patientId,
                tenantId,
                encryptionPort.encrypt(tenantId, "Ada Lovelace"),
                new DocumentId("CC", "123456789"),
                BloodType.O_POSITIVE,
                encryptionPort.encrypt(tenantId, "3001234567"),
                encryptionPort.encrypt(tenantId, "ada@example.com"),
                encryptionPort.encrypt(tenantId, "Charles Babbage"),
                List.of(),
                List.of(),
                Instant.now()
        );

        final PatientRepository repository = new PatientRepository() {
            @Override
            public Patient save(final Patient entity) {
                return entity;
            }

            @Override
            public Optional<Patient> findByTenantAndId(final UUID tenant,
                    final UUID currentPatientId) {
                return Optional.of(patient);
            }
        };

        final AtomicInteger auditCounter = new AtomicInteger(0);
        final AuditLogPort auditLogPort = new AuditLogPort() {
            @Override
            public void recordPhiAccess(final UUID tenant,
                                        final UUID actor,
                                        final UUID targetPatient,
                                        final String action) {
                auditCounter.incrementAndGet();
            }
        };

        final GetPatientUseCase useCase = new GetPatientUseCase(
                repository,
                auditLogPort,
                encryptionPort
        );

        final Patient result = useCase.getById(tenantId, actorId, patientId);

        assertEquals("Ada Lovelace", result.fullName());
        assertEquals(1, auditCounter.get());
    }
}
