package com.universal.reconciliation.examples.custodian;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.service.ExportService;
import com.universal.reconciliation.service.ReconciliationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Coordinates reconciliation cutoffs and report scheduling for the custodian trade example.
 */
@Component
public class CustodianTradeScheduler {

    private final ScenarioClock clock;
    private final ReconciliationService reconciliationService;
    private final CustodianReportScheduler reportScheduler;

    private ReconciliationDefinition definition;
    private List<String> expectedCustodians = List.of();

    private final Map<CycleKey, CutoffState> states = new LinkedHashMap<>();

    public CustodianTradeScheduler(
            ScenarioClock clock, ReconciliationService reconciliationService, ExportService exportService) {
        this.clock = clock;
        this.reconciliationService = reconciliationService;
        this.reportScheduler = new CustodianReportScheduler(exportService, clock);
    }

    public void configure(ReconciliationDefinition definition, List<String> custodians) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.expectedCustodians = List.copyOf(custodians);
        this.states.clear();
        this.reportScheduler.reset();
    }

    public void recordCustodianArrival(LocalDate businessDate, CutoffCycle cycle, String custodian) {
        CutoffState state = state(businessDate, cycle);
        state.custodianArrivals.put(custodian, clock.instant());
        evaluate(state);
    }

    public void recordPlatformArrival(LocalDate businessDate, CutoffCycle cycle) {
        CutoffState state = state(businessDate, cycle);
        state.platformArrival = clock.instant();
        evaluate(state);
    }

    public void evaluateCurrentInstant() {
        Instant now = clock.instant();
        for (CutoffState state : states.values()) {
            if (!state.runTriggered) {
                Instant cutoffInstant = state.cutoffInstant(clock.getZone());
                if (!now.isBefore(cutoffInstant)) {
                    triggerRun(state, true);
                }
            }
        }
        reportScheduler.evaluate(now);
    }

    public List<CutoffResult> results() {
        return states.values().stream()
                .filter(state -> state.runTriggered && state.runDetail != null)
                .map(state -> new CutoffResult(state.businessDate, state.cycle, state.triggeredByCutoff, state.runDetail))
                .collect(Collectors.toList());
    }

    public List<ReportExecution> reportExecutions() {
        return reportScheduler.executions();
    }

    private void evaluate(CutoffState state) {
        if (!state.runTriggered
                && state.platformArrival != null
                && state.custodianArrivals.keySet().containsAll(expectedCustodians)) {
            triggerRun(state, false);
        }
        reportScheduler.evaluate(clock.instant());
    }

    private void triggerRun(CutoffState state, boolean dueToCutoff) {
        state.runTriggered = true;
        state.triggeredByCutoff = dueToCutoff;
        TriggerRunRequest request = new TriggerRunRequest(
                TriggerType.SCHEDULED_CRON,
                state.cycle.name() + "-" + state.businessDate,
                dueToCutoff
                        ? "Triggered at cutoff due to outstanding files"
                        : "Triggered automatically once all files were available",
                "custodian-scheduler");
        RunDetailDto run = reconciliationService.triggerRun(
                definition.getId(),
                List.of("recon-makers", "recon-checkers"),
                "custodian-scheduler",
                request);
        state.runDetail = run;
        reportScheduler.register(state, run);
    }

    private CutoffState state(LocalDate date, CutoffCycle cycle) {
        CycleKey key = new CycleKey(date, cycle);
        return states.computeIfAbsent(key, ignored -> new CutoffState(date, cycle));
    }

    private static final class CutoffState {
        private final LocalDate businessDate;
        private final CutoffCycle cycle;
        private final Map<String, Instant> custodianArrivals = new LinkedHashMap<>();
        private Instant platformArrival;
        private boolean runTriggered;
        private boolean triggeredByCutoff;
        private RunDetailDto runDetail;

        private CutoffState(LocalDate businessDate, CutoffCycle cycle) {
            this.businessDate = businessDate;
            this.cycle = cycle;
        }

        private Instant cutoffInstant(ZoneId zoneId) {
            LocalDateTime cutoffDateTime = LocalDateTime.of(businessDate, cycle.cutoff());
            return cutoffDateTime.atZone(zoneId).toInstant();
        }
    }

    private record CycleKey(LocalDate businessDate, CutoffCycle cycle) {}

    public record CutoffResult(
            LocalDate businessDate, CutoffCycle cycle, boolean triggeredByCutoff, RunDetailDto runDetail) {}

    public record ReportExecution(
            CutoffCycle cycle, String window, Instant scheduledAt, Instant executedAt, byte[] workbook) {}

    private static final class CustodianReportScheduler {

        private final ExportService exportService;
        private final ScenarioClock clock;
        private final List<ReportTask> tasks = new ArrayList<>();
        private final List<ReportExecution> executions = new ArrayList<>();

        private CustodianReportScheduler(ExportService exportService, ScenarioClock clock) {
            this.exportService = exportService;
            this.clock = clock;
        }

        void register(CutoffState state, RunDetailDto runDetail) {
            LocalDate businessDate = state.businessDate;
            if (state.cycle == CutoffCycle.MORNING) {
                schedule(runDetail, state.cycle, "15:00 Daily", businessDate, 15, 0);
            } else {
                schedule(runDetail, state.cycle, "21:00 Daily", businessDate, 21, 0);
                schedule(runDetail, state.cycle, "02:00 Overnight", businessDate.plusDays(1), 2, 0);
            }
        }

        void evaluate(Instant now) {
            for (ReportTask task : tasks) {
                if (!task.executed && !now.isBefore(task.scheduledAt)) {
                    byte[] workbook = exportService.exportToExcel(task.runDetail);
                    executions.add(new ReportExecution(
                            task.cycle,
                            task.window,
                            task.scheduledAt,
                            now,
                            Arrays.copyOf(workbook, workbook.length)));
                    task.executed = true;
                }
            }
        }

        List<ReportExecution> executions() {
            return List.copyOf(executions);
        }

        void reset() {
            tasks.clear();
            executions.clear();
        }

        private void schedule(
                RunDetailDto runDetail, CutoffCycle cycle, String window, LocalDate date, int hour, int minute) {
            Instant scheduledAt = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
                    .atZone(clock.getZone())
                    .toInstant();
            tasks.add(new ReportTask(runDetail, cycle, window, scheduledAt));
        }

        private static final class ReportTask {
            private final RunDetailDto runDetail;
            private final CutoffCycle cycle;
            private final String window;
            private final Instant scheduledAt;
            private boolean executed;

            private ReportTask(RunDetailDto runDetail, CutoffCycle cycle, String window, Instant scheduledAt) {
                this.runDetail = runDetail;
                this.cycle = cycle;
                this.window = window;
                this.scheduledAt = scheduledAt;
            }
        }
    }
}
