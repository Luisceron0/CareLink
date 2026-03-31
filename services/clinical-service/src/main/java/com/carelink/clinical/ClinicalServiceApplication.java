package com.carelink.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entrypoint para clinical-service.
 */
@SpringBootApplication(
    scanBasePackages = "com.carelink",
    proxyBeanMethods = false
)
public final class ClinicalServiceApplication {

    private ClinicalServiceApplication() {
        // Utility class
    }

    /**
     * Arranca la aplicacion Spring Boot.
     *
     * @param args argumentos de la JVM
     */
    public static void main(final String[] args) {
        SpringApplication.run(ClinicalServiceApplication.class, args);
    }
}
