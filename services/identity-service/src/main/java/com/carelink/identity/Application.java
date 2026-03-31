package com.carelink.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public final class Application {

    private Application() {
    }

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
