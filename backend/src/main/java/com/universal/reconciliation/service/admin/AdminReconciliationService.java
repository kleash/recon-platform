package com.universal.reconciliation.service.admin;

import com.universal.reconciliation.domain.dto.admin.AdminAccessControlEntryDto;
import com.universal.reconciliation.domain.dto.admin.AdminAccessControlEntryRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldDto;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingDto;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldTransformationDto;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldTransformationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldRequest;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionBatchDto;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationDetailDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPageDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPatchRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationSchemaDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.admin.AdminReportColumnDto;
import com.universal.reconciliation.domain.dto.admin.AdminReportColumnRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReportTemplateDto;
import com.universal.reconciliation.domain.dto.admin.AdminReportTemplateRequest;
import com.universal.reconciliation.domain.dto.admin.AdminSourceDto;
import com.universal.reconciliation.domain.dto.admin.AdminSourceRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.ReportColumn;
import com.universal.reconciliation.domain.entity.ReportTemplate;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.service.SystemActivityService;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import com.universal.reconciliation.service.ingestion.SourceIngestionService;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.SourceTransformationPlanMapper;
import com.universal.reconciliation.service.transform.TransformationEvaluationException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Orchestrates administrative CRUD operations for reconciliation definitions.
 */
@Service
@Transactional
public class AdminReconciliationService {

    private final ReconciliationDefinitionRepository definitionRepository;
    private final SystemActivityService systemActivityService;
    private final AdminReconciliationValidator validator;
    private final SourceIngestionService sourceIngestionService;
    private final DataTransformationService transformationService;
    private final SourceTransformationPlanMapper transformationPlanMapper;

    public AdminReconciliationService(
            ReconciliationDefinitionRepository definitionRepository,
            SystemActivityService systemActivityService,
            AdminReconciliationValidator validator,
            SourceIngestionService sourceIngestionService,
            DataTransformationService transformationService,
            SourceTransformationPlanMapper transformationPlanMapper) {
        this.definitionRepository = definitionRepository;
        this.systemActivityService = systemActivityService;
        this.validator = validator;
        this.sourceIngestionService = sourceIngestionService;
        this.transformationService = transformationService;
        this.transformationPlanMapper = transformationPlanMapper;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AdminReconciliationPageDto list(
            ReconciliationLifecycleStatus status,
            String owner,
            Instant updatedAfter,
            Instant updatedBefore,
            String search,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by("updatedAt").descending());
        Specification<ReconciliationDefinition> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (owner != null && !owner.isBlank()) {
            String ownerLike = "%" + owner.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("ownedBy")), ownerLike));
        }
        if (updatedAfter != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedAfter));
        }
        if (updatedBefore != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("updatedAt"), updatedBefore));
        }
        if (search != null && !search.isBlank()) {
            String searchLike = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), searchLike),
                    cb.like(cb.lower(root.get("name")), searchLike),
                    cb.like(cb.lower(root.get("description")), searchLike)));
        }

        Page<ReconciliationDefinition> results = definitionRepository.findAll(spec, pageable);
        List<AdminReconciliationSummaryDto> items = results.stream().map(this::mapSummary).toList();
        return new AdminReconciliationPageDto(
                items,
                results.getTotalElements(),
                results.getTotalPages(),
                results.getNumber(),
                results.getSize());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AdminReconciliationDetailDto get(Long id) {
        return mapDetail(loadDefinition(id));
    }

    public AdminReconciliationDetailDto create(AdminReconciliationRequest request, String actor) {
        validator.validate(request);
        definitionRepository.findByCode(request.code())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Reconciliation code already exists");
                });
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setCreatedBy(actor);
        definition.setUpdatedBy(actor);
        applyRequest(definition, request, actor);
        definitionRepository.save(definition);
        systemActivityService.recordEvent(
                SystemEventType.RECONCILIATION_CONFIG_CHANGE,
                String.format("Reconciliation %s created by %s", definition.getCode(), actor));
        return mapDetail(definition);
    }

    public AdminReconciliationDetailDto update(Long id, AdminReconciliationRequest request, String actor) {
        ReconciliationDefinition definition = loadDefinition(id);
        validator.validate(request);
        if (request.version() != null && !Objects.equals(definition.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException(
                    "Reconciliation definition was updated by another user. Please refresh and retry.");
        }
        if (!definition.getCode().equals(request.code())) {
            Optional<ReconciliationDefinition> other = definitionRepository.findByCode(request.code());
            if (other.isPresent() && !Objects.equals(other.get().getId(), definition.getId())) {
                throw new IllegalArgumentException("Reconciliation code already exists");
            }
        }
        applyRequest(definition, request, actor);
        systemActivityService.recordEvent(
                SystemEventType.RECONCILIATION_CONFIG_CHANGE,
                String.format("Reconciliation %s updated by %s", definition.getCode(), actor));
        return mapDetail(definition);
    }

    public AdminReconciliationDetailDto patch(Long id, AdminReconciliationPatchRequest request, String actor) {
        ReconciliationDefinition definition = loadDefinition(id);
        boolean changed = false;

        if (request.status() != null && request.status() != definition.getStatus()) {
            applyStatus(definition, request.status(), actor);
            changed = true;
        }
        if (request.makerCheckerEnabled() != null
                && request.makerCheckerEnabled() != definition.isMakerCheckerEnabled()) {
            definition.setMakerCheckerEnabled(request.makerCheckerEnabled());
            changed = true;
        }
        if (request.notes() != null && !Objects.equals(request.notes(), definition.getNotes())) {
            definition.setNotes(request.notes());
            changed = true;
        }
        if (request.owner() != null && !Objects.equals(trimToNull(request.owner()), definition.getOwnedBy())) {
            definition.setOwnedBy(trimToNull(request.owner()));
            changed = true;
        }
        if (request.autoTriggerEnabled() != null
                && request.autoTriggerEnabled() != definition.isAutoTriggerEnabled()) {
            definition.setAutoTriggerEnabled(request.autoTriggerEnabled());
            changed = true;
        }
        if (request.autoTriggerCron() != null
                && !Objects.equals(trimToNull(request.autoTriggerCron()), definition.getAutoTriggerCron())) {
            definition.setAutoTriggerCron(trimToNull(request.autoTriggerCron()));
            changed = true;
        }
        if (request.autoTriggerTimezone() != null
                && !Objects.equals(trimToNull(request.autoTriggerTimezone()), definition.getAutoTriggerTimezone())) {
            definition.setAutoTriggerTimezone(trimToNull(request.autoTriggerTimezone()));
            changed = true;
        }
        if (request.autoTriggerGraceMinutes() != null
                && !Objects.equals(request.autoTriggerGraceMinutes(), definition.getAutoTriggerGraceMinutes())) {
            definition.setAutoTriggerGraceMinutes(request.autoTriggerGraceMinutes());
            changed = true;
        }

        if (!changed) {
            return mapDetail(definition);
        }

        definition.touch();
        definition.setUpdatedBy(actor);
        systemActivityService.recordEvent(
                SystemEventType.RECONCILIATION_CONFIG_CHANGE,
                String.format("Reconciliation %s patched by %s", definition.getCode(), actor));
        return mapDetail(definition);
    }

    public void retire(Long id, String actor) {
        ReconciliationDefinition definition = loadDefinition(id);
        applyStatus(definition, ReconciliationLifecycleStatus.RETIRED, actor);
        definition.touch();
        definition.setUpdatedBy(actor);
        systemActivityService.recordEvent(
                SystemEventType.RECONCILIATION_CONFIG_CHANGE,
                String.format("Reconciliation %s retired by %s", definition.getCode(), actor));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AdminReconciliationSchemaDto exportSchema(Long id) {
        ReconciliationDefinition definition = loadDefinition(id);
        List<AdminReconciliationSchemaDto.SchemaSourceDto> sources = definition.getSources().stream()
                .sorted(Comparator.comparing(ReconciliationSource::getCode))
                .map(source -> new AdminReconciliationSchemaDto.SchemaSourceDto(
                        source.getCode(),
                        source.getAdapterType(),
                        source.isAnchor(),
                        source.getConnectionConfig(),
                        source.getArrivalExpectation(),
                        source.getArrivalTimezone(),
                        source.getArrivalSlaMinutes(),
                        source.getAdapterOptions(),
                        buildIngestionEndpoint(definition.getId(), source.getCode())))
                .toList();
        List<AdminReconciliationSchemaDto.SchemaFieldDto> fields = definition.getCanonicalFields().stream()
                .sorted(Comparator.comparing(field -> Optional.ofNullable(field.getDisplayOrder()).orElse(Integer.MAX_VALUE)))
                .map(field -> new AdminReconciliationSchemaDto.SchemaFieldDto(
                        field.getDisplayName(),
                        field.getCanonicalName(),
                        field.getRole(),
                        field.getDataType(),
                        field.getComparisonLogic(),
                        field.getThresholdPercentage(),
                        field.getFormattingHint(),
                        field.isRequired(),
                        field.getMappings().stream()
                                .sorted(Comparator.comparing(mapping -> mapping.getSource().getCode()))
                                .map(mapping -> new AdminReconciliationSchemaDto.SchemaFieldMappingDto(
                                        mapping.getSource().getCode(),
                                        mapping.getSourceColumn(),
                                        mapping.getTransformationExpression(),
                                        mapping.getDefaultValue(),
                                        mapping.getOrdinalPosition(),
                                        mapping.isRequired(),
                                        mapping.getTransformations().stream()
                                                .map(transformation -> new AdminReconciliationSchemaDto.SchemaFieldTransformationDto(
                                                        transformation.getId(),
                                                        transformation.getType().name(),
                                                        transformation.getExpression(),
                                                        transformation.getConfiguration(),
                                                        transformation.getDisplayOrder(),
                                                        transformation.isActive()))
                                                .toList()))
                                .toList()))
                .toList();
        return new AdminReconciliationSchemaDto(definition.getId(), definition.getCode(), definition.getName(), sources, fields);
    }

    public AdminIngestionBatchDto ingest(
            Long definitionId,
            String sourceCode,
            AdminIngestionRequest ingestionMetadata,
            IngestionAdapterRequest ingestionRequest,
            String actor) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        if (definition.getStatus() == ReconciliationLifecycleStatus.RETIRED) {
            throw new IllegalArgumentException("Reconciliation is retired and cannot accept new batches.");
        }
        ReconciliationSource source = definition.getSources().stream()
                .filter(candidate -> candidate.getCode().equals(sourceCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source code " + sourceCode));

        if (!source.getAdapterType().equals(ingestionMetadata.adapterType())) {
            throw new IllegalArgumentException("Adapter type does not match configured source adapter.");
        }

        var batch = sourceIngestionService.ingest(
                definition, sourceCode, ingestionMetadata.adapterType(), ingestionRequest);

        systemActivityService.recordEvent(
                SystemEventType.INGESTION_BATCH_ACCEPTED,
                String.format(
                        "Ingestion batch %s accepted for reconciliation %s by %s",
                        batch.getId(), definition.getCode(), actor));
        return new AdminIngestionBatchDto(
                batch.getId(),
                batch.getLabel(),
                batch.getStatus(),
                batch.getRecordCount(),
                batch.getChecksum(),
                batch.getIngestedAt());
    }

    private void applyRequest(ReconciliationDefinition definition, AdminReconciliationRequest request, String actor) {
        definition.setCode(request.code());
        definition.setName(request.name());
        definition.setDescription(request.description());
        definition.setMakerCheckerEnabled(request.makerCheckerEnabled());
        definition.setNotes(request.notes());
        definition.setOwnedBy(trimToNull(request.owner()));
        definition.setAutoTriggerEnabled(request.autoTriggerEnabled());
        definition.setAutoTriggerCron(trimToNull(request.autoTriggerCron()));
        definition.setAutoTriggerTimezone(trimToNull(request.autoTriggerTimezone()));
        definition.setAutoTriggerGraceMinutes(request.autoTriggerGraceMinutes());
        definition.setUpdatedBy(actor);
        applyStatus(definition, request.status(), actor);

        definition.touch();

        syncSources(definition, request.sources());
        Map<String, ReconciliationSource> sourceByCode = definition.getSources().stream()
                .collect(Collectors.toMap(
                        ReconciliationSource::getCode,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        syncCanonicalFields(definition, request.canonicalFields(), sourceByCode);
        syncReportTemplates(definition, request.reportTemplates());
        syncAccessControl(definition, request.accessControlEntries());
    }

    private void syncSources(ReconciliationDefinition definition, List<AdminSourceRequest> sourceRequests) {
        Map<String, ReconciliationSource> existing = definition.getSources().stream()
                .collect(Collectors.toMap(
                        ReconciliationSource::getCode,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        LinkedHashSet<ReconciliationSource> updated = new LinkedHashSet<>();
        for (AdminSourceRequest request : sourceRequests) {
            ReconciliationSource source = existing.remove(request.code());
            if (source == null) {
                source = new ReconciliationSource();
                source.setDefinition(definition);
            }
            source.setCode(request.code());
            source.setDisplayName(request.displayName());
            source.setAdapterType(request.adapterType());
            source.setAnchor(request.anchor());
            source.setDescription(trimToNull(request.description()));
            source.setConnectionConfig(trimToNull(request.connectionConfig()));
            source.setArrivalExpectation(trimToNull(request.arrivalExpectation()));
            source.setArrivalTimezone(trimToNull(request.arrivalTimezone()));
            source.setArrivalSlaMinutes(request.arrivalSlaMinutes());
            source.setAdapterOptions(trimToNull(request.adapterOptions()));
            source.setTransformationPlan(transformationPlanMapper.serialize(request.transformationPlan()));
            source.touch();
            updated.add(source);
        }

        // orphan removal will cascade deletes for sources not present in the updated set
        definition.getSources().clear();
        definition.getSources().addAll(updated);
    }

    private void syncCanonicalFields(
            ReconciliationDefinition definition,
            List<AdminCanonicalFieldRequest> fieldRequests,
            Map<String, ReconciliationSource> sourcesByCode) {
        Map<String, CanonicalField> existing = definition.getCanonicalFields().stream()
                .collect(Collectors.toMap(
                        CanonicalField::getCanonicalName,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        LinkedHashSet<CanonicalField> updated = new LinkedHashSet<>();
        for (AdminCanonicalFieldRequest request : fieldRequests) {
            CanonicalField field = existing.remove(request.canonicalName());
            if (field == null) {
                field = new CanonicalField();
                field.setDefinition(definition);
            }
            field.setCanonicalName(request.canonicalName());
            field.setDisplayName(request.displayName());
            field.setRole(request.role());
            field.setDataType(request.dataType());
            field.setComparisonLogic(request.comparisonLogic());
            field.setThresholdPercentage(request.thresholdPercentage());
            field.setClassifierTag(request.classifierTag());
            field.setFormattingHint(trimToNull(request.formattingHint()));
            field.setDisplayOrder(request.displayOrder());
            field.setRequired(request.required());
            updateFieldMappings(field, request.mappings(), sourcesByCode);
            field.touch();
            updated.add(field);
        }
        definition.getCanonicalFields().clear();
        definition.getCanonicalFields().addAll(updated);
    }

    private void updateFieldMappings(
            CanonicalField field,
            List<AdminCanonicalFieldMappingRequest> mappingRequests,
            Map<String, ReconciliationSource> sourcesByCode) {
        field.getMappings().forEach(mapping -> {
            ReconciliationSource source = mapping.getSource();
            if (source != null) {
                source.getFieldMappings().remove(mapping);
            }
        });
        field.getMappings().clear();

        if (mappingRequests == null || mappingRequests.isEmpty()) {
            return;
        }

        for (AdminCanonicalFieldMappingRequest request : mappingRequests) {
            ReconciliationSource source = sourcesByCode.get(request.sourceCode());
            if (source == null) {
                throw new IllegalArgumentException("Unknown source code: " + request.sourceCode());
            }
            CanonicalFieldMapping mapping = new CanonicalFieldMapping();
            mapping.setCanonicalField(field);
            mapping.setSource(source);
            mapping.setSourceColumn(request.sourceColumn());
            mapping.setTransformationExpression(request.transformationExpression());
            mapping.setDefaultValue(request.defaultValue());
            mapping.setOrdinalPosition(request.ordinalPosition());
            mapping.setRequired(request.required());
            syncTransformations(mapping, request.transformations());
            mapping.touch();
            field.getMappings().add(mapping);
            source.getFieldMappings().add(mapping);
        }
    }

    private void syncTransformations(
            CanonicalFieldMapping mapping, List<AdminCanonicalFieldTransformationRequest> transformationRequests) {
        mapping.getTransformations().clear();
        if (transformationRequests == null || transformationRequests.isEmpty()) {
            return;
        }

        int orderFallback = transformationRequests.stream()
                .map(AdminCanonicalFieldTransformationRequest::displayOrder)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1)
                + 1;
        for (AdminCanonicalFieldTransformationRequest request : transformationRequests) {
            CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
            transformation.setMapping(mapping);
            transformation.setType(request.type());
            transformation.setExpression(trimToNull(request.expression()));
            transformation.setConfiguration(trimToNull(request.configuration()));
            transformation.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : orderFallback++);
            transformation.setActive(request.active() == null || request.active());
            transformation.touch();
            try {
                transformationService.validate(transformation);
            } catch (TransformationEvaluationException ex) {
                throw new IllegalArgumentException("Invalid transformation: " + ex.getMessage(), ex);
            }
            mapping.getTransformations().add(transformation);
        }
    }

    private void syncReportTemplates(
            ReconciliationDefinition definition, List<AdminReportTemplateRequest> templateRequests) {
        if (templateRequests == null) {
            definition.getReportTemplates().clear();
            return;
        }

        Map<String, ReportTemplate> existing = definition.getReportTemplates().stream()
                .collect(Collectors.toMap(
                        ReportTemplate::getName,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        LinkedHashSet<ReportTemplate> updated = new LinkedHashSet<>();
        for (AdminReportTemplateRequest request : templateRequests) {
            ReportTemplate template = existing.remove(request.name());
            if (template == null) {
                template = new ReportTemplate();
                template.setDefinition(definition);
            }
            template.setName(request.name());
            template.setDescription(request.description());
            template.setIncludeMatched(request.includeMatched());
            template.setIncludeMismatched(request.includeMismatched());
            template.setIncludeMissing(request.includeMissing());
            template.setHighlightDifferences(request.highlightDifferences());
            syncReportColumns(template, request.columns());
            updated.add(template);
        }
        definition.getReportTemplates().clear();
        definition.getReportTemplates().addAll(updated);
    }

    private void syncReportColumns(ReportTemplate template, List<AdminReportColumnRequest> columnRequests) {
        template.getColumns().clear();
        if (columnRequests == null) {
            return;
        }
        List<ReportColumn> columns = new ArrayList<>();
        for (AdminReportColumnRequest request : columnRequests) {
            ReportColumn column = new ReportColumn();
            column.setTemplate(template);
            column.setHeader(request.header());
            column.setSource(request.source());
            column.setSourceField(request.sourceField());
            column.setDisplayOrder(request.displayOrder());
            column.setHighlightDifferences(request.highlightDifferences());
            columns.add(column);
        }
        template.getColumns().addAll(columns);
    }

    private void syncAccessControl(
            ReconciliationDefinition definition, List<AdminAccessControlEntryRequest> entryRequests) {
        if (entryRequests == null) {
            definition.getAccessControlEntries().clear();
            return;
        }

        Map<String, AccessControlEntry> existing = definition.getAccessControlEntries().stream()
                .collect(Collectors.toMap(
                        AccessControlEntry::getLdapGroupDn,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        LinkedHashSet<AccessControlEntry> updated = new LinkedHashSet<>();
        for (AdminAccessControlEntryRequest request : entryRequests) {
            AccessControlEntry entry = existing.remove(request.ldapGroupDn());
            if (entry == null) {
                entry = new AccessControlEntry();
                entry.setDefinition(definition);
            }
            entry.setLdapGroupDn(request.ldapGroupDn());
            entry.setRole(request.role());
            entry.setProduct(trimToNull(request.product()));
            entry.setSubProduct(trimToNull(request.subProduct()));
            entry.setEntityName(trimToNull(request.entityName()));
            entry.setNotifyOnPublish(request.notifyOnPublish());
            entry.setNotifyOnIngestionFailure(request.notifyOnIngestionFailure());
            entry.setNotificationChannel(trimToNull(request.notificationChannel()));
            updated.add(entry);
        }
        definition.getAccessControlEntries().clear();
        definition.getAccessControlEntries().addAll(updated);
    }

    private AdminReconciliationSummaryDto mapSummary(ReconciliationDefinition definition) {
        Instant lastIngestion = definition.getSources().stream()
                .flatMap(source -> source.getBatches().stream())
                .map(SourceDataBatch::getIngestedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new AdminReconciliationSummaryDto(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getStatus(),
                definition.isMakerCheckerEnabled(),
                definition.getUpdatedAt(),
                definition.getOwnedBy(),
                definition.getUpdatedBy(),
                lastIngestion);
    }

    private AdminReconciliationDetailDto mapDetail(ReconciliationDefinition definition) {
        List<AdminSourceDto> sources = definition.getSources().stream()
                .sorted(Comparator.comparing(ReconciliationSource::getCode))
                .map(source -> new AdminSourceDto(
                        source.getId(),
                        source.getCode(),
                        source.getDisplayName(),
                        source.getAdapterType(),
                        source.isAnchor(),
                        source.getDescription(),
                        source.getConnectionConfig(),
                        source.getArrivalExpectation(),
                        source.getArrivalTimezone(),
                        source.getArrivalSlaMinutes(),
                        source.getAdapterOptions(),
                        transformationPlanMapper.deserialize(source.getTransformationPlan()).orElse(null),
                        source.getCreatedAt(),
                        source.getUpdatedAt()))
                .toList();

        List<AdminCanonicalFieldDto> fields = definition.getCanonicalFields().stream()
                .sorted(Comparator.comparing(field -> Optional.ofNullable(field.getDisplayOrder()).orElse(Integer.MAX_VALUE)))
                .map(field -> new AdminCanonicalFieldDto(
                        field.getId(),
                        field.getCanonicalName(),
                        field.getDisplayName(),
                        field.getRole(),
                        field.getDataType(),
                        field.getComparisonLogic(),
                        field.getThresholdPercentage(),
                        field.getClassifierTag(),
                        field.getFormattingHint(),
                        field.getDisplayOrder(),
                        field.isRequired(),
                        field.getCreatedAt(),
                        field.getUpdatedAt(),
                        field.getMappings().stream()
                                .map(mapping -> new AdminCanonicalFieldMappingDto(
                                        mapping.getId(),
                                        mapping.getSource() != null ? mapping.getSource().getCode() : null,
                                        mapping.getSourceColumn(),
                                        mapping.getTransformationExpression(),
                                        mapping.getDefaultValue(),
                                        mapping.getOrdinalPosition(),
                                        mapping.isRequired(),
                                        mapping.getTransformations().stream()
                                                .map(transformation -> new AdminCanonicalFieldTransformationDto(
                                                        transformation.getId(),
                                                        transformation.getType(),
                                                        transformation.getExpression(),
                                                        transformation.getConfiguration(),
                                                        transformation.getDisplayOrder(),
                                                        transformation.isActive()))
                                                .toList()))
                                .toList()))
                .toList();

        List<AdminReportTemplateDto> reportTemplates = definition.getReportTemplates().stream()
                .sorted(Comparator.comparing(ReportTemplate::getName))
                .map(template -> new AdminReportTemplateDto(
                        template.getId(),
                        template.getName(),
                        template.getDescription(),
                        template.isIncludeMatched(),
                        template.isIncludeMismatched(),
                        template.isIncludeMissing(),
                        template.isHighlightDifferences(),
                        template.getColumns().stream()
                                .sorted(Comparator.comparingInt(ReportColumn::getDisplayOrder))
                                .map(column -> new AdminReportColumnDto(
                                        column.getId(),
                                        column.getHeader(),
                                        column.getSource(),
                                        column.getSourceField(),
                                        column.getDisplayOrder(),
                                        column.isHighlightDifferences()))
                                .toList()))
                .toList();

        List<AdminAccessControlEntryDto> accessEntries = definition.getAccessControlEntries() == null
                ? List.of()
                : definition.getAccessControlEntries().stream()
                        .sorted(Comparator.comparing(AccessControlEntry::getLdapGroupDn))
                        .map(entry -> new AdminAccessControlEntryDto(
                                entry.getId(),
                                entry.getLdapGroupDn(),
                                entry.getRole(),
                                entry.getProduct(),
                                entry.getSubProduct(),
                                entry.getEntityName(),
                                entry.isNotifyOnPublish(),
                                entry.isNotifyOnIngestionFailure(),
                                entry.getNotificationChannel()))
                        .toList();

        List<AdminIngestionBatchDto> ingestionBatches = definition.getSources().stream()
                .flatMap(source -> source.getBatches().stream())
                .sorted(Comparator.comparing(SourceDataBatch::getIngestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(batch -> new AdminIngestionBatchDto(
                        batch.getId(),
                        batch.getLabel(),
                        batch.getStatus(),
                        batch.getRecordCount(),
                        batch.getChecksum(),
                        batch.getIngestedAt()))
                .toList();

        return new AdminReconciliationDetailDto(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getNotes(),
                definition.getOwnedBy(),
                definition.isMakerCheckerEnabled(),
                definition.getStatus(),
                definition.getCreatedAt(),
                definition.getUpdatedAt(),
                definition.getCreatedBy(),
                definition.getUpdatedBy(),
                definition.getPublishedAt(),
                definition.getPublishedBy(),
                definition.getRetiredAt(),
                definition.getRetiredBy(),
                definition.getVersion(),
                definition.isAutoTriggerEnabled(),
                definition.getAutoTriggerCron(),
                definition.getAutoTriggerTimezone(),
                definition.getAutoTriggerGraceMinutes(),
                sources,
                fields,
                reportTemplates,
                accessEntries,
                ingestionBatches);
    }

    private ReconciliationDefinition loadDefinition(Long id) {
        return definitionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation definition not found"));
    }

    private void applyStatus(ReconciliationDefinition definition, ReconciliationLifecycleStatus status, String actor) {
        if (status == null) {
            return;
        }
        ReconciliationLifecycleStatus previousStatus = definition.getStatus();
        definition.setStatus(status);
        if (status == ReconciliationLifecycleStatus.PUBLISHED) {
            if (previousStatus != ReconciliationLifecycleStatus.PUBLISHED
                    || definition.getPublishedAt() == null) {
                definition.setPublishedAt(Instant.now());
                definition.setPublishedBy(actor);
            }
            definition.setRetiredAt(null);
            definition.setRetiredBy(null);
        } else if (status == ReconciliationLifecycleStatus.RETIRED) {
            definition.setRetiredAt(Instant.now());
            definition.setRetiredBy(actor);
        } else {
            definition.setPublishedAt(null);
            definition.setPublishedBy(null);
            definition.setRetiredAt(null);
            definition.setRetiredBy(null);
        }
    }

    private String buildIngestionEndpoint(Long definitionId, String sourceCode) {
        return String.format("/api/admin/reconciliations/%d/sources/%s/batches", definitionId, sourceCode);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
