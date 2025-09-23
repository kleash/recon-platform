package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReconciliationServiceIntegrationTest {

    private static final String SIMPLE_CODE = "CASH_VS_GL_SIMPLE";

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private AccessControlEntryRepository accessControlEntryRepository;

    @Autowired
    private SourceRecordARepository sourceRecordARepository;

    @Autowired
    private SourceRecordBRepository sourceRecordBRepository;

    private final List<String> groups = List.of("recon-makers", "recon-checkers");

    @BeforeEach
    void setUp() {
        ensureSimpleDefinition();
    }

    @Test
    void fetchLatestRun_returnsFilteredBreaksAndMetadata() {
        Long definitionId = definitionId(SIMPLE_CODE);
        RunDetailDto initial = reconciliationService.triggerRun(
                definitionId,
                groups,
                "integration-test",
                new TriggerRunRequest(TriggerType.MANUAL_API, "it-run", "integration test", null));
        assertThat(initial.summary().runId()).isNotNull();
        assertThat(initial.summary().triggerType()).isEqualTo(TriggerType.MANUAL_API);
        assertThat(initial.analytics().totalBreakCount()).isGreaterThan(0);

        RunDetailDto usBreaks = reconciliationService.fetchLatestRun(
                definitionId,
                groups,
                new BreakFilterCriteria("Payments", "Wire", "US", Set.of(BreakStatus.OPEN)));

        assertThat(usBreaks.breaks())
                .allSatisfy(breakItem -> assertThat(breakItem.entity()).isEqualTo("US"));
        assertThat(usBreaks.filters().products()).contains("Payments");
        assertThat(usBreaks.analytics().breaksByStatus()).containsKey(BreakStatus.OPEN.name());

        RunDetailDto euOnly = reconciliationService.fetchLatestRun(
                definitionId,
                groups,
                new BreakFilterCriteria(null, null, "EU", Set.of(BreakStatus.OPEN)));

        assertThat(euOnly.breaks())
                .hasSize(1)
                .first()
                .extracting(b -> b.entity())
                .isEqualTo("EU");
    }

    @Test
    void triggerRun_rejectsRequestsWithoutAccess() {
        Long definitionId = definitionId(SIMPLE_CODE);

        assertThatThrownBy(() -> reconciliationService.triggerRun(
                        definitionId, List.of("unauthorised"), "integration-test", new TriggerRunRequest(null, null, null, null)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void fetchLatestRun_returnsEmptySummaryWhenNoRunsExist() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setCode("REGRESSION_" + System.nanoTime());
        definition.setName("Regression Coverage");
        definition.setDescription("Created for regression testing");
        definition.setMakerCheckerEnabled(false);
        definition = definitionRepository.save(definition);

        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setLdapGroupDn("regression-testers");
        entry.setRole(AccessRole.MAKER);
        accessControlEntryRepository.save(entry);

        RunDetailDto detail = reconciliationService.fetchLatestRun(
                definition.getId(), List.of("regression-testers"), BreakFilterCriteria.none());

        assertThat(detail.summary().runId()).isNull();
        assertThat(detail.summary().triggerType()).isEqualTo(TriggerType.MANUAL_API);
        assertThat(detail.breaks()).isEmpty();
        assertThat(detail.analytics().totalBreakCount()).isZero();
        assertThat(detail.filters().products()).isEmpty();
    }

    @Test
    void listRuns_returnsRecentSummariesInDescendingOrder() {
        Long definitionId = definitionId(SIMPLE_CODE);

        reconciliationService.triggerRun(
                definitionId,
                groups,
                "integration-test",
                new TriggerRunRequest(TriggerType.MANUAL_API, "first", "first manual run", "first-tester"));
        reconciliationService.triggerRun(
                definitionId,
                groups,
                "integration-test",
                new TriggerRunRequest(TriggerType.MANUAL_API, "second", "second manual run", "second-tester"));

        var summaries = reconciliationService.listRuns(definitionId, groups, 5);

        assertThat(summaries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(summaries.get(0).triggerComments()).isEqualTo("second manual run");
        assertThat(summaries.get(0).triggerCorrelationId()).isEqualTo("second");
        assertThat(summaries.get(1).triggerComments()).isEqualTo("first manual run");

        assertThat(reconciliationService.listRuns(definitionId, groups, 1))
                .singleElement()
                .extracting(summary -> summary.triggerComments())
                .isEqualTo("second manual run");
    }

    private Long definitionId(String code) {
        ReconciliationDefinition definition = definitionRepository
                .findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing definition " + code));
        return definition.getId();
    }

    private void ensureSimpleDefinition() {
        ReconciliationDefinition definition = definitionRepository.findByCode(SIMPLE_CODE).orElseGet(() -> createDefinition());
        ensureAccessControl(definition);
        ensureSourceRecords(definition);
    }

    private ReconciliationDefinition createDefinition() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setCode(SIMPLE_CODE);
        definition.setName("Cash vs GL (Integration)");
        definition.setDescription("Seeded for integration testing");
        definition.setMakerCheckerEnabled(false);

        definition.getFields().add(field(definition, "transactionId", FieldRole.KEY));
        definition.getFields().add(field(definition, "amount", FieldRole.COMPARE));
        definition.getFields().add(field(definition, "currency", FieldRole.COMPARE));
        definition.getFields().add(field(definition, "tradeDate", FieldRole.COMPARE));
        definition.getFields().add(field(definition, "product", FieldRole.PRODUCT));
        definition.getFields().add(field(definition, "subProduct", FieldRole.SUB_PRODUCT));
        definition.getFields().add(field(definition, "entityName", FieldRole.ENTITY));

        ReportTemplate template = new ReportTemplate();
        template.setDefinition(definition);
        template.setName("Integration Template");
        template.setDescription("Auto-generated for integration tests");
        template.setIncludeMatched(true);
        template.setIncludeMismatched(true);
        template.setIncludeMissing(true);
        template.setHighlightDifferences(true);

        template.getColumns().add(column(template, "Trade ID (A)", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Trade ID (B)", ReportColumnSource.SOURCE_B, "transactionId", 2, true));
        template.getColumns().add(column(template, "Amount (A)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Amount (B)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Entity", ReportColumnSource.SOURCE_A, "entityName", 5, false));
        template.getColumns().add(column(template, "Break", ReportColumnSource.BREAK_METADATA, "status", 6, false));

        definition.getReportTemplates().add(template);
        return definitionRepository.save(definition);
    }

    private void ensureAccessControl(ReconciliationDefinition definition) {
        List<AccessControlEntry> existing = accessControlEntryRepository
                .findByDefinitionAndLdapGroupDnIn(definition, groups);
        List<AccessControlEntry> entriesToCreate = new ArrayList<>();

        if (!hasEntry(existing, "recon-makers", "US")) {
            entriesToCreate.add(accessEntry(definition, "recon-makers", AccessRole.MAKER, "US"));
        }
        if (!hasEntry(existing, "recon-checkers", "US")) {
            entriesToCreate.add(accessEntry(definition, "recon-checkers", AccessRole.CHECKER, "US"));
        }
        if (!hasEntry(existing, "recon-checkers", "EU")) {
            entriesToCreate.add(accessEntry(definition, "recon-checkers", AccessRole.CHECKER, "EU"));
        }

        if (!entriesToCreate.isEmpty()) {
            accessControlEntryRepository.saveAll(entriesToCreate);
        }
    }

    private boolean hasEntry(List<AccessControlEntry> entries, String group, String entity) {
        return entries.stream()
                .anyMatch(entry -> group.equals(entry.getLdapGroupDn())
                        && java.util.Objects.equals(entity, entry.getEntityName()));
    }

    private void ensureSourceRecords(ReconciliationDefinition definition) {
        if (sourceRecordARepository.findByDefinitionAndTransactionId(definition, "CASH-1001").isPresent()) {
            return;
        }
        List<SourceRecordA> sourceAs = List.of(
                sourceA(definition, "CASH-1001", "1000.00", "USD", "US"),
                sourceA(definition, "CASH-1002", "500.50", "EUR", "EU"),
                sourceA(definition, "CASH-1003", "200.00", "USD", "US"));
        sourceRecordARepository.saveAll(sourceAs);

        List<SourceRecordB> sourceBs = List.of(
                sourceB(definition, "CASH-1001", "1000.00", "USD", "US"),
                sourceB(definition, "CASH-1002", "505.50", "EUR", "EU"),
                sourceB(definition, "CASH-1004", "310.10", "USD", "US"));
        sourceRecordBRepository.saveAll(sourceBs);
    }

    private ReconciliationField field(ReconciliationDefinition definition, String name, FieldRole role) {
        ReconciliationField field = new ReconciliationField();
        field.setDefinition(definition);
        field.setSourceField(name);
        field.setDisplayName(name);
        field.setRole(role);
        field.setDataType(switch (name) {
            case "amount" -> FieldDataType.DECIMAL;
            case "tradeDate" -> FieldDataType.DATE;
            default -> FieldDataType.STRING;
        });
        field.setComparisonLogic(switch (name) {
            case "tradeDate" -> ComparisonLogic.DATE_ONLY;
            default -> ComparisonLogic.EXACT_MATCH;
        });
        return field;
    }

    private ReportColumn column(
            ReportTemplate template, String header, ReportColumnSource source, String field, int order, boolean highlight) {
        ReportColumn column = new ReportColumn();
        column.setTemplate(template);
        column.setHeader(header);
        column.setSource(source);
        column.setSourceField(field);
        column.setDisplayOrder(order);
        column.setHighlightDifferences(highlight);
        return column;
    }

    private AccessControlEntry accessEntry(
            ReconciliationDefinition definition, String group, AccessRole role, String entity) {
        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setLdapGroupDn(group);
        entry.setRole(role);
        entry.setProduct("Payments");
        entry.setSubProduct("Wire");
        entry.setEntityName(entity);
        return entry;
    }

    private SourceRecordA sourceA(
            ReconciliationDefinition definition, String tradeId, String amount, String currency, String entity) {
        SourceRecordA record = new SourceRecordA();
        record.setDefinition(definition);
        record.setTransactionId(tradeId);
        record.setAmount(new BigDecimal(amount));
        record.setCurrency(currency);
        record.setTradeDate(LocalDate.parse("2024-03-18"));
        record.setProduct("Payments");
        record.setSubProduct("Wire");
        record.setEntityName(entity);
        record.setQuantity(new BigDecimal("1.00"));
        return record;
    }

    private SourceRecordB sourceB(
            ReconciliationDefinition definition, String tradeId, String amount, String currency, String entity) {
        SourceRecordB record = new SourceRecordB();
        record.setDefinition(definition);
        record.setTransactionId(tradeId);
        record.setAmount(new BigDecimal(amount));
        record.setCurrency(currency);
        record.setTradeDate(LocalDate.parse("2024-03-18"));
        record.setProduct("Payments");
        record.setSubProduct("Wire");
        record.setEntityName(entity);
        record.setQuantity(new BigDecimal("1.00"));
        return record;
    }
}
