package com.universal.reconciliation.examples.cashgl;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
            AccessControlEntryRepository accessControlEntryRepository,
            SourceRecordARepository sourceRecordARepository,
            SourceRecordBRepository sourceRecordBRepository) {
        super(definitionRepository, accessControlEntryRepository, sourceRecordARepository, sourceRecordBRepository);
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

        registerFields(definition);
        configureReportTemplate(definition);
        definitionRepository.save(definition);

        List<AccessControlEntry> entries = List.of(
                entry(definition, "recon-makers", AccessRole.MAKER, "Payments", "Wire", "US"),
                entry(definition, "recon-makers", AccessRole.MAKER, "Payments", "Wire", "EU"),
                entry(definition, "recon-checkers", AccessRole.CHECKER, "Payments", "Wire", "US"),
                entry(definition, "recon-checkers", AccessRole.CHECKER, "Payments", "Wire", "EU"));
        accessControlEntryRepository.saveAll(entries);

        List<SourceRecordA> sourceARecords = readCsv("etl/cash_gl/source_a.csv").stream()
                .map(row -> mapSourceA(definition, row))
                .toList();
        sourceRecordARepository.saveAll(sourceARecords);

        List<SourceRecordB> sourceBRecords = readCsv("etl/cash_gl/source_b.csv").stream()
                .map(row -> mapSourceB(definition, row))
                .toList();
        sourceRecordBRepository.saveAll(sourceBRecords);

        log.info(
                "Seeded {} with {} source A and {} source B records",
                DEFINITION_CODE,
                sourceARecords.size(),
                sourceBRecords.size());
    }

    private void registerFields(ReconciliationDefinition definition) {
        addField(definition, "transactionId", "Transaction ID", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "amount", "Amount", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "currency", "Currency", FieldRole.COMPARE, FieldDataType.STRING, ComparisonLogic.CASE_INSENSITIVE, null);
        addField(definition, "tradeDate", "Trade Date", FieldRole.COMPARE, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null);
        addField(definition, "product", "Product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "subProduct", "Sub Product", FieldRole.SUB_PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "entityName", "Entity", FieldRole.ENTITY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "transactionId", "Transaction ID", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "amount", "Amount", FieldRole.DISPLAY, FieldDataType.DECIMAL, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "currency", "Currency", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "tradeDate", "Trade Date", FieldRole.DISPLAY, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null);
    }

    private void configureReportTemplate(ReconciliationDefinition definition) {
        ReportTemplate template = template(
                definition,
                "Simple Cash Export",
                "Default export template for the cash vs GL illustration.",
                true,
                true,
                true,
                true);
        definition.getReportTemplates().add(template);

        template.getColumns().add(column(template, "Transaction ID (A)", ReportColumnSource.SOURCE_A, "transactionId", 1, true));
        template.getColumns().add(column(template, "Transaction ID (B)", ReportColumnSource.SOURCE_B, "transactionId", 2, true));
        template.getColumns().add(column(template, "Amount (A)", ReportColumnSource.SOURCE_A, "amount", 3, true));
        template.getColumns().add(column(template, "Amount (B)", ReportColumnSource.SOURCE_B, "amount", 4, true));
        template.getColumns().add(column(template, "Currency (A)", ReportColumnSource.SOURCE_A, "currency", 5, true));
        template.getColumns().add(column(template, "Currency (B)", ReportColumnSource.SOURCE_B, "currency", 6, true));
        template.getColumns().add(column(template, "Trade Date (A)", ReportColumnSource.SOURCE_A, "tradeDate", 7, true));
        template.getColumns().add(column(template, "Trade Date (B)", ReportColumnSource.SOURCE_B, "tradeDate", 8, true));
        template.getColumns().add(column(template, "Break Status", ReportColumnSource.BREAK_METADATA, "status", 9, false));
    }

    private void addField(
            ReconciliationDefinition definition,
            String sourceField,
            String displayName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            BigDecimal threshold) {
        ReconciliationField field = field(definition, sourceField, displayName, role, dataType, logic, threshold);
        definition.getFields().add(field);
    }

    private SourceRecordA mapSourceA(ReconciliationDefinition definition, Map<String, String> row) {
        SourceRecordA record = new SourceRecordA();
        record.setDefinition(definition);
        record.setTransactionId(row.get("transactionId"));
        record.setAmount(decimal(row.get("amount")));
        record.setCurrency(row.get("currency"));
        record.setTradeDate(date(row.get("tradeDate")));
        record.setProduct(row.get("product"));
        record.setSubProduct(row.get("subProduct"));
        record.setEntityName(row.get("entityName"));
        return record;
    }

    private SourceRecordB mapSourceB(ReconciliationDefinition definition, Map<String, String> row) {
        SourceRecordB record = new SourceRecordB();
        record.setDefinition(definition);
        record.setTransactionId(row.get("transactionId"));
        record.setAmount(decimal(row.get("amount")));
        record.setCurrency(row.get("currency"));
        record.setTradeDate(date(row.get("tradeDate")));
        record.setProduct(row.get("product"));
        record.setSubProduct(row.get("subProduct"));
        record.setEntityName(row.get("entityName"));
        return record;
    }
}
