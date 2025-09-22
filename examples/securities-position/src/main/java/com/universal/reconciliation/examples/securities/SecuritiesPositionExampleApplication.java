package com.universal.reconciliation.examples.securities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Launcher that wires the platform with the securities position ETL sample.
 */
@SpringBootApplication(scanBasePackages = {"com.universal.reconciliation", "com.universal.reconciliation.examples.securities"})
public class SecuritiesPositionExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecuritiesPositionExampleApplication.class, args);
    }
}
