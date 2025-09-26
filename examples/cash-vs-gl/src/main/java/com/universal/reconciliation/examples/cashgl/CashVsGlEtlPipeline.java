package com.universal.reconciliation.examples.cashgl;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.CanonicalField;
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
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a simple cash versus general ledger reconciliation definition with
 * representative source data.
 */
@Component
public class CashVsGlEtlPipeline extends AbstractExampleEtlPipeline implements EtlPipeline {

    private static final String DEFINITION_CODE = "CASH_VS_GL_SIMPLE";

    public CashVsGlEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            CanonicalFieldRepository canonicalFieldRepository,
            ReportTemplateRepository reportTemplateRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceIngestionService sourceIngestionService) {
        super(
                definitionRepository,
                sourceRepository,
                canonicalFieldRepository,
                reportTemplateRepository,
                accessControlEntryRepository,
                sourceIngestionService);
    }

    @Override
    public String name() {
        return "Cash vs General Ledger (Simple)";
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
                "Cash vs General Ledger (Simple)",
                "Illustrative single-ledger reconciliation without maker-checker workflow.",
                false);
        definition.setOwnedBy("Treasury Operations");
        definition.setNotes("Seeded via cash vs GL example pipeline for demo environments.");
        definition.setStatus(ReconciliationLifecycleStatus.PUBLISHED);
        definition.setAutoTriggerEnabled(true);
        definition.setAutoTriggerCron("0 7 * * MON-FRI");
        definition.setAutoTriggerTimezone("America/New_York");
        definition.setAutoTriggerGraceMinutes(20);

        ReconciliationSource cashSource = source(
                definition, "CASH", "Cash Ledger", true, IngestionAdapterType.CSV_FILE);
        ReconciliationSource glSource = source(
                definition, "GL", "General Ledger", false, IngestionAdapterType.CSV_FILE);
        cashSource.setDescription("Daily bank statement feed used as the anchor dataset.");
        cashSource.setConnectionConfig("s3://demo-data/cash-ledger");
        cashSource.setArrivalExpectation("Weekdays by 06:30 Eastern");
        cashSource.setArrivalTimezone("America/New_York");
        cashSource.setArrivalSlaMinutes(30);
        cashSource.setAdapterOptions("{\"delimiter\":\",\"}");
        glSource.setDescription("ERP general ledger export aligned to the cash view.");
        glSource.setConnectionConfig("jdbc:postgresql://ledger-sim/gl");
        glSource.setArrivalExpectation("Weekdays by 06:45 Eastern");
        glSource.setArrivalTimezone("America/New_York");
        glSource.setArrivalSlaMinutes(45);
        glSource.setAdapterOptions("{\"delimiter\":\",\"}");

        CanonicalField transactionId = canonicalField(
                definition,
                "transactionId",
                "Transaction ID",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                1,
                true);
        mapping(transactionId, cashSource, "transactionId", true);
        mapping(transactionId, glSource, "transactionId", true);

        CanonicalField amount = canonicalField(
                definition,
                "amount",
                "Amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.EXACT_MATCH,
                null,
                2,
                true);
        mapping(amount, cashSource, "amount", true);
        mapping(amount, glSource, "amount", true);

        CanonicalField currency = canonicalField(
                definition,
                "currency",
                "Currency",
                FieldRole.COMPARE,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null,
                3,
                true);
        mapping(currency, cashSource, "currency", true);
        mapping(currency, glSource, "currency", true);

        CanonicalField tradeDate = canonicalField(
                definition,
                "tradeDate",
                "Trade Date",
                FieldRole.COMPARE,
                FieldDataType.DATE,
                ComparisonLogic.DATE_ONLY,
                null,
                4,
                true);
        mapping(tradeDate, cashSource, "tradeDate", true);
        mapping(tradeDate, glSource, "tradeDate", true);

        CanonicalField product = canonicalField(
                definition,
                "product",
                "Product",
                FieldRole.PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                5,
                true);
        product.setClassifierTag("product");
        mapping(product, cashSource, "product", true);
        mapping(product, glSource, "product", true);

        CanonicalField subProduct = canonicalField(
                definition,
                "subProduct",
                "Sub Product",
                FieldRole.SUB_PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                6,
                true);
        subProduct.setClassifierTag("subProduct");
        mapping(subProduct, cashSource, "subProduct", true);
        mapping(subProduct, glSource, "subProduct", true);

        CanonicalField entity = canonicalField(
                definition,
                "entity",
                "Entity",
                FieldRole.ENTITY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                7,
                true);
        entity.setClassifierTag("entity");
        mapping(entity, cashSource, "entityName", true);
        mapping(entity, glSource, "entityName", true);

        CanonicalField quantity = canonicalField(
                definition,
                "quantity",
                "Quantity",
                FieldRole.DISPLAY,
                FieldDataType.DECIMAL,
                ComparisonLogic.EXACT_MATCH,
                BigDecimal.ZERO,
                8,
                false);
        mapping(quantity, cashSource, "quantity", false);
        mapping(quantity, glSource, "quantity", false);

        ReportTemplate template = template(
                definition,
                "Simple Cash Export",
                "Default export template for the cash vs GL illustration.",
                true,
                true,
                true,
                true);
        template.getColumns().add(column(template, "Transaction ID (cash)", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Transaction ID (gl)", ReportColumnSource.SOURCE_B, "transactionId", 2, true));
        template.getColumns().add(column(template, "Amount (cash)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Amount (gl)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Currency (cash)", ReportColumnSource.SOURCE_A, "currency", 5, true));
        template.getColumns().add(column(template, "Currency (gl)", ReportColumnSource.SOURCE_B, "currency", 6, true));
        template.getColumns().add(column(template, "Trade Date (cash)", ReportColumnSource.SOURCE_A, "tradeDate", 7, true));
        template.getColumns().add(column(template, "Trade Date (gl)", ReportColumnSource.SOURCE_B, "tradeDate", 8, true));
        template.getColumns().add(column(template, "Break Status", ReportColumnSource.BREAK_METADATA, "status", 9, false));

        persistDefinition(definition);

        AccessControlEntry makerUs = entry(definition, "recon-makers", AccessRole.MAKER, "Payments", "Wire", "US");
        makerUs.setNotifyOnIngestionFailure(true);
        makerUs.setNotificationChannel("treasury-makers@universal.example");
        AccessControlEntry makerEu = entry(definition, "recon-makers", AccessRole.MAKER, "Payments", "Wire", "EU");
        makerEu.setNotifyOnIngestionFailure(true);
        makerEu.setNotificationChannel("treasury-makers@universal.example");
        AccessControlEntry checkerUs = entry(definition, "recon-checkers", AccessRole.CHECKER, "Payments", "Wire", "US");
        checkerUs.setNotifyOnPublish(true);
        checkerUs.setNotificationChannel("treasury-checkers@universal.example");
        AccessControlEntry checkerEu = entry(definition, "recon-checkers", AccessRole.CHECKER, "Payments", "Wire", "EU");
        checkerEu.setNotifyOnPublish(true);
        checkerEu.setNotificationChannel("treasury-checkers@universal.example");

        saveAccessControlEntries(List.of(makerUs, makerEu, checkerUs, checkerEu));

        ingestCsv(definition, cashSource.getCode(), "etl/cash_gl/source_a.csv");
        ingestCsv(definition, glSource.getCode(), "etl/cash_gl/source_b.csv");

        log.info("Seeded {} via dynamic metadata-driven pipeline", DEFINITION_CODE);
    }
}
