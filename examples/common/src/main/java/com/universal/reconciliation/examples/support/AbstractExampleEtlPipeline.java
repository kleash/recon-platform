package com.universal.reconciliation.examples.support;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * Shared utilities for the standalone reconciliation example pipelines.
 */
public abstract class AbstractExampleEtlPipeline {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ReconciliationDefinitionRepository definitionRepository;
    protected final AccessControlEntryRepository accessControlEntryRepository;
    protected final SourceRecordARepository sourceRecordARepository;
    protected final SourceRecordBRepository sourceRecordBRepository;

    protected AbstractExampleEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceRecordARepository sourceRecordARepository,
            SourceRecordBRepository sourceRecordBRepository) {
        this.definitionRepository = definitionRepository;
        this.accessControlEntryRepository = accessControlEntryRepository;
        this.sourceRecordARepository = sourceRecordARepository;
        this.sourceRecordBRepository = sourceRecordBRepository;
    }

    protected boolean definitionExists(String code) {
        return definitionRepository.findByCode(code).isPresent();
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

    protected ReconciliationField field(
            ReconciliationDefinition definition,
            String sourceField,
            String displayName,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            BigDecimal threshold) {
        ReconciliationField field = new ReconciliationField();
        field.setDefinition(definition);
        field.setSourceField(source(sourceField));
        field.setDisplayName(displayName);
        field.setRole(role);
        field.setDataType(dataType);
        field.setComparisonLogic(logic);
        field.setThresholdPercentage(threshold);
        return field;
    }

    private String source(String value) {
        return value;
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

    protected BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    protected LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    protected List<Map<String, String>> readCsv(String resourcePath) {
        Resource resource = resolveResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            String[] headers = headerLine.split(",");
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                rows.add(mapRow(headers, line.split(",")));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read ETL resource " + resourcePath, ex);
        }
    }

    protected List<Map<String, String>> readExcel(String resourcePath) {
        try (InputStream inputStream = openExcelStream(resourcePath);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null || sheet.getLastRowNum() == 0) {
                return List.of();
            }
            Row headerRow = sheet.getRow(0);
            int columnCount = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                headers.add(headerRow.getCell(i).getStringCellValue());
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String[] values = new String[columnCount];
                for (int c = 0; c < columnCount; c++) {
                    values[c] = row.getCell(c) != null ? row.getCell(c).toString() : null;
                }
                rows.add(mapRow(headers.toArray(String[]::new), values));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read Excel resource " + resourcePath, ex);
        }
    }

    private InputStream openExcelStream(String resourcePath) throws IOException {
        Resource resource = new ClassPathResource(resourcePath);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        if (resourcePath.endsWith(".xlsx")) {
            Resource base64Resource = new ClassPathResource(resourcePath + ".b64");
            if (base64Resource.exists()) {
                try (InputStream base64Stream = base64Resource.getInputStream()) {
                    String encoded = StreamUtils.copyToString(base64Stream, StandardCharsets.UTF_8);
                    byte[] decoded = Base64.getMimeDecoder().decode(encoded);
                    return new ByteArrayInputStream(decoded);
                }
            }
        }
        throw new IllegalStateException("Missing ETL resource: " + resourcePath);
    }

    private Resource resolveResource(String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing ETL resource: " + resourcePath);
        }
        return resource;
    }

    private Map<String, String> mapRow(String[] headers, String[] values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim();
            String raw = i < values.length && values[i] != null ? values[i].trim() : null;
            if (StringUtils.hasText(raw)) {
                row.put(header, raw.replace("\r", ""));
            } else {
                row.put(header, null);
            }
        }
        return row;
    }
}
