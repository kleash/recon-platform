package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationListItemDto;
import com.universal.reconciliation.domain.dto.ReconciliationSummaryDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.RunStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public ReconciliationService(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            ReconciliationRunRepository runRepository,
            BreakItemRepository breakItemRepository,
            MatchingEngine matchingEngine,
            ObjectMapper objectMapper,
            BreakMapper breakMapper) {
        this.definitionRepository = definitionRepository;
        this.accessControlEntryRepository = accessControlEntryRepository;
        this.runRepository = runRepository;
        this.breakItemRepository = breakItemRepository;
        this.matchingEngine = matchingEngine;
        this.objectMapper = objectMapper;
        this.breakMapper = breakMapper;
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
    public RunDetailDto triggerRun(Long definitionId, List<String> userGroups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureViewAccess(definition, userGroups);
        MatchingResult result = matchingEngine.execute(definition);

        ReconciliationRun run = new ReconciliationRun();
        run.setDefinition(definition);
        run.setRunDateTime(Instant.now());
        run.setTriggerType(TriggerType.MANUAL_API);
        run.setStatus(RunStatus.SUCCESS);
        run.setMatchedCount(result.matchedCount());
        run.setMismatchedCount(result.mismatchedCount());
        run.setMissingCount(result.missingCount());
        run = runRepository.save(run);

        persistBreaks(run, result.breaks());
        return buildRunDetail(run);
    }

    public RunDetailDto fetchLatestRun(Long definitionId, List<String> userGroups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureViewAccess(definition, userGroups);
        return runRepository.findTopByDefinitionOrderByRunDateTimeDesc(definition)
                .map(this::buildRunDetail)
                .orElseGet(() -> new RunDetailDto(
                        new ReconciliationSummaryDto(null, null, 0, 0, 0), List.of()));
    }

    public RunDetailDto fetchRunDetail(Long runId, List<String> userGroups) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
        ensureViewAccess(run.getDefinition(), userGroups);
        return buildRunDetail(run);
    }

    private ReconciliationDefinition loadDefinition(Long definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));
    }

    private void ensureViewAccess(ReconciliationDefinition definition, List<String> userGroups) {
        if (userGroups.isEmpty()) {
            throw new SecurityException("User lacks group membership for this reconciliation");
        }
        List<AccessControlEntry> entries = accessControlEntryRepository.findByDefinitionAndLdapGroupDnIn(definition, userGroups);
        if (entries.isEmpty()) {
            throw new SecurityException("Access denied");
        }
    }

    private void persistBreaks(ReconciliationRun run, List<BreakCandidate> candidates) {
        List<BreakItem> items = new ArrayList<>();
        for (BreakCandidate candidate : candidates) {
            BreakItem breakItem = new BreakItem();
            breakItem.setRun(run);
            breakItem.setBreakType(candidate.type());
            breakItem.setDetectedAt(Instant.now());
            breakItem.setSourceAJson(writeJson(candidate.sourceA()));
            breakItem.setSourceBJson(writeJson(candidate.sourceB()));
            items.add(breakItem);
        }
        breakItemRepository.saveAll(items);
    }

    private RunDetailDto buildRunDetail(ReconciliationRun run) {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                run.getId(), run.getRunDateTime(), run.getMatchedCount(), run.getMismatchedCount(), run.getMissingCount());
        List<BreakItemDto> breaks = breakItemRepository.findByRunOrderByDetectedAtAsc(run).stream()
                .map(breakMapper::toDto)
                .toList();
        return new RunDetailDto(summary, breaks);
    }

    private String writeJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize break payload", e);
        }
    }
}
