package com.universal.reconciliation.examples.custodian;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.examples.support.AbstractExampleEtlPipeline;
import com.universal.reconciliation.etl.EtlPipeline;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complex ETL sample covering multi-custodian trade reconciliation with automated cutoffs.
 */
@Component
public class CustodianTradeEtlPipeline extends AbstractExampleEtlPipeline implements EtlPipeline {

    private static final String DEFINITION_CODE = "CUSTODIAN_TRADE_COMPLEX";
    private static final List<String> CUSTODIANS = List.of("ALPHA_BANK", "BETA_TRUST", "OMEGA_CLEAR");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 3, 18);

    private final CustodianTradeScheduler scheduler;
    private final ScenarioClock clock;

    public CustodianTradeEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceRecordARepository sourceRecordARepository,
            SourceRecordBRepository sourceRecordBRepository,
            CustodianTradeScheduler scheduler,
            ScenarioClock clock) {
        super(definitionRepository, accessControlEntryRepository, sourceRecordARepository, sourceRecordBRepository);
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "Custodian Trade Reconciliation";
    }

    @Override
    @Transactional
    public void run() {
        if (definitionExists(DEFINITION_CODE)) {
            log.debug("Skipping {} ETL because the definition already exists", DEFINITION_CODE);
            return;
        }

        ReconciliationDefinition definition = definition(
                DEFINITION_CODE,
                "Global Custodian Trade Reconciliation",
                "Illustrates a multi-file workflow with automated cutoffs and report scheduling.",
                true);

        registerFields(definition);
        configureReportTemplate(definition);
        definitionRepository.save(definition);

        List<AccessControlEntry> entries = List.of(
                entry(definition, "recon-makers", AccessRole.MAKER, "Global Markets", "Equities", "US"),
                entry(definition, "recon-checkers", AccessRole.CHECKER, "Global Markets", "Equities", "US"),
                entry(definition, "recon-ops", AccessRole.VIEWER, "Global Markets", "Equities", "US"));
        accessControlEntryRepository.saveAll(entries);

        scheduler.configure(definition, CUSTODIANS);

        runMorningCutoff(definition);
        runEveningCutoff(definition);

        clock.advanceTo(BUSINESS_DATE.plusDays(1), LocalTime.of(2, 5));
        scheduler.evaluateCurrentInstant();
    }

    private void runMorningCutoff(ReconciliationDefinition definition) {
        clock.advanceTo(BUSINESS_DATE, LocalTime.of(7, 45));
        ingestCustodianFile(definition, "etl/custodian/custodian_alpha_morning.xlsx", "ALPHA_BANK");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "ALPHA_BANK");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(8, 15));
        ingestCustodianFile(definition, "etl/custodian/custodian_beta_morning.xlsx", "BETA_TRUST");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "BETA_TRUST");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(8, 40));
        ingestCustodianFile(definition, "etl/custodian/custodian_omega_morning.xlsx", "OMEGA_CLEAR");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "OMEGA_CLEAR");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(9, 10));
        ingestPlatformFile(definition, "etl/custodian/trading_platform_morning.xlsx");
        scheduler.recordPlatformArrival(BUSINESS_DATE, CutoffCycle.MORNING);

        scheduler.evaluateCurrentInstant();

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(15, 5));
        scheduler.evaluateCurrentInstant();
    }

    private void runEveningCutoff(ReconciliationDefinition definition) {
        clock.advanceTo(BUSINESS_DATE, LocalTime.of(16, 35));
        ingestCustodianFile(definition, "etl/custodian/custodian_alpha_evening.xlsx", "ALPHA_BANK");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "ALPHA_BANK");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(17, 5));
        ingestCustodianFile(definition, "etl/custodian/custodian_beta_evening.xlsx", "BETA_TRUST");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "BETA_TRUST");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(17, 40));
        ingestPlatformFile(definition, "etl/custodian/trading_platform_evening.xlsx");
        scheduler.recordPlatformArrival(BUSINESS_DATE, CutoffCycle.EVENING);

        clock.advanceTo(BUSINESS_DATE, CutoffCycle.EVENING.cutoff());
        scheduler.evaluateCurrentInstant();

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(18, 12));
        ingestCustodianFile(definition, "etl/custodian/custodian_omega_evening.xlsx", "OMEGA_CLEAR");
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "OMEGA_CLEAR");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(21, 10));
        scheduler.evaluateCurrentInstant();
    }

    private void registerFields(ReconciliationDefinition definition) {
        addField(definition, "transactionId", "Trade ID", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "subProduct", "Source", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "amount", "Gross Amount", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "quantity", "Quantity", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "currency", "Currency", FieldRole.COMPARE, FieldDataType.STRING, ComparisonLogic.CASE_INSENSITIVE, null);
        addField(definition, "tradeDate", "Trade Date", FieldRole.COMPARE, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null);
        addField(definition, "product", "Product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "entityName", "Portfolio", FieldRole.ENTITY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "subProduct", "Source", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "amount", "Gross Amount", FieldRole.DISPLAY, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "quantity", "Quantity", FieldRole.DISPLAY, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "currency", "Currency", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "entityName", "Portfolio", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "custodian", "Custodian", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
    }

    private void configureReportTemplate(ReconciliationDefinition definition) {
        ReportTemplate template = template(
                definition,
                "Custodian Trade Health",
                "Automated reporting of custodian versus trading platform variances.",
                true,
                true,
                true,
                true);
        definition.getReportTemplates().add(template);

        template.getColumns().add(column(template, "Trade ID", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Source", ReportColumnSource.SOURCE_A, "subProduct", 2, true));
        template.getColumns().add(column(template, "Gross Amount (A)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Gross Amount (B)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Quantity (A)", ReportColumnSource.SOURCE_A, "quantity", 5, true));
        template.getColumns().add(column(template, "Quantity (B)", ReportColumnSource.SOURCE_B, "quantity", 6, true));
        template.getColumns().add(column(template, "Currency", ReportColumnSource.SOURCE_A, "currency", 7, false));
        template.getColumns().add(column(template, "Portfolio", ReportColumnSource.SOURCE_A, "entityName", 8, false));
        template.getColumns().add(column(template, "Workflow Status", ReportColumnSource.BREAK_METADATA, "status", 9, false));
    }

    private void addField(
            ReconciliationDefinition definition,
            String sourceField,
            String displayName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            java.math.BigDecimal threshold) {
        ReconciliationField field = field(definition, sourceField, displayName, role, dataType, logic, threshold);
        definition.getFields().add(field);
    }

    private void ingestCustodianFile(ReconciliationDefinition definition, String resource, String sourceName) {
        List<Map<String, String>> rows = readExcel(resource);
        List<SourceRecordA> records = rows.stream()
                .map(row -> mapCustodianRow(definition, row, sourceName))
                .toList();
        sourceRecordARepository.saveAll(records);
    }

    private void ingestPlatformFile(ReconciliationDefinition definition, String resource) {
        List<Map<String, String>> rows = readExcel(resource);
        List<SourceRecordB> records = rows.stream()
                .map(row -> mapPlatformRow(definition, row))
                .toList();
        sourceRecordBRepository.saveAll(records);
    }

    private SourceRecordA mapCustodianRow(
            ReconciliationDefinition definition, Map<String, String> row, String sourceName) {
        SourceRecordA record = new SourceRecordA();
        record.setDefinition(definition);
        record.setTransactionId(row.get("trade_id"));
        record.setAmount(decimal(row.get("gross_amount")));
        record.setCurrency(row.get("currency"));
        record.setTradeDate(date(row.get("trade_date")));
        record.setProduct("Global Markets");
        record.setSubProduct(sourceName);
        record.setEntityName(row.get("portfolio"));
        record.setAccountId(row.get("custodian_code"));
        record.setQuantity(decimal(row.get("quantity")));
        record.setMarketValue(decimal(row.get("gross_amount")));
        record.setValuationCurrency(row.get("currency"));
        record.setCustodian(sourceName);
        record.setPortfolioManager(row.get("trader"));
        return record;
    }

    private SourceRecordB mapPlatformRow(ReconciliationDefinition definition, Map<String, String> row) {
        SourceRecordB record = new SourceRecordB();
        record.setDefinition(definition);
        record.setTransactionId(row.get("trade_id"));
        record.setAmount(decimal(row.get("gross_amount")));
        record.setCurrency(row.get("currency"));
        record.setTradeDate(date(row.get("trade_date")));
        record.setProduct("Global Markets");
        record.setSubProduct(row.get("source"));
        record.setEntityName(row.get("book"));
        record.setAccountId(row.get("account"));
        record.setQuantity(decimal(row.get("quantity")));
        record.setMarketValue(decimal(row.get("gross_amount")));
        record.setValuationCurrency(row.get("currency"));
        record.setCustodian(row.get("source"));
        record.setPortfolioManager(row.get("desk_owner"));
        return record;
    }
}
