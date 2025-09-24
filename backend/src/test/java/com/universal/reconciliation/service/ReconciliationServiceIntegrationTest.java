package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import com.universal.reconciliation.service.ingestion.SourceIngestionService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private ReconciliationSourceRepository sourceRepository;

    @Autowired
    private SourceDataBatchRepository batchRepository;

    @Autowired
    private SourceIngestionService sourceIngestionService;

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
                .allSatisfy(breakItem -> assertThat(breakItem.classifications().get("entity")).isEqualTo("US"));
        assertThat(usBreaks.filters().products()).contains("Payments");
        assertThat(usBreaks.analytics().breaksByStatus()).containsKey(BreakStatus.OPEN.name());

        RunDetailDto euOnly = reconciliationService.fetchLatestRun(
                definitionId,
                groups,
                new BreakFilterCriteria(null, null, "EU", Set.of(BreakStatus.OPEN)));

        assertThat(euOnly.breaks())
                .hasSize(1)
                .first()
                .extracting(b -> b.classifications().get("entity"))
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
                definition.getId(),
                List.of("regression-testers"),
                BreakFilterCriteria.none());

        assertThat(detail.summary().runId()).isNull();
        assertThat(detail.breaks()).isEmpty();
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
        return definitionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing definition " + code))
                .getId();
    }

    private void ensureSimpleDefinition() {
        ReconciliationDefinition definition = definitionRepository
                .findByCode(SIMPLE_CODE)
                .orElseGet(this::createDefinition);
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

        ReconciliationSource cash = source(definition, "CASH", "Cash Ledger", true);
        ReconciliationSource gl = source(definition, "GL", "General Ledger", false);

        canonicalField(
                definition,
                "transactionId",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                true,
                1,
                Map.of(cash, "transactionId", gl, "transactionId"));

        canonicalField(
                definition,
                "amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.EXACT_MATCH,
                null,
                true,
                2,
                Map.of(cash, "amount", gl, "amount"));

        canonicalField(
                definition,
                "currency",
                FieldRole.COMPARE,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null,
                true,
                3,
                Map.of(cash, "currency", gl, "currency"));

        canonicalField(
                definition,
                "tradeDate",
                FieldRole.COMPARE,
                FieldDataType.DATE,
                ComparisonLogic.DATE_ONLY,
                null,
                true,
                4,
                Map.of(cash, "tradeDate", gl, "tradeDate"));

        CanonicalField product = canonicalField(
                definition,
                "product",
                FieldRole.PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                true,
                5,
                Map.of(cash, "product", gl, "product"));
        product.setClassifierTag("product");

        CanonicalField subProduct = canonicalField(
                definition,
                "subProduct",
                FieldRole.SUB_PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                true,
                6,
                Map.of(cash, "subProduct", gl, "subProduct"));
        subProduct.setClassifierTag("subProduct");

        CanonicalField entity = canonicalField(
                definition,
                "entity",
                FieldRole.ENTITY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                true,
                7,
                Map.of(cash, "entity", gl, "entity"));
        entity.setClassifierTag("entity");

        ReportTemplate template = new ReportTemplate();
        template.setDefinition(definition);
        template.setName("Integration Template");
        template.setDescription("Auto-generated for integration tests");
        template.setIncludeMatched(true);
        template.setIncludeMismatched(true);
        template.setIncludeMissing(true);
        template.setHighlightDifferences(true);

        template.getColumns().add(column(template, "Trade ID (CASH)", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Trade ID (GL)", ReportColumnSource.SOURCE_B, "transactionId", 2, true));
        template.getColumns().add(column(template, "Amount (CASH)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Amount (GL)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Entity", ReportColumnSource.BREAK_METADATA, "entity", 5, false));
        template.getColumns().add(column(template, "Break Status", ReportColumnSource.BREAK_METADATA, "status", 6, false));

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
        ReconciliationSource cash = sourceRepository.findByDefinitionAndCode(definition, "CASH").orElseThrow();
        if (batchRepository.findFirstBySourceOrderByIngestedAtDesc(cash).isPresent()) {
            return;
        }
        ingestCsv(definition, "CASH", cashSeed());
        ingestCsv(definition, "GL", glSeed());
    }

    private void ingestCsv(ReconciliationDefinition definition, String sourceCode, String csv) {
        IngestionAdapterRequest request = new IngestionAdapterRequest(
                () -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                Map.of("label", "integration-seed"));
        sourceIngestionService.ingest(definition, sourceCode, IngestionAdapterType.CSV_FILE, request);
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

    private ReconciliationSource source(
            ReconciliationDefinition definition, String code, String displayName, boolean anchor) {
        ReconciliationSource source = new ReconciliationSource();
        source.setDefinition(definition);
        source.setCode(code);
        source.setDisplayName(displayName);
        source.setAnchor(anchor);
        source.setAdapterType(IngestionAdapterType.CSV_FILE);
        source.setFieldMappings(new LinkedHashSet<>());
        definition.getSources().add(source);
        return source;
    }

    private CanonicalField canonicalField(
            ReconciliationDefinition definition,
            String canonicalName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            BigDecimal threshold,
            boolean required,
            int order,
            Map<ReconciliationSource, String> mappings) {
        CanonicalField field = new CanonicalField();
        field.setDefinition(definition);
        field.setCanonicalName(canonicalName);
        field.setDisplayName(canonicalName);
        field.setRole(role);
        field.setDataType(dataType);
        field.setComparisonLogic(logic);
        field.setThresholdPercentage(threshold);
        field.setRequired(required);
        field.setDisplayOrder(order);
        field.setMappings(new LinkedHashSet<>());
        definition.getCanonicalFields().add(field);

        mappings.forEach((source, column) -> {
            CanonicalFieldMapping mapping = new CanonicalFieldMapping();
            mapping.setCanonicalField(field);
            mapping.setSource(source);
            mapping.setSourceColumn(column);
            mapping.setRequired(required);
            mapping.setOrdinalPosition(order);
            field.getMappings().add(mapping);
            source.getFieldMappings().add(mapping);
        });
        return field;
    }

    private String cashSeed() {
        return String.join(
                "\n",
                "transactionId,amount,currency,tradeDate,product,subProduct,entity",
                "CASH-1001,1000.00,USD,2024-01-15,Payments,Wire,US",
                "CASH-1002,500.50,EUR,2024-01-15,Payments,Wire,EU",
                "CASH-1003,200.00,USD,2024-01-15,Payments,Wire,US");
    }

    private String glSeed() {
        return String.join(
                "\n",
                "transactionId,amount,currency,tradeDate,product,subProduct,entity",
                "CASH-1001,1000.00,USD,2024-01-15,Payments,Wire,US",
                "CASH-1002,505.50,EUR,2024-01-15,Payments,Wire,EU",
                "CASH-1004,310.10,USD,2024-01-15,Payments,Wire,US");
    }
}
