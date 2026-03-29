package com.carelink.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entrypoint para scheduling-service.
 */
@SpringBootApplication(scanBasePackages = "com.carelink")
public final class SchedulingServiceApplication {

    private SchedulingServiceApplication() {
        // Utility class
    }

    /**
     * Arranca la aplicación Spring Boot.
     *
     * @param args argumentos de la JVM
     */
    public static void main(final String[] args) {
        SpringApplication.run(SchedulingServiceApplication.class, args);
    }
}
