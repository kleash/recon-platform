package com.universal.reconciliation.examples.custodian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application bootstrapper that wires the core platform with the custodian trade ETL components.
 */
@SpringBootApplication(scanBasePackages = {"com.universal.reconciliation", "com.universal.reconciliation.examples.custodian"})
public class CustodianTradeExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustodianTradeExampleApplication.class, args);
    }
}
