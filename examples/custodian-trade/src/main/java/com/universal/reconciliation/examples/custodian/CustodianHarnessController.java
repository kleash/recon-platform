package com.universal.reconciliation.examples.custodian;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes custodian example diagnostics for the integration harness profile.
 */
@Profile("example-harness")
@RestController
@RequestMapping("/api/examples/custodian")
public class CustodianHarnessController {

    private final CustodianTradeScheduler scheduler;

    public CustodianHarnessController(CustodianTradeScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/cutoffs")
    public List<CustodianTradeScheduler.CutoffResult> cutoffs() {
        return scheduler.results();
    }

    @GetMapping("/reports")
    public List<CustodianTradeScheduler.ReportExecution> reports() {
        return scheduler.reportExecutions();
    }
}
