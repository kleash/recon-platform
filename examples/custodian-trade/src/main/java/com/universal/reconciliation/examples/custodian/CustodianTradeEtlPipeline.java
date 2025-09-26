package com.universal.reconciliation.examples.custodian;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import com.universal.reconciliation.examples.support.AbstractExampleEtlPipeline;
import com.universal.reconciliation.etl.EtlPipeline;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.CanonicalFieldRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.ReportTemplateRepository;
import com.universal.reconciliation.service.ingestion.SourceIngestionService;
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
    private static final String CUSTODIAN_SOURCE_CODE = "CUSTODIAN";
    private static final String PLATFORM_SOURCE_CODE = "PLATFORM";
    private static final List<String> CUSTODIANS = List.of("ALPHA_BANK", "BETA_TRUST", "OMEGA_CLEAR");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 3, 18);

    private final CustodianTradeScheduler scheduler;
    private final ScenarioClock clock;

    public CustodianTradeEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            CanonicalFieldRepository canonicalFieldRepository,
            ReportTemplateRepository reportTemplateRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceIngestionService sourceIngestionService,
            CustodianTradeScheduler scheduler,
            ScenarioClock clock) {
        super(
                definitionRepository,
                sourceRepository,
                canonicalFieldRepository,
                reportTemplateRepository,
                accessControlEntryRepository,
                sourceIngestionService);
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
        definition.setOwnedBy("Global Markets Operations");
        definition.setNotes("Demonstrates custodian cutoff logic, scheduler triggers, and report automation.");
        definition.setStatus(ReconciliationLifecycleStatus.PUBLISHED);
        definition.setAutoTriggerEnabled(false);

        ReconciliationSource custodianSource = source(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "Custodian Feeds",
                false,
                IngestionAdapterType.CSV_FILE);
        ReconciliationSource platformSource = source(
                definition,
                PLATFORM_SOURCE_CODE,
                "Trading Platform",
                true,
                IngestionAdapterType.CSV_FILE);
        custodianSource.setDescription("Intraday custodian files arriving across global cutoffs.");
        custodianSource.setConnectionConfig("sftp://custodian-dropbox/trades");
        custodianSource.setArrivalExpectation("Morning 08:30 & Evening 17:30 Eastern");
        custodianSource.setArrivalTimezone("America/New_York");
        custodianSource.setArrivalSlaMinutes(45);
        custodianSource.setAdapterOptions("{\"delimiter\":\",\",\"header\":true}");
        platformSource.setDescription("Trading platform summary export aligned to custodian trades.");
        platformSource.setConnectionConfig("jdbc:postgresql://platform-sim/trades");
        platformSource.setArrivalExpectation("Morning 09:15 & Evening 18:00 Eastern");
        platformSource.setArrivalTimezone("America/New_York");
        platformSource.setArrivalSlaMinutes(30);
        platformSource.setAdapterOptions("{\"delimiter\":\",\",\"header\":true}");

        configureFields(definition, custodianSource, platformSource);
        configureReportTemplate(definition);

        persistDefinition(definition);

        AccessControlEntry maker = entry(definition, "recon-makers", AccessRole.MAKER, "Global Markets", "Equities", "US");
        maker.setNotifyOnIngestionFailure(true);
        maker.setNotificationChannel("gm-makers@universal.example");
        AccessControlEntry checker = entry(definition, "recon-checkers", AccessRole.CHECKER, "Global Markets", "Equities", "US");
        checker.setNotifyOnPublish(true);
        checker.setNotificationChannel("gm-checkers@universal.example");
        AccessControlEntry viewer = entry(definition, "recon-ops", AccessRole.VIEWER, "Global Markets", "Equities", "US");
        viewer.setNotificationChannel("gm-ops@universal.example");
        saveAccessControlEntries(List.of(maker, checker, viewer));

        scheduler.configure(definition, CUSTODIANS);

        runMorningCutoff(definition);
        runEveningCutoff(definition);

        clock.advanceTo(BUSINESS_DATE.plusDays(1), LocalTime.of(2, 5));
        scheduler.evaluateCurrentInstant();
    }

    private void configureFields(
            ReconciliationDefinition definition,
            ReconciliationSource custodianSource,
            ReconciliationSource platformSource) {
        CanonicalField transactionId = canonicalField(
                definition,
                "transactionId",
                "Trade ID",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                1,
                true);
        mapToBothSources(transactionId, custodianSource, platformSource, "trade_id", true);

        CanonicalField sourceField = canonicalField(
                definition,
                "subProduct",
                "Source",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                2,
                true);
        sourceField.setClassifierTag("subProduct");
        mapToBothSources(sourceField, custodianSource, platformSource, "source", true);

        CanonicalField amount = canonicalField(
                definition,
                "amount",
                "Gross Amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                null,
                3,
                true);
        mapToBothSources(amount, custodianSource, platformSource, "gross_amount", true);

        CanonicalField quantity = canonicalField(
                definition,
                "quantity",
                "Quantity",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                null,
                4,
                true);
        mapToBothSources(quantity, custodianSource, platformSource, "quantity", true);

        CanonicalField currency = canonicalField(
                definition,
                "currency",
                "Currency",
                FieldRole.COMPARE,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null,
                5,
                true);
        mapToBothSources(currency, custodianSource, platformSource, "currency", true);

        CanonicalField tradeDate = canonicalField(
                definition,
                "tradeDate",
                "Trade Date",
                FieldRole.COMPARE,
                FieldDataType.DATE,
                ComparisonLogic.DATE_ONLY,
                null,
                6,
                true);
        mapToBothSources(tradeDate, custodianSource, platformSource, "trade_date", true);

        CanonicalField product = canonicalField(
                definition,
                "product",
                "Product",
                FieldRole.PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                7,
                true);
        product.setClassifierTag("product");
        CanonicalFieldMapping productCustodianMapping = mapping(product, custodianSource, "product", true);
        productCustodianMapping.setDefaultValue("Global Markets");
        CanonicalFieldMapping productPlatformMapping = mapping(product, platformSource, "product", true);
        productPlatformMapping.setDefaultValue("Global Markets");

        CanonicalField entity = canonicalField(
                definition,
                "entityName",
                "Portfolio",
                FieldRole.ENTITY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                8,
                true);
        entity.setClassifierTag("entity");
        mapping(entity, custodianSource, "portfolio", true);
        mapping(entity, platformSource, "book", true);

        CanonicalField custodian = canonicalField(
                definition,
                "custodian",
                "Custodian",
                FieldRole.DISPLAY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                9,
                true);
        mapToBothSources(custodian, custodianSource, platformSource, "source", true);
    }

    private void mapToBothSources(
            CanonicalField field,
            ReconciliationSource custodianSource,
            ReconciliationSource platformSource,
            String sourceColumn,
            boolean required) {
        mapping(field, custodianSource, sourceColumn, required);
        mapping(field, platformSource, sourceColumn, required);
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

        template.getColumns().add(column(template, "Trade ID", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Source", ReportColumnSource.SOURCE_A, "subProduct", 2, true));
        template.getColumns().add(column(template, "Gross Amount (Custodian)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Gross Amount (Platform)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Quantity (Custodian)", ReportColumnSource.SOURCE_A, "quantity", 5, true));
        template.getColumns().add(column(template, "Quantity (Platform)", ReportColumnSource.SOURCE_B, "quantity", 6, true));
        template.getColumns().add(column(template, "Currency", ReportColumnSource.SOURCE_A, "currency", 7, false));
        template.getColumns().add(column(template, "Portfolio", ReportColumnSource.SOURCE_A, "entityName", 8, false));
        template.getColumns().add(column(template, "Workflow Status", ReportColumnSource.BREAK_METADATA, "status", 9, false));
    }

    private void runMorningCutoff(ReconciliationDefinition definition) {
        clock.advanceTo(BUSINESS_DATE, LocalTime.of(7, 45));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_alpha_morning.csv",
                Map.of("label", "Alpha Morning"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "ALPHA_BANK");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(8, 15));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_beta_morning.csv",
                Map.of("label", "Beta Morning"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "BETA_TRUST");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(8, 40));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_omega_morning.csv",
                Map.of("label", "Omega Morning"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.MORNING, "OMEGA_CLEAR");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(9, 10));
        ingestCsv(
                definition,
                PLATFORM_SOURCE_CODE,
                "etl/custodian/trading_platform_morning.csv",
                Map.of("label", "Platform Morning"));
        scheduler.recordPlatformArrival(BUSINESS_DATE, CutoffCycle.MORNING);

        scheduler.evaluateCurrentInstant();

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(15, 5));
        scheduler.evaluateCurrentInstant();
    }

    private void runEveningCutoff(ReconciliationDefinition definition) {
        clock.advanceTo(BUSINESS_DATE, LocalTime.of(16, 35));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_alpha_evening.csv",
                Map.of("label", "Alpha Evening"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "ALPHA_BANK");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(17, 5));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_beta_evening.csv",
                Map.of("label", "Beta Evening"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "BETA_TRUST");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(17, 40));
        ingestCsv(
                definition,
                PLATFORM_SOURCE_CODE,
                "etl/custodian/trading_platform_evening.csv",
                Map.of("label", "Platform Evening"));
        scheduler.recordPlatformArrival(BUSINESS_DATE, CutoffCycle.EVENING);

        clock.advanceTo(BUSINESS_DATE, CutoffCycle.EVENING.cutoff());
        scheduler.evaluateCurrentInstant();

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(18, 12));
        ingestCsv(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "etl/custodian/custodian_omega_evening.csv",
                Map.of("label", "Omega Evening"));
        scheduler.recordCustodianArrival(BUSINESS_DATE, CutoffCycle.EVENING, "OMEGA_CLEAR");

        clock.advanceTo(BUSINESS_DATE, LocalTime.of(21, 10));
        scheduler.evaluateCurrentInstant();
    }
}
