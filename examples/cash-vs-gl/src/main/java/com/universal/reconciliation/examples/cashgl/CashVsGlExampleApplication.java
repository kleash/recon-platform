package com.universal.reconciliation.examples.cashgl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone launcher that pulls in the core platform and the cash vs GL ETL.
 */
@SpringBootApplication(scanBasePackages = {"com.universal.reconciliation", "com.universal.reconciliation.examples.cashgl"})
public class CashVsGlExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashVsGlExampleApplication.class, args);
    }
}
