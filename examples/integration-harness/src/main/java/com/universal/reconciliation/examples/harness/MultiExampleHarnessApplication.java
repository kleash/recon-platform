package com.universal.reconciliation.examples.harness;

import com.universal.reconciliation.UniversalReconciliationPlatformApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the reconciliation platform for the integration harness without embedding example ETL pipelines.
 */
@SpringBootApplication(scanBasePackageClasses = UniversalReconciliationPlatformApplication.class)
public class MultiExampleHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiExampleHarnessApplication.class, args);
    }
}
