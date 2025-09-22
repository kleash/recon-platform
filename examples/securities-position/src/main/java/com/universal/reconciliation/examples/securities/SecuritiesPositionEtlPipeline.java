package com.universal.reconciliation.examples.securities;

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
 * Loads a complex maker-checker securities position reconciliation with tolerance-based matching.
 */
@Component
public class SecuritiesPositionEtlPipeline extends AbstractExampleEtlPipeline implements EtlPipeline {

    private static final String DEFINITION_CODE = "SEC_POSITION_COMPLEX";

    public SecuritiesPositionEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceRecordARepository sourceRecordARepository,
            SourceRecordBRepository sourceRecordBRepository) {
        super(definitionRepository, accessControlEntryRepository, sourceRecordARepository, sourceRecordBRepository);
    }

    @Override
    public String name() {
        return "Securities Position (Complex)";
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
                "Global Securities Positions",
                "Complex maker-checker example covering quantity and valuation tolerances.",
                true);

        registerFields(definition);
        configureReportTemplate(definition);
        definitionRepository.save(definition);

        List<AccessControlEntry> entries = List.of(
                entry(definition, "recon-makers", AccessRole.MAKER, "Securities", "Equities", null),
                entry(definition, "recon-checkers", AccessRole.CHECKER, "Securities", "Equities", null));
        accessControlEntryRepository.saveAll(entries);

        List<SourceRecordA> sourceARecords = readCsv("etl/securities/source_a.csv").stream()
                .map(row -> mapSourceA(definition, row))
                .toList();
        sourceRecordARepository.saveAll(sourceARecords);

        List<SourceRecordB> sourceBRecords = readCsv("etl/securities/source_b.csv").stream()
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
        addField(definition, "accountId", "Account", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "isin", "ISIN", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(
                definition,
                "quantity",
                "Quantity",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                BigDecimal.valueOf(0.5));
        addField(
                definition,
                "marketValue",
                "Market Value",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                BigDecimal.valueOf(1.5));
        addField(
                definition,
                "valuationCurrency",
                "Valuation CCY",
                FieldRole.COMPARE,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null);
        addField(
                definition,
                "valuationDate",
                "Valuation Date",
                FieldRole.COMPARE,
                FieldDataType.DATE,
                ComparisonLogic.DATE_ONLY,
                null);
        addField(definition, "product", "Product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(
                definition,
                "subProduct",
                "Desk",
                FieldRole.SUB_PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null);
        addField(
                definition,
                "entityName",
                "Entity",
                FieldRole.ENTITY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null);

        addField(definition, "transactionId", "Position ID", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "accountId", "Account", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "isin", "ISIN", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "quantity", "Quantity", FieldRole.DISPLAY, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "marketValue", "Market Value", FieldRole.DISPLAY, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, null);
        addField(definition, "valuationCurrency", "Valuation CCY", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "valuationDate", "Valuation Date", FieldRole.DISPLAY, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null);
        addField(definition, "custodian", "Custodian", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        addField(definition, "portfolioManager", "Portfolio Manager", FieldRole.DISPLAY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
    }

    private void configureReportTemplate(ReconciliationDefinition definition) {
        ReportTemplate template = template(
                definition,
                "Global Securities Export",
                "Detailed maker-checker export with tolerances and workflow metadata.",
                true,
                true,
                true,
                true);
        definition.getReportTemplates().add(template);

        template.getColumns().add(column(template, "Account", ReportColumnSource.SOURCE_A, "accountId", 1, false));
        template.getColumns().add(column(template, "ISIN", ReportColumnSource.SOURCE_A, "isin", 2, false));
        template.getColumns().add(column(template, "Quantity (A)", ReportColumnSource.SOURCE_A, "quantity", 3, true));
        template.getColumns().add(column(template, "Quantity (B)", ReportColumnSource.SOURCE_B, "quantity", 4, true));
        template.getColumns().add(column(template, "Market Value (A)", ReportColumnSource.SOURCE_A, "marketValue", 5, true));
        template.getColumns().add(column(template, "Market Value (B)", ReportColumnSource.SOURCE_B, "marketValue", 6, true));
        template.getColumns().add(column(template, "Valuation CCY (A)", ReportColumnSource.SOURCE_A, "valuationCurrency", 7, true));
        template.getColumns().add(column(template, "Valuation CCY (B)", ReportColumnSource.SOURCE_B, "valuationCurrency", 8, true));
        template.getColumns().add(column(template, "Valuation Date (A)", ReportColumnSource.SOURCE_A, "valuationDate", 9, true));
        template.getColumns().add(column(template, "Valuation Date (B)", ReportColumnSource.SOURCE_B, "valuationDate", 10, true));
        template.getColumns().add(column(template, "Custodian", ReportColumnSource.SOURCE_A, "custodian", 11, false));
        template.getColumns().add(column(template, "Portfolio Manager", ReportColumnSource.SOURCE_A, "portfolioManager", 12, false));
        template.getColumns().add(column(template, "Workflow Status", ReportColumnSource.BREAK_METADATA, "status", 13, false));
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
        record.setAccountId(row.get("accountId"));
        record.setIsin(row.get("isin"));
        record.setAmount(decimal(row.get("marketValue")));
        record.setQuantity(decimal(row.get("quantity")));
        record.setMarketValue(decimal(row.get("marketValue")));
        record.setCurrency(row.get("valuationCurrency"));
        record.setValuationCurrency(row.get("valuationCurrency"));
        record.setTradeDate(date(row.get("valuationDate")));
        record.setValuationDate(date(row.get("valuationDate")));
        record.setProduct(row.get("product"));
        record.setSubProduct(row.get("subProduct"));
        record.setEntityName(row.get("entityName"));
        record.setCustodian(row.get("custodian"));
        record.setPortfolioManager(row.get("portfolioManager"));
        return record;
    }

    private SourceRecordB mapSourceB(ReconciliationDefinition definition, Map<String, String> row) {
        SourceRecordB record = new SourceRecordB();
        record.setDefinition(definition);
        record.setTransactionId(row.get("transactionId"));
        record.setAccountId(row.get("accountId"));
        record.setIsin(row.get("isin"));
        record.setAmount(decimal(row.get("marketValue")));
        record.setQuantity(decimal(row.get("quantity")));
        record.setMarketValue(decimal(row.get("marketValue")));
        record.setCurrency(row.get("valuationCurrency"));
        record.setValuationCurrency(row.get("valuationCurrency"));
        record.setTradeDate(date(row.get("valuationDate")));
        record.setValuationDate(date(row.get("valuationDate")));
        record.setProduct(row.get("product"));
        record.setSubProduct(row.get("subProduct"));
        record.setEntityName(row.get("entityName"));
        record.setCustodian(row.get("custodian"));
        record.setPortfolioManager(row.get("portfolioManager"));
        return record;
    }
}
