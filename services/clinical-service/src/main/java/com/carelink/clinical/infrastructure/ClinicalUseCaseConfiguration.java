package com.carelink.clinical.infrastructure;

import com.carelink.clinical.application.CreateEncounterUseCase;
import com.carelink.clinical.application.ExportPatientFhirUseCase;
import com.carelink.clinical.application.ExportPatientPdfUseCase;
import com.carelink.clinical.application.GetEncounterUseCase;
import com.carelink.clinical.application.GetPatientUseCase;
import com.carelink.clinical.application.HandleGdprRequestUseCase;
import com.carelink.clinical.application.RegisterPatientUseCase;
import com.carelink.clinical.application.SignEncounterUseCase;
import com.carelink.clinical.application.UpdateEncounterUseCase;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncounterRepository;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.FhirExporter;
import com.carelink.clinical.domain.port.GdprIdentityStore;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.infrastructure.audit.NoOpAuditLogAdapter;
import com.carelink.clinical.infrastructure.persistence
    .InMemoryGdprIdentityStore;
import com.carelink.clinical.infrastructure.security.NoOpEncryptionAdapter;
import org.springframework.boot.autoconfigure.condition
    .ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de casos de uso para clinical-service.
 */
@Configuration(proxyBeanMethods = false)
public final class ClinicalUseCaseConfiguration {

    private ClinicalUseCaseConfiguration() {
    }

    @Bean
    static AuditLogPort auditLogPortFallback() {
        return new NoOpAuditLogAdapter();
    }

    @Bean
    static EncryptionPort encryptionPortFallback() {
        return new NoOpEncryptionAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(GdprIdentityStore.class)
    static GdprIdentityStore gdprIdentityStoreFallback() {
        return new InMemoryGdprIdentityStore();
    }

    @Bean
        static RegisterPatientUseCase registerPatientUseCase(
            final PatientRepository patientRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new RegisterPatientUseCase(
                patientRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static GetPatientUseCase getPatientUseCase(
            final PatientRepository patientRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new GetPatientUseCase(
                patientRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static CreateEncounterUseCase createEncounterUseCase(
            final EncounterRepository encounterRepository,
            final PatientRepository patientRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new CreateEncounterUseCase(
                encounterRepository,
                patientRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static UpdateEncounterUseCase updateEncounterUseCase(
            final EncounterRepository encounterRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new UpdateEncounterUseCase(
                encounterRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static SignEncounterUseCase signEncounterUseCase(
            final EncounterRepository encounterRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new SignEncounterUseCase(
                encounterRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static GetEncounterUseCase getEncounterUseCase(
            final EncounterRepository encounterRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new GetEncounterUseCase(
                encounterRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static ExportPatientFhirUseCase exportPatientFhirUseCase(
            final PatientRepository patientRepository,
            final FhirExporter fhirExporter,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new ExportPatientFhirUseCase(
                patientRepository,
                fhirExporter,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static ExportPatientPdfUseCase exportPatientPdfUseCase(
            final PatientRepository patientRepository,
            final AuditLogPort auditLogPort,
            final EncryptionPort encryptionPort) {
        return new ExportPatientPdfUseCase(
                patientRepository,
                auditLogPort,
                encryptionPort
        );
    }

    @Bean
        static HandleGdprRequestUseCase handleGdprRequestUseCase(
            final PatientRepository patientRepository,
            final GdprIdentityStore gdprIdentityStore,
            final EncryptionPort encryptionPort) {
        return new HandleGdprRequestUseCase(
                patientRepository,
                gdprIdentityStore,
                encryptionPort
        );
    }
}
