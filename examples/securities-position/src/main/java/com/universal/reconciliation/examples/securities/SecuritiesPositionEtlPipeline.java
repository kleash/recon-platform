package com.universal.reconciliation.examples.securities;

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
 * Loads a complex maker-checker securities position reconciliation using the
 * dynamic canonical field model.
 */
@Component
public class SecuritiesPositionEtlPipeline extends AbstractExampleEtlPipeline implements EtlPipeline {

    private static final String DEFINITION_CODE = "SEC_POSITION_COMPLEX";
    private static final String CUSTODIAN_SOURCE_CODE = "CUSTODIAN";
    private static final String PORTFOLIO_SOURCE_CODE = "PORTFOLIO";

    public SecuritiesPositionEtlPipeline(
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

        ReconciliationSource custodianSource = source(
                definition,
                CUSTODIAN_SOURCE_CODE,
                "Global Custodians",
                true,
                IngestionAdapterType.CSV_FILE);
        ReconciliationSource portfolioSource = source(
                definition,
                PORTFOLIO_SOURCE_CODE,
                "Portfolio Accounting",
                false,
                IngestionAdapterType.CSV_FILE);

        configureFields(definition, custodianSource, portfolioSource);

        ReportTemplate template = template(
                definition,
                "Global Securities Export",
                "Detailed maker-checker export with tolerances and workflow metadata.",
                true,
                true,
                true,
                true);
        template.getColumns().add(column(template, "Account", ReportColumnSource.SOURCE_A, "accountId", 1, false));
        template.getColumns().add(column(template, "ISIN", ReportColumnSource.SOURCE_A, "isin", 2, false));
        template.getColumns().add(column(template, "Quantity (Custodian)", ReportColumnSource.SOURCE_A, "quantity", 3, true));
        template.getColumns().add(column(template, "Quantity (Portfolio)", ReportColumnSource.SOURCE_B, "quantity", 4, true));
        template.getColumns().add(column(template, "Market Value (Custodian)", ReportColumnSource.SOURCE_A, "marketValue", 5, true));
        template.getColumns().add(column(template, "Market Value (Portfolio)", ReportColumnSource.SOURCE_B, "marketValue", 6, true));
        template.getColumns().add(column(template, "Valuation CCY (Custodian)", ReportColumnSource.SOURCE_A, "valuationCurrency", 7, true));
        template.getColumns().add(column(template, "Valuation CCY (Portfolio)", ReportColumnSource.SOURCE_B, "valuationCurrency", 8, true));
        template.getColumns().add(column(template, "Valuation Date (Custodian)", ReportColumnSource.SOURCE_A, "valuationDate", 9, true));
        template.getColumns().add(column(template, "Valuation Date (Portfolio)", ReportColumnSource.SOURCE_B, "valuationDate", 10, true));
        template.getColumns().add(column(template, "Custodian", ReportColumnSource.SOURCE_A, "custodian", 11, false));
        template.getColumns().add(column(template, "Portfolio Manager", ReportColumnSource.SOURCE_A, "portfolioManager", 12, false));
        template.getColumns().add(column(template, "Workflow Status", ReportColumnSource.BREAK_METADATA, "status", 13, false));

        persistDefinition(definition);

        saveAccessControlEntries(List.of(
                entry(definition, "recon-makers", AccessRole.MAKER, "Securities", "Equities", null),
                entry(definition, "recon-checkers", AccessRole.CHECKER, "Securities", "Equities", null)));

        ingestCsv(definition, custodianSource.getCode(), "etl/securities/source_a.csv");
        ingestCsv(definition, portfolioSource.getCode(), "etl/securities/source_b.csv");

        log.info("Seeded {} via dynamic metadata-driven pipeline", DEFINITION_CODE);
    }

    private void configureFields(
            ReconciliationDefinition definition,
            ReconciliationSource custodianSource,
            ReconciliationSource portfolioSource) {
        CanonicalField transactionId = canonicalField(
                definition,
                "transactionId",
                "Position ID",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                1,
                true);
        mapToBothSources(transactionId, custodianSource, portfolioSource, "transactionId", true);

        CanonicalField accountId = canonicalField(
                definition,
                "accountId",
                "Account",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                2,
                true);
        mapToBothSources(accountId, custodianSource, portfolioSource, "accountId", true);

        CanonicalField isin = canonicalField(
                definition,
                "isin",
                "ISIN",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                3,
                true);
        mapToBothSources(isin, custodianSource, portfolioSource, "isin", true);

        CanonicalField quantity = canonicalField(
                definition,
                "quantity",
                "Quantity",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                BigDecimal.valueOf(0.5),
                4,
                true);
        mapToBothSources(quantity, custodianSource, portfolioSource, "quantity", true);

        CanonicalField marketValue = canonicalField(
                definition,
                "marketValue",
                "Market Value",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                BigDecimal.valueOf(1.5),
                5,
                true);
        mapToBothSources(marketValue, custodianSource, portfolioSource, "marketValue", true);

        CanonicalField valuationCurrency = canonicalField(
                definition,
                "valuationCurrency",
                "Valuation CCY",
                FieldRole.COMPARE,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null,
                6,
                true);
        mapToBothSources(valuationCurrency, custodianSource, portfolioSource, "valuationCurrency", true);

        CanonicalField valuationDate = canonicalField(
                definition,
                "valuationDate",
                "Valuation Date",
                FieldRole.COMPARE,
                FieldDataType.DATE,
                ComparisonLogic.DATE_ONLY,
                null,
                7,
                true);
        mapToBothSources(valuationDate, custodianSource, portfolioSource, "valuationDate", true);

        CanonicalField product = canonicalField(
                definition,
                "product",
                "Product",
                FieldRole.PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                8,
                true);
        product.setClassifierTag("product");
        mapToBothSources(product, custodianSource, portfolioSource, "product", true);

        CanonicalField subProduct = canonicalField(
                definition,
                "subProduct",
                "Desk",
                FieldRole.SUB_PRODUCT,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                9,
                true);
        subProduct.setClassifierTag("subProduct");
        mapToBothSources(subProduct, custodianSource, portfolioSource, "subProduct", true);

        CanonicalField entity = canonicalField(
                definition,
                "entityName",
                "Entity",
                FieldRole.ENTITY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                10,
                true);
        entity.setClassifierTag("entity");
        mapToBothSources(entity, custodianSource, portfolioSource, "entityName", true);

        CanonicalField custodian = canonicalField(
                definition,
                "custodian",
                "Custodian",
                FieldRole.DISPLAY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                11,
                false);
        mapToBothSources(custodian, custodianSource, portfolioSource, "custodian", false);

        CanonicalField portfolioManager = canonicalField(
                definition,
                "portfolioManager",
                "Portfolio Manager",
                FieldRole.DISPLAY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                12,
                false);
        mapToBothSources(portfolioManager, custodianSource, portfolioSource, "portfolioManager", false);
    }

    private void mapToBothSources(
            CanonicalField field,
            ReconciliationSource custodianSource,
            ReconciliationSource portfolioSource,
            String sourceColumn,
            boolean required) {
        mapping(field, custodianSource, sourceColumn, required);
        mapping(field, portfolioSource, sourceColumn, required);
    }

}
