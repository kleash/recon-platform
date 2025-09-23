package com.universal.reconciliation.examples.harness;

import com.universal.reconciliation.UniversalReconciliationPlatformApplication;
import com.universal.reconciliation.examples.cashgl.CashVsGlEtlPipeline;
import com.universal.reconciliation.examples.custodian.CustodianHarnessController;
import com.universal.reconciliation.examples.custodian.CustodianTradeEtlPipeline;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the reconciliation platform with multiple standalone example pipelines loaded at once.
 */
@SpringBootApplication(scanBasePackageClasses = {
        UniversalReconciliationPlatformApplication.class,
        CashVsGlEtlPipeline.class,
        CustodianTradeEtlPipeline.class,
        CustodianHarnessController.class})
public class MultiExampleHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiExampleHarnessApplication.class, args);
    }
}
