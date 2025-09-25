package com.universal.reconciliation.service.admin;

import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReportTemplateRequest;
import com.universal.reconciliation.domain.dto.admin.AdminSourceRequest;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldRole;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Performs domain validations for admin-authored reconciliation definitions before
 * they are persisted.
 */
@Component
public class AdminReconciliationValidator {

    public void validate(AdminReconciliationRequest request) {
        ensureSourcesAreValid(request.sources());
        ensureCanonicalFieldsAreValid(request);
        ensureReportTemplatesAreValid(request.reportTemplates());
        ensureAutoTriggerIsValid(request);
    }

    private void ensureSourcesAreValid(List<AdminSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source must be configured.");
        }
        Set<String> codes = new HashSet<>();
        long anchorCount = 0;
        for (AdminSourceRequest source : sources) {
            String code = normaliseKey(source.code());
            if (!codes.add(code)) {
                throw new IllegalArgumentException("Duplicate source code detected: " + source.code());
            }
            if (source.anchor()) {
                anchorCount++;
            }
            if (source.arrivalSlaMinutes() != null && source.arrivalSlaMinutes() < 0) {
                throw new IllegalArgumentException(
                        "Arrival SLA minutes cannot be negative for source " + source.code());
            }
        }
        if (anchorCount == 0) {
            throw new IllegalArgumentException("At least one source must be flagged as the anchor source.");
        }
        if (anchorCount > 1) {
            throw new IllegalArgumentException("Only a single anchor source can be configured per reconciliation.");
        }
    }

    private void ensureCanonicalFieldsAreValid(AdminReconciliationRequest request) {
        List<AdminCanonicalFieldRequest> fields = request.canonicalFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one canonical field must be defined.");
        }

        Set<String> fieldNames = new HashSet<>();
        boolean keyFieldPresent = false;
        Map<String, AdminSourceRequest> sourcesByCode = request.sources().stream()
                .collect(Collectors.toMap(src -> normaliseKey(src.code()), src -> src));

        for (AdminCanonicalFieldRequest field : fields) {
            String name = normaliseKey(field.canonicalName());
            if (!fieldNames.add(name)) {
                throw new IllegalArgumentException("Duplicate canonical field name detected: " + field.canonicalName());
            }
            if (FieldRole.KEY.equals(field.role())) {
                keyFieldPresent = true;
            }

            validateThreshold(field);
            ensureMappingsValid(field, sourcesByCode);
        }

        if (!keyFieldPresent) {
            throw new IllegalArgumentException("At least one canonical field must be assigned the KEY role.");
        }
    }

    private void validateThreshold(AdminCanonicalFieldRequest field) {
        if (ComparisonLogic.NUMERIC_THRESHOLD.equals(field.comparisonLogic())) {
            BigDecimal threshold = field.thresholdPercentage();
            if (threshold == null) {
                throw new IllegalArgumentException(
                        "Numeric threshold comparison requires a thresholdPercentage for field "
                                + field.canonicalName());
            }
            if (threshold.scale() > 6) {
                throw new IllegalArgumentException(
                        "Threshold percentage cannot have more than 6 decimal places for field "
                                + field.canonicalName());
            }
            if (threshold.signum() < 0) {
                throw new IllegalArgumentException(
                        "Threshold percentage cannot be negative for field " + field.canonicalName());
            }
        } else if (field.thresholdPercentage() != null) {
            throw new IllegalArgumentException(
                    "Threshold percentage supplied for non-numeric comparison on field " + field.canonicalName());
        }
    }

    private void ensureMappingsValid(
            AdminCanonicalFieldRequest field, Map<String, AdminSourceRequest> sourcesByCode) {
        List<AdminCanonicalFieldMappingRequest> mappings = field.mappings();
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Canonical field " + field.canonicalName() + " must define at least one source mapping.");
        }
        Set<String> dedupe = new HashSet<>();
        for (AdminCanonicalFieldMappingRequest mapping : mappings) {
            String sourceCode = mapping.sourceCode();
            if (!sourcesByCode.containsKey(normaliseKey(sourceCode))) {
                throw new IllegalArgumentException(
                        "Mapping references unknown source code " + sourceCode + " for field " + field.canonicalName());
            }
            String key = normaliseKey(field.canonicalName()) + "::" + normaliseKey(sourceCode);
            if (!dedupe.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate mapping for source " + sourceCode + " on field " + field.canonicalName());
            }
            if (!StringUtils.hasText(mapping.sourceColumn())) {
                throw new IllegalArgumentException(
                        "Source column is required for mapping on field " + field.canonicalName());
            }
        }
    }

    private void ensureReportTemplatesAreValid(List<AdminReportTemplateRequest> templates) {
        if (templates == null) {
            return;
        }
        Map<String, AdminReportTemplateRequest> names = new HashMap<>();
        for (AdminReportTemplateRequest template : templates) {
            String key = normaliseKey(template.name());
            AdminReportTemplateRequest existing = names.putIfAbsent(key, template);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate report template name detected: " + template.name());
            }
        }
    }

    private void ensureAutoTriggerIsValid(AdminReconciliationRequest request) {
        if (request.autoTriggerGraceMinutes() != null && request.autoTriggerGraceMinutes() < 0) {
            throw new IllegalArgumentException("Auto-trigger grace minutes cannot be negative.");
        }
        if (request.autoTriggerEnabled()) {
            if (!StringUtils.hasText(request.autoTriggerCron())) {
                throw new IllegalArgumentException(
                        "Auto-trigger cron expression is required when auto-trigger is enabled.");
            }
            if (!StringUtils.hasText(request.autoTriggerTimezone())) {
                throw new IllegalArgumentException(
                        "Auto-trigger timezone is required when auto-trigger is enabled.");
            }
        }
    }

    private String normaliseKey(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}

