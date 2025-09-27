package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.ApprovalQueueDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.FilterMetadataDto;
import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunAnalyticsDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.dto.TriggerRunRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakClassificationValue;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.RunStatus;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.AccessControlEntryRepository;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationRunRepository;
import com.universal.reconciliation.service.matching.BreakCandidate;
import com.universal.reconciliation.service.matching.MatchingEngine;
import com.universal.reconciliation.service.matching.MatchingResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsulates reconciliation definition discovery and matching execution logic.
 */
@Service
public class ReconciliationService {

    private final ReconciliationDefinitionRepository definitionRepository;
    private final AccessControlEntryRepository accessControlEntryRepository;
    private final ReconciliationRunRepository runRepository;
    private final BreakItemRepository breakItemRepository;
    private final MatchingEngine matchingEngine;
    private final ObjectMapper objectMapper;
    private final BreakMapper breakMapper;
    private final BreakAccessService breakAccessService;
    private final SystemActivityService systemActivityService;
    private final RunAnalyticsCalculator runAnalyticsCalculator;
    private final int approvalQueueSize;

    public ReconciliationService(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            ReconciliationRunRepository runRepository,
            BreakItemRepository breakItemRepository,
            MatchingEngine matchingEngine,
            ObjectMapper objectMapper,
            BreakMapper breakMapper,
            BreakAccessService breakAccessService,
            SystemActivityService systemActivityService,
            RunAnalyticsCalculator runAnalyticsCalculator,
            @Value("${app.approvals.queue-size:200}") int approvalQueueSize) {
        this.definitionRepository = definitionRepository;
        this.accessControlEntryRepository = accessControlEntryRepository;
        this.runRepository = runRepository;
        this.breakItemRepository = breakItemRepository;
        this.matchingEngine = matchingEngine;
        this.objectMapper = objectMapper;
        this.breakMapper = breakMapper;
        this.breakAccessService = breakAccessService;
        this.systemActivityService = systemActivityService;
        this.runAnalyticsCalculator = runAnalyticsCalculator;
        this.approvalQueueSize = approvalQueueSize;
    }

    public List<ReconciliationListItemDto> listAccessible(List<String> userGroups) {
        if (userGroups.isEmpty()) {
            return List.of();
        }
        List<AccessControlEntry> entries = accessControlEntryRepository.findByLdapGroupDnIn(userGroups);
        Map<Long, ReconciliationDefinition> definitions = new LinkedHashMap<>();
        for (AccessControlEntry entry : entries) {
            definitions.putIfAbsent(entry.getDefinition().getId(), entry.getDefinition());
        }
        return definitions.values().stream()
                .sorted(Comparator.comparing(ReconciliationDefinition::getName))
                .map(def -> new ReconciliationListItemDto(def.getId(), def.getCode(), def.getName(), def.getDescription()))
                .toList();
    }

    @Transactional
    public RunDetailDto triggerRun(
            Long definitionId,
            List<String> userGroups,
            String initiatedBy,
            TriggerRunRequest request) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        List<AccessControlEntry> entries = ensureAccess(definition, userGroups);
        MatchingResult result = matchingEngine.execute(definition);

        ReconciliationRun run = new ReconciliationRun();
        run.setDefinition(definition);
        run.setRunDateTime(Instant.now());
        TriggerType triggerType = request.triggerType() != null ? request.triggerType() : TriggerType.MANUAL_API;
        run.setTriggerType(triggerType);
        run.setTriggeredBy(resolveInitiator(request, initiatedBy));
        run.setTriggerCorrelationId(request.correlationId());
        run.setTriggerComments(request.comments());
        run.setStatus(RunStatus.SUCCESS);
        run.setMatchedCount(result.matchedCount());
        run.setMismatchedCount(result.mismatchedCount());
        run.setMissingCount(result.missingCount());
        run = runRepository.save(run);

        persistBreaks(run, result.breaks());

        systemActivityService.recordEvent(
                SystemEventType.RECONCILIATION_RUN,
                String.format(
                        "Reconciliation %s executed via %s trigger by %s",
                        definition.getCode(), triggerType.name(), run.getTriggeredBy()));

        return buildRunDetail(run, definition, entries, BreakFilterCriteria.none());
    }

    public RunDetailDto fetchLatestRun(Long definitionId, List<String> userGroups, BreakFilterCriteria filter) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        List<AccessControlEntry> entries = ensureAccess(definition, userGroups);
        return runRepository.findTopByDefinitionOrderByRunDateTimeDesc(definition)
                .map(run -> buildRunDetail(run, definition, entries, filter))
                .orElseGet(() -> new RunDetailDto(
                        new ReconciliationSummaryDto(
                                definition.getId(),
                                null,
                                null,
                                TriggerType.MANUAL_API,
                                null,
                                null,
                                null,
                                0,
                                0,
                                0),
                        RunAnalyticsDto.empty(),
                        List.of(),
                        buildFilterMetadata(entries)));
    }

    public RunDetailDto fetchRunDetail(Long runId, List<String> userGroups, BreakFilterCriteria filter) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
        ReconciliationDefinition definition = run.getDefinition();
        List<AccessControlEntry> entries = ensureAccess(definition, userGroups);
        return buildRunDetail(run, definition, entries, filter);
    }

    public RunDetailDto fetchRunDetail(Long runId, List<String> userGroups) {
        return fetchRunDetail(runId, userGroups, BreakFilterCriteria.none());
    }

    @Transactional(readOnly = true)
    public List<ReconciliationSummaryDto> listRuns(Long definitionId, List<String> userGroups, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureAccess(definition, userGroups);
        return runRepository.findByDefinitionOrderByRunDateTimeDesc(definition).stream()
                .limit(limit)
                .map(run -> new ReconciliationSummaryDto(
                        definition.getId(),
                        run.getId(),
                        run.getRunDateTime(),
                        run.getTriggerType(),
                        run.getTriggeredBy(),
                        run.getTriggerCorrelationId(),
                        run.getTriggerComments(),
                        run.getMatchedCount(),
                        run.getMismatchedCount(),
                        run.getMissingCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalQueueDto fetchApprovalQueue(Long definitionId, List<String> userGroups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        List<AccessControlEntry> entries = ensureAccess(definition, userGroups);
        boolean hasCheckerRole = entries.stream().anyMatch(entry -> entry.getRole() == AccessRole.CHECKER);
        if (!hasCheckerRole) {
            throw new AccessDeniedException("Checker role required to view approvals queue");
        }

        List<BreakItem> pending = breakItemRepository.findByRunDefinitionIdAndStatusOrderByDetectedAtAsc(
                definitionId, BreakStatus.PENDING_APPROVAL, PageRequest.of(0, approvalQueueSize));

        List<BreakItemDto> accessible = pending.stream()
                .filter(item -> breakAccessService.canView(item, entries))
                .map(item -> breakMapper.toDto(
                        item, breakAccessService.allowedStatuses(item, definition, entries)))
                .toList();

        return new ApprovalQueueDto(accessible, buildFilterMetadata(entries));
    }

    private ReconciliationDefinition loadDefinition(Long definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));
    }

    private List<AccessControlEntry> ensureAccess(ReconciliationDefinition definition, List<String> userGroups) {
        List<AccessControlEntry> entries = breakAccessService.findEntries(definition, userGroups);
        if (entries.isEmpty()) {
            throw new SecurityException("Access denied");
        }
        return entries;
    }

    private void persistBreaks(ReconciliationRun run, List<BreakCandidate> candidates) {
        List<BreakItem> items = new ArrayList<>();
        for (BreakCandidate candidate : candidates) {
            BreakItem breakItem = new BreakItem();
            breakItem.setRun(run);
            breakItem.setBreakType(candidate.type());
            breakItem.setDetectedAt(Instant.now());
            String product = candidate.classifications().get("product");
            String subProduct = candidate.classifications().get("subProduct");
            String entity = candidate.classifications().get("entity");
            breakItem.setProduct(product);
            breakItem.setSubProduct(subProduct);
            breakItem.setEntityName(entity);
            breakItem.setClassificationJson(writeJson(candidate.classifications()));
            breakItem.setSourcePayloadJson(writeJson(candidate.sources()));
            breakItem.setMissingSourcesJson(writeJson(candidate.missingSources()));

            if (candidate.classifications() != null && !candidate.classifications().isEmpty()) {
                candidate.classifications().forEach((key, value) -> {
                    BreakClassificationValue classificationValue = new BreakClassificationValue();
                    classificationValue.setBreakItem(breakItem);
                    classificationValue.setAttributeKey(key);
                    classificationValue.setAttributeValue(value);
                    breakItem.getClassificationValues().add(classificationValue);
                });
            }

            ensureClassificationValue(breakItem, "product", product);
            ensureClassificationValue(breakItem, "subProduct", subProduct);
            ensureClassificationValue(breakItem, "entity", entity);
            items.add(breakItem);
        }
        breakItemRepository.saveAll(items);
    }

    private void ensureClassificationValue(BreakItem item, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        boolean exists = item.getClassificationValues().stream()
                .anyMatch(entry -> key.equals(entry.getAttributeKey()) && value.equals(entry.getAttributeValue()));
        if (exists) {
            return;
        }
        BreakClassificationValue classificationValue = new BreakClassificationValue();
        classificationValue.setBreakItem(item);
        classificationValue.setAttributeKey(key);
        classificationValue.setAttributeValue(value);
        item.getClassificationValues().add(classificationValue);
    }

    private RunDetailDto buildRunDetail(
            ReconciliationRun run,
            ReconciliationDefinition definition,
            List<AccessControlEntry> entries,
            BreakFilterCriteria filter) {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                definition.getId(),
                run.getId(),
                run.getRunDateTime(),
                run.getTriggerType(),
                run.getTriggeredBy(),
                run.getTriggerCorrelationId(),
                run.getTriggerComments(),
                run.getMatchedCount(),
                run.getMismatchedCount(),
                run.getMissingCount());

        List<BreakItem> accessibleBreaks = breakItemRepository.findByRunOrderByDetectedAtAsc(run).stream()
                .filter(item -> breakAccessService.canView(item, entries))
                .filter(filter::matches)
                .toList();

        List<BreakItemDto> breaks = accessibleBreaks.stream()
                .map(item -> breakMapper.toDto(
                        item, breakAccessService.allowedStatuses(item, definition, entries)))
                .toList();

        RunAnalyticsDto analytics = runAnalyticsCalculator.calculate(run, accessibleBreaks);

        return new RunDetailDto(summary, analytics, breaks, buildFilterMetadata(entries));
    }

    private FilterMetadataDto buildFilterMetadata(List<AccessControlEntry> entries) {
        Set<String> products = new LinkedHashSet<>();
        Set<String> subProducts = new LinkedHashSet<>();
        Set<String> entities = new LinkedHashSet<>();

        for (AccessControlEntry entry : entries) {
            if (entry.getProduct() != null) {
                products.add(entry.getProduct());
            }
            if (entry.getSubProduct() != null) {
                subProducts.add(entry.getSubProduct());
            }
            if (entry.getEntityName() != null) {
                entities.add(entry.getEntityName());
            }
        }

        return new FilterMetadataDto(
                products.stream().sorted().toList(),
                subProducts.stream().sorted().toList(),
                entities.stream().sorted().toList(),
                List.copyOf(EnumSet.allOf(BreakStatus.class)));
    }

    private String writeJson(Object data) {
        if (data == null) {
            return "{}";
        }
        if (data instanceof Map<?, ?> map && map.isEmpty()) {
            return "{}";
        }
        if (data instanceof List<?> list && list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize break payload", e);
        }
    }

    private String resolveInitiator(TriggerRunRequest request, String defaultInitiator) {
        if (request.initiatedBy() != null && !request.initiatedBy().isBlank()) {
            return request.initiatedBy();
        }
        return defaultInitiator;
    }
}
