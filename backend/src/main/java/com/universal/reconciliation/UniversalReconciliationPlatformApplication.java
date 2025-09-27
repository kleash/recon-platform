package com.universal.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Universal Reconciliation Platform backend application.
 * Phase 1 keeps the service monolithic while exposing clear module boundaries
 * so later phases can be decomposed into autonomous services.
 */
@SpringBootApplication
@EnableAsync
public class UniversalReconciliationPlatformApplication {

    /**
     * Boots the Spring context.
     *
     * @param args runtime arguments supplied by the hosting environment.
     */
    public static void main(String[] args) {
        SpringApplication.run(UniversalReconciliationPlatformApplication.class, args);
    }
}
