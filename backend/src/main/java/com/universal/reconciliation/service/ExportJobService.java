package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.universal.reconciliation.domain.dto.ExportJobDto;
import com.universal.reconciliation.domain.dto.ExportJobRequestDto;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.ExportJob;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ExportFormat;
import com.universal.reconciliation.domain.enums.ExportJobStatus;
import com.universal.reconciliation.domain.enums.ExportJobType;
import com.universal.reconciliation.repository.ExportJobRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.service.export.DatasetExportWriter;
import com.universal.reconciliation.service.export.DatasetRow;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchResult;
import com.universal.reconciliation.service.search.BreakSearchRow;
import com.universal.reconciliation.util.ParsingUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Manages export job scheduling, processing, and retrieval.
 */
@Service
public class ExportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExportJobService.class);
    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    private final ExportJobRepository exportJobRepository;
    private final ReconciliationDefinitionRepository definitionRepository;
    private final BreakSearchService breakSearchService;
    private final BreakSearchCriteriaFactory criteriaFactory;
    private final BreakAccessService breakAccessService;
    private final DatasetExportWriter datasetExportWriter;
    private final ObjectMapper objectMapper;
    private ExportJobService self;

    public ExportJobService(
            ExportJobRepository exportJobRepository,
            ReconciliationDefinitionRepository definitionRepository,
            BreakSearchService breakSearchService,
            BreakSearchCriteriaFactory criteriaFactory,
            BreakAccessService breakAccessService,
            DatasetExportWriter datasetExportWriter,
            ObjectMapper objectMapper) {
        this.exportJobRepository = exportJobRepository;
        this.definitionRepository = definitionRepository;
        this.breakSearchService = breakSearchService;
        this.criteriaFactory = criteriaFactory;
        this.breakAccessService = breakAccessService;
        this.datasetExportWriter = datasetExportWriter;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setSelf(@Lazy ExportJobService self) {
        this.self = self;
    }

    @Transactional
    public ExportJobDto queueDatasetExport(
            Long definitionId, ExportJobRequestDto request, String owner, List<String> groups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        List<AccessControlEntry> entries = breakAccessService.findEntries(definition, groups);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("User lacks access to this reconciliation");
        }

        ExportJob job = new ExportJob();
        job.setDefinition(definition);
        job.setOwner(owner);
        job.setJobType(ExportJobType.RESULT_DATASET);
        job.setFormat(request.format());
        job.setFiltersJson(writeJson(request.filters()));
        job.setOwnerGroupsJson(writeJson(groups));
        job.setSettingsJson(writeJson(Map.of("includeMetadata", request.includeMetadata())));
        job.setTimezone(SGT.getId());
        job.setFileName(buildFileName(request.fileNamePrefix(), definition.getCode(), request.format()));
        exportJobRepository.save(job);

        processJobAsync(job.getId());

        return toDto(job);
    }

    @Transactional(readOnly = true)
    public List<ExportJobDto> listJobs(Long definitionId, String owner) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        return exportJobRepository.findByDefinitionAndOwnerOrderByCreatedAtDesc(definition, owner).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ExportJob> findJob(Long jobId, String owner) {
        return exportJobRepository.findById(jobId).filter(job -> job.getOwner().equals(owner));
    }

    @Async
    public void processJobAsync(Long jobId) {
        try {
            ExportJobService target = self != null ? self : this;
            target.processJob(jobId);
        } catch (Exception ex) {
            log.error("Export job {} failed", jobId, ex);
        }
    }

    @Transactional
    public void processJob(Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found"));
        try {
            job.setStatus(ExportJobStatus.PROCESSING);
            job.touch();
            exportJobRepository.save(job);

            MultiValueMap<String, String> params = toParams(job.getFiltersJson());
            int requestedSize = ParsingUtils.parseIntOrDefault(params.getFirst("size"), 2000, "size");
            int effectiveSize = Math.min(Math.max(requestedSize, 200), 5000);
            params.set("size", String.valueOf(effectiveSize));

            List<String> groups = parseGroups(job.getOwnerGroupsJson());

            List<DatasetRow> dataset = new ArrayList<>();
            Set<String> attributeKeys = new LinkedHashSet<>();

            String cursorToken = null;
            while (true) {
                if (cursorToken != null) {
                    params.set("cursor", cursorToken);
                } else {
                    params.remove("cursor");
                }
                BreakSearchCriteria criteria = criteriaFactory.fromQueryParams(params);
                BreakSearchResult page = breakSearchService.search(
                        job.getDefinition().getId(), criteria, groups);
                page.rows().forEach(row -> {
                    DatasetRow exportRow = toDatasetRow(row);
                    dataset.add(exportRow);
                    attributeKeys.addAll(exportRow.attributes().keySet());
                });

                if (!page.hasMore() || page.nextCursor() == null) {
                    break;
                }
                cursorToken = page.nextCursor().toToken();
            }

            List<String> orderedAttributes = DatasetExportWriter.normaliseAttributeKeys(attributeKeys);
            Map<String, Object> metadata = Map.of(
                    "filterSummary", job.getFiltersJson(),
                    "generatedBy", job.getOwner());
            byte[] content = datasetExportWriter.write(job.getFormat(), dataset, orderedAttributes, metadata);
            job.setPayload(content);
            job.setContentHash(hash(content));
            job.setRowCount((long) dataset.size());
            job.setStatus(ExportJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.touch();
            exportJobRepository.save(job);
        } catch (Exception ex) {
            job.setStatus(ExportJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.touch();
            exportJobRepository.save(job);
            throw new IllegalStateException("Failed to process export job " + jobId, ex);
        }
    }

    private DatasetRow toDatasetRow(BreakSearchRow row) {
        var breakItem = row.breakItem();
        String maker = breakItem.submittedByDn();
        String checker = breakItem.history().stream()
                .filter(entry -> entry.actorRole() == AccessRole.CHECKER)
                .reduce((first, second) -> second)
                .map(entry -> entry.actorDn())
                .orElse(null);
        String latestComment = breakItem.comments().isEmpty()
                ? null
                : breakItem.comments().get(breakItem.comments().size() - 1).comment();

        Map<String, String> attributes = new LinkedHashMap<>(row.attributeValues());
        breakItem.classifications().forEach(attributes::putIfAbsent);

        return new DatasetRow(
                row.breakId(),
                row.runId(),
                row.runDateTime(),
                row.triggerType(),
                breakItem.status(),
                breakItem.breakType().name(),
                breakItem.detectedAt(),
                attributes,
                maker,
                checker,
                latestComment,
                breakItem.missingSources(),
                breakItem.submittedByDn(),
                breakItem.submittedAt());
    }

    private MultiValueMap<String, String> toParams(String filtersJson) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (filtersJson == null || filtersJson.isBlank()) {
            return params;
        }
        try {
            Map<String, List<String>> map = objectMapper.readValue(
                    filtersJson, new TypeReference<Map<String, List<String>>>() {});
            map.forEach((key, values) -> params.put(key, new ArrayList<>(values != null ? values : List.of())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse export filters", e);
        }
        return params;
    }

    private List<String> parseGroups(String groupsJson) {
        if (groupsJson == null || groupsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(groupsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse owner groups", e);
        }
    }

    private ReconciliationDefinition loadDefinition(Long definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise JSON payload", e);
        }
    }

    private String buildFileName(String prefix, String definitionCode, ExportFormat format) {
        String base = (prefix != null && !prefix.isBlank()) ? prefix : definitionCode;
        String timestamp = Instant.now().atZone(SGT).toLocalDateTime().toString().replace(':', '-');
        return base + "-" + timestamp + "." + format.name().toLowerCase(Locale.ROOT);
    }

    private String hash(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public ExportJobDto toDto(ExportJob job) {
        return new ExportJobDto(
                job.getId(),
                job.getJobType(),
                job.getFormat(),
                job.getStatus(),
                job.getFileName(),
                job.getContentHash(),
                job.getRowCount(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getErrorMessage());
    }
}
