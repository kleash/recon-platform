package com.universal.reconciliation.examples.support;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.CanonicalFieldRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationSourceRepository;
import com.universal.reconciliation.repository.ReportTemplateRepository;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import com.universal.reconciliation.service.ingestion.SourceIngestionService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

/**
 * Shared utilities for the standalone reconciliation example pipelines under
 * {@code examples/}. The helper methods expose the dynamic metadata model so
 * examples can seed definitions without touching core platform code.
 */
public abstract class AbstractExampleEtlPipeline {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ReconciliationDefinitionRepository definitionRepository;
    protected final ReconciliationSourceRepository sourceRepository;
    protected final CanonicalFieldRepository canonicalFieldRepository;
    protected final ReportTemplateRepository reportTemplateRepository;
    protected final AccessControlEntryRepository accessControlEntryRepository;
    protected final SourceIngestionService sourceIngestionService;

    protected AbstractExampleEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationSourceRepository sourceRepository,
            CanonicalFieldRepository canonicalFieldRepository,
            ReportTemplateRepository reportTemplateRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceIngestionService sourceIngestionService) {
        this.definitionRepository = definitionRepository;
        this.sourceRepository = sourceRepository;
        this.canonicalFieldRepository = canonicalFieldRepository;
        this.reportTemplateRepository = reportTemplateRepository;
        this.accessControlEntryRepository = accessControlEntryRepository;
        this.sourceIngestionService = sourceIngestionService;
    }

    protected boolean definitionExists(String code) {
        return definitionRepository.findByCode(code).isPresent();
    }

    @Transactional
    protected ReconciliationDefinition persistDefinition(ReconciliationDefinition definition) {
        return definitionRepository.save(definition);
    }

    protected ReconciliationDefinition definition(
            String code, String name, String description, boolean makerCheckerEnabled) {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setCode(code);
        definition.setName(name);
        definition.setDescription(description);
        definition.setMakerCheckerEnabled(makerCheckerEnabled);
        return definition;
    }

    protected ReconciliationSource source(
            ReconciliationDefinition definition,
            String code,
            String displayName,
            boolean anchor,
            IngestionAdapterType adapterType) {
        ReconciliationSource source = new ReconciliationSource();
        source.setDefinition(definition);
        source.setCode(code);
        source.setDisplayName(displayName);
        source.setAnchor(anchor);
        source.setAdapterType(adapterType);
        source.setCreatedAt(Instant.now());
        source.setUpdatedAt(Instant.now());
        definition.getSources().add(source);
        return source;
    }

    protected CanonicalField canonicalField(
            ReconciliationDefinition definition,
            String canonicalName,
            String displayName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic comparisonLogic,
            BigDecimal threshold,
            Integer displayOrder,
            boolean required) {
        CanonicalField field = new CanonicalField();
        field.setDefinition(definition);
        field.setCanonicalName(canonicalName);
        field.setDisplayName(displayName);
        field.setRole(role);
        field.setDataType(dataType);
        field.setComparisonLogic(comparisonLogic);
        field.setThresholdPercentage(threshold);
        field.setDisplayOrder(displayOrder);
        field.setRequired(required);
        field.setCreatedAt(Instant.now());
        field.setUpdatedAt(Instant.now());
        definition.getCanonicalFields().add(field);
        return field;
    }

    protected CanonicalFieldMapping mapping(
            CanonicalField field,
            ReconciliationSource source,
            String sourceColumn,
            boolean required) {
        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setCanonicalField(field);
        mapping.setSource(source);
        mapping.setSourceColumn(sourceColumn);
        mapping.setRequired(required);
        mapping.setCreatedAt(Instant.now());
        mapping.setUpdatedAt(Instant.now());
        field.getMappings().add(mapping);
        source.getFieldMappings().add(mapping);
        return mapping;
    }

    protected ReportTemplate template(
            ReconciliationDefinition definition,
            String name,
            String description,
            boolean includeMatched,
            boolean includeMismatched,
            boolean includeMissing,
            boolean highlightDifferences) {
        ReportTemplate template = new ReportTemplate();
        template.setDefinition(definition);
        template.setName(name);
        template.setDescription(description);
        template.setIncludeMatched(includeMatched);
        template.setIncludeMismatched(includeMismatched);
        template.setIncludeMissing(includeMissing);
        template.setHighlightDifferences(highlightDifferences);
        definition.getReportTemplates().add(template);
        return template;
    }

    protected ReportColumn column(
            ReportTemplate template,
            String header,
            ReportColumnSource source,
            String sourceField,
            int order,
            boolean highlight) {
        ReportColumn column = new ReportColumn();
        column.setTemplate(template);
        column.setHeader(header);
        column.setSource(source);
        column.setSourceField(sourceField);
        column.setDisplayOrder(order);
        column.setHighlightDifferences(highlight);
        template.getColumns().add(column);
        return column;
    }

    protected AccessControlEntry entry(
            ReconciliationDefinition definition,
            String ldapGroup,
            AccessRole role,
            String product,
            String subProduct,
            String entity) {
        AccessControlEntry entry = new AccessControlEntry();
        entry.setDefinition(definition);
        entry.setLdapGroupDn(ldapGroup);
        entry.setRole(role);
        entry.setProduct(product);
        entry.setSubProduct(subProduct);
        entry.setEntityName(entity);
        return entry;
    }

    protected void saveAccessControlEntries(List<AccessControlEntry> entries) {
        accessControlEntryRepository.saveAll(entries);
    }

    protected void ingestCsv(ReconciliationDefinition definition, String sourceCode, String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Missing ETL resource: " + resourcePath);
        }
        IngestionAdapterRequest request = new IngestionAdapterRequest(
                () -> openResource(resource),
                Map.of("charset", StandardCharsets.UTF_8));
        sourceIngestionService.ingest(definition, sourceCode, IngestionAdapterType.CSV_FILE, request);
    }

    private InputStream openResource(Resource resource) {
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open ETL resource " + resource.getFilename(), e);
        }
    }

    protected String readResourceAsString(String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read ETL resource " + resourcePath, e);
        }
    }

    protected void registerReportTemplates(ReconciliationDefinition definition) {
        if (!definition.getReportTemplates().isEmpty()) {
            reportTemplateRepository.saveAll(definition.getReportTemplates());
        }
    }

    protected void persistSources(ReconciliationDefinition definition) {
        if (!definition.getSources().isEmpty()) {
            sourceRepository.saveAll(definition.getSources());
        }
    }

    protected void persistCanonicalFields(ReconciliationDefinition definition) {
        if (!definition.getCanonicalFields().isEmpty()) {
            canonicalFieldRepository.saveAll(definition.getCanonicalFields());
        }
    }
}
