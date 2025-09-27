package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.BreakColumnFilterDto;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.GridColumnDto;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakClassificationValue;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationRun;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.domain.enums.FilterOperator;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.TriggerType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.ReconciliationRunRepository;
import com.universal.reconciliation.service.search.BreakSearchCriteria;
import com.universal.reconciliation.service.search.BreakSearchCursor;
import com.universal.reconciliation.service.search.BreakSearchResult;
import com.universal.reconciliation.service.search.BreakSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides server-side search across reconciliation break results with
 * keyset pagination, dynamic filters, and metadata hydration for the grid.
 */
@Service
public class BreakSearchService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Singapore");

    private final EntityManager entityManager;
    private final BreakMapper breakMapper;
    private final ReconciliationDefinitionRepository definitionRepository;
    private final ReconciliationRunRepository runRepository;
    private final BreakAccessService breakAccessService;

    public BreakSearchService(
            EntityManager entityManager,
            BreakMapper breakMapper,
            ReconciliationDefinitionRepository definitionRepository,
            ReconciliationRunRepository runRepository,
            BreakAccessService breakAccessService) {
        this.entityManager = entityManager;
        this.breakMapper = breakMapper;
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.breakAccessService = breakAccessService;
    }

    @Transactional(readOnly = true)
    public BreakSearchResult search(Long reconciliationId, BreakSearchCriteria criteria, List<String> userGroups) {
        ReconciliationDefinition definition = definitionRepository.findById(reconciliationId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));

        var accessEntries = breakAccessService.findEntries(definition, userGroups);

        if (accessEntries.isEmpty()) {
            List<GridColumnDto> columns = buildColumnMetadata(definition);
            long totalCount = criteria.includeTotals() ? 0L : -1L;
            return new BreakSearchResult(List.of(), null, false, totalCount, columns);
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BreakItem> query = cb.createQuery(BreakItem.class);
        Root<BreakItem> root = query.from(BreakItem.class);
        root.fetch("classificationValues", JoinType.LEFT);
        root.fetch("comments", JoinType.LEFT);
        root.fetch("workflowAudits", JoinType.LEFT);
        var runJoin = root.join("run");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(runJoin.get("definition"), definition));

        if (!userGroups.isEmpty()) {
            // Use BreakAccessService to ensure entitlements (delegated to caller).
            // The caller is expected to supply only definitions the user can
            // access so no extra predicate required here.
        }

        if (criteria.fromDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(runJoin.get("runDateTime"), criteria.fromDate()));
        }
        if (criteria.toDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(runJoin.get("runDateTime"), criteria.toDate()));
        }
        if (!criteria.runIds().isEmpty()) {
            predicates.add(runJoin.get("id").in(criteria.runIds()));
        }
        if (!criteria.triggerTypes().isEmpty()) {
            predicates.add(runJoin.get("triggerType").in(criteria.triggerTypes()));
        }
        if (!criteria.statuses().isEmpty()) {
            predicates.add(root.get("status").in(criteria.statuses()));
        }

        if (criteria.cursor() != null) {
            BreakSearchCursor cursor = criteria.cursor();
            Predicate beforeCursor = cb.or(
                    cb.lessThan(runJoin.get("runDateTime"), cursor.runDateTime()),
                    cb.and(
                            cb.equal(runJoin.get("runDateTime"), cursor.runDateTime()),
                            cb.lessThan(root.get("id"), cursor.breakId())));
            predicates.add(beforeCursor);
        }

        if (criteria.hasColumnFilters()) {
            for (BreakColumnFilterDto filter : criteria.columnFilterValues()) {
                predicates.add(buildClassificationPredicate(cb, query, root, filter));
            }
        }

        if (criteria.searchTerm() != null && !criteria.searchTerm().isBlank()) {
            String likeTerm = "%" + criteria.searchTerm().toLowerCase(Locale.ROOT) + "%";
            Predicate byId = cb.like(cb.lower(root.get("id").as(String.class)), likeTerm);
            Predicate byStatus = cb.like(cb.lower(root.get("status")), likeTerm);

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BreakClassificationValue> subRoot = subquery.from(BreakClassificationValue.class);
            subquery.select(cb.literal(1L));
            subquery.where(
                    cb.equal(subRoot.get("breakItem"), root),
                    cb.like(cb.lower(subRoot.get("attributeValue")), likeTerm));

            predicates.add(cb.or(byId, byStatus, cb.exists(subquery)));
        }

        if (!accessEntries.isEmpty()) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<AccessControlEntry> aceRoot = subquery.from(AccessControlEntry.class);
            subquery.select(aceRoot.get("id"));
            subquery.where(
                    aceRoot.in(accessEntries),
                    matchesAccessScope(cb, aceRoot, root));
            predicates.add(cb.exists(subquery));
        }

        query.select(root).where(predicates.toArray(Predicate[]::new));
        query.orderBy(cb.desc(runJoin.get("runDateTime")), cb.desc(root.get("id")));
        query.distinct(true);

        TypedQuery<BreakItem> typedQuery = entityManager.createQuery(query);
        typedQuery.setMaxResults(criteria.pageSize() + 1);
        List<BreakItem> items = typedQuery.getResultList();

        boolean hasMore = items.size() > criteria.pageSize();
        if (hasMore) {
            items = items.subList(0, criteria.pageSize());
        }

        Map<Long, ReconciliationRun> runLookup = fetchRuns(items);
        List<BreakSearchRow> rows = items.stream()
                .map(item -> toRow(item, runLookup.get(item.getRun().getId()), definition, accessEntries))
                .toList();

        BreakSearchCursor nextCursor = null;
        if (hasMore && !rows.isEmpty()) {
            BreakSearchRow last = rows.get(rows.size() - 1);
            nextCursor = new BreakSearchCursor(last.runDateTime(), last.breakId());
        }

        long totalCount = criteria.includeTotals() ? countTotal(definition, criteria, accessEntries) : -1L;

        List<GridColumnDto> columns = buildColumnMetadata(definition);

        return new BreakSearchResult(rows, nextCursor, hasMore, totalCount, columns);
    }

    private Map<Long, ReconciliationRun> fetchRuns(List<BreakItem> items) {
        Set<Long> runIds = items.stream().map(item -> item.getRun().getId()).collect(Collectors.toSet());
        if (runIds.isEmpty()) {
            return Map.of();
        }
        return runRepository.findAllById(runIds).stream()
                .collect(Collectors.toMap(ReconciliationRun::getId, run -> run));
    }

    private Predicate buildClassificationPredicate(
            CriteriaBuilder cb, CriteriaQuery<?> query, Root<BreakItem> root, BreakColumnFilterDto filter) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<BreakClassificationValue> subRoot = subquery.from(BreakClassificationValue.class);
        subquery.select(cb.literal(1L));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(subRoot.get("breakItem"), root));
        predicates.add(cb.equal(subRoot.get("attributeKey"), filter.key()));

        List<String> values = filter.values();
        if (values != null && !values.isEmpty()) {
            FilterOperator operator = filter.operator();
            switch (operator) {
                case EQUALS:
                    predicates.add(subRoot.get("attributeValue").in(values));
                    break;
                case NOT_EQUALS:
                    predicates.add(cb.not(subRoot.get("attributeValue").in(values)));
                    break;
                case GREATER_THAN:
                    predicates.add(cb.greaterThan(subRoot.get("attributeValue"), values.get(0)));
                    break;
                case GREATER_THAN_OR_EQUALS:
                    predicates.add(cb.greaterThanOrEqualTo(subRoot.get("attributeValue"), values.get(0)));
                    break;
                case LESS_THAN:
                    predicates.add(cb.lessThan(subRoot.get("attributeValue"), values.get(0)));
                    break;
                case LESS_THAN_OR_EQUALS:
                    predicates.add(cb.lessThanOrEqualTo(subRoot.get("attributeValue"), values.get(0)));
                    break;
                case BETWEEN:
                    if (values.size() < 2) {
                        throw new IllegalArgumentException("Between operator requires two values");
                    }
                    predicates.add(cb.between(subRoot.get("attributeValue"), values.get(0), values.get(1)));
                    break;
                case CONTAINS: {
                    List<Predicate> orPredicates = values.stream()
                            .map(value -> cb.like(
                                    cb.lower(subRoot.get("attributeValue")),
                                    "%" + value.toLowerCase(Locale.ROOT) + "%"))
                            .toList();
                    predicates.add(cb.or(orPredicates.toArray(Predicate[]::new)));
                    break;
                }
                case STARTS_WITH: {
                    List<Predicate> orPredicates = values.stream()
                            .map(value -> cb.like(
                                    cb.lower(subRoot.get("attributeValue")),
                                    value.toLowerCase(Locale.ROOT) + "%"))
                            .toList();
                    predicates.add(cb.or(orPredicates.toArray(Predicate[]::new)));
                    break;
                }
                case ENDS_WITH: {
                    List<Predicate> orPredicates = values.stream()
                            .map(value -> cb.like(
                                    cb.lower(subRoot.get("attributeValue")),
                                    "%" + value.toLowerCase(Locale.ROOT)))
                            .toList();
                    predicates.add(cb.or(orPredicates.toArray(Predicate[]::new)));
                    break;
                }
                case IN:
                    predicates.add(subRoot.get("attributeValue").in(values));
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Operator not supported for classification filter: " + operator);
            }
        }

        subquery.where(predicates.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }

    private BreakSearchRow toRow(
            BreakItem item, ReconciliationRun run, ReconciliationDefinition definition, List<AccessControlEntry> accessEntries) {
        List<BreakStatus> allowedStatuses = breakAccessService.allowedStatuses(item, definition, accessEntries);
        BreakItemDto dto = breakMapper.toDto(item, allowedStatuses);
        Map<String, String> attributes = item.getClassificationValues().stream()
                .collect(Collectors.toMap(
                        BreakClassificationValue::getAttributeKey,
                        BreakClassificationValue::getAttributeValue,
                        (left, right) -> right,
                        LinkedHashMap::new));
        String zone = DISPLAY_ZONE.getId();
        TriggerType triggerType = run != null ? run.getTriggerType() : TriggerType.MANUAL_API;
        Instant runDateTime = run != null ? run.getRunDateTime() : item.getDetectedAt();
        return new BreakSearchRow(item.getId(),
                run != null ? run.getId() : null,
                runDateTime,
                zone,
                triggerType,
                dto,
                attributes);
    }

    private long countTotal(
            ReconciliationDefinition definition, BreakSearchCriteria criteria, List<AccessControlEntry> accessEntries) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<BreakItem> root = countQuery.from(BreakItem.class);
        var runJoin = root.join("run");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(runJoin.get("definition"), definition));
        if (criteria.fromDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(runJoin.get("runDateTime"), criteria.fromDate()));
        }
        if (criteria.toDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(runJoin.get("runDateTime"), criteria.toDate()));
        }
        if (!criteria.runIds().isEmpty()) {
            predicates.add(runJoin.get("id").in(criteria.runIds()));
        }
        if (!criteria.triggerTypes().isEmpty()) {
            predicates.add(runJoin.get("triggerType").in(criteria.triggerTypes()));
        }
        if (!criteria.statuses().isEmpty()) {
            predicates.add(root.get("status").in(criteria.statuses()));
        }

        if (criteria.hasColumnFilters()) {
            for (BreakColumnFilterDto filter : criteria.columnFilterValues()) {
                predicates.add(buildClassificationPredicate(cb, countQuery, root, filter));
            }
        }

        if (criteria.searchTerm() != null && !criteria.searchTerm().isBlank()) {
            String likeTerm = "%" + criteria.searchTerm().toLowerCase(Locale.ROOT) + "%";
            Predicate byId = cb.like(cb.lower(root.get("id").as(String.class)), likeTerm);
            Predicate byStatus = cb.like(cb.lower(root.get("status")), likeTerm);

            Subquery<Long> subqueryMatches = countQuery.subquery(Long.class);
            Root<BreakClassificationValue> subRoot = subqueryMatches.from(BreakClassificationValue.class);
            subqueryMatches.select(cb.literal(1L));
            subqueryMatches.where(
                    cb.equal(subRoot.get("breakItem"), root),
                    cb.like(cb.lower(subRoot.get("attributeValue")), likeTerm));

            predicates.add(cb.or(byId, byStatus, cb.exists(subqueryMatches)));
        }

        if (!accessEntries.isEmpty()) {
            Subquery<Long> subquery = countQuery.subquery(Long.class);
            Root<AccessControlEntry> aceRoot = subquery.from(AccessControlEntry.class);
            subquery.select(aceRoot.get("id"));
            subquery.where(
                    aceRoot.in(accessEntries),
                    matchesAccessScope(cb, aceRoot, root));
            predicates.add(cb.exists(subquery));
        }

        countQuery.select(cb.countDistinct(root)).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private Predicate matchesAccessScope(CriteriaBuilder cb, Root<AccessControlEntry> aceRoot, Root<BreakItem> breakRoot) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.or(
                cb.isNull(aceRoot.get("product")),
                cb.equal(aceRoot.get("product"), breakRoot.get("product"))));
        predicates.add(cb.or(
                cb.isNull(aceRoot.get("subProduct")),
                cb.equal(aceRoot.get("subProduct"), breakRoot.get("subProduct"))));
        predicates.add(cb.or(
                cb.isNull(aceRoot.get("entityName")),
                cb.equal(aceRoot.get("entityName"), breakRoot.get("entityName"))));
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private List<GridColumnDto> buildColumnMetadata(ReconciliationDefinition definition) {
        return definition.getCanonicalFields().stream()
                .sorted((a, b) -> Integer.compare(
                        a.getDisplayOrder() != null ? a.getDisplayOrder() : Integer.MAX_VALUE,
                        b.getDisplayOrder() != null ? b.getDisplayOrder() : Integer.MAX_VALUE))
                .map(this::toColumn)
                .toList();
    }

    private GridColumnDto toColumn(CanonicalField field) {
        FieldDataType dataType = field.getDataType();
        List<FilterOperator> operators;
        if (dataType == FieldDataType.BOOLEAN) {
            operators = List.of(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS);
        } else if (dataType == FieldDataType.DECIMAL || dataType == FieldDataType.INTEGER) {
            operators = List.of(
                    FilterOperator.EQUALS,
                    FilterOperator.NOT_EQUALS,
                    FilterOperator.GREATER_THAN,
                    FilterOperator.LESS_THAN);
        } else if (dataType == FieldDataType.DATE || dataType == FieldDataType.DATETIME) {
            operators = List.of(
                    FilterOperator.BETWEEN,
                    FilterOperator.GREATER_THAN,
                    FilterOperator.LESS_THAN);
        } else {
            operators = List.of(FilterOperator.EQUALS, FilterOperator.CONTAINS, FilterOperator.STARTS_WITH);
        }
        String resolvedType = mapDataType(field.getDataType());
        return new GridColumnDto(field.getCanonicalName(), field.getDisplayName(), resolvedType, operators, true, true);
    }

    private String mapDataType(FieldDataType dataType) {
        if (dataType == FieldDataType.BOOLEAN) {
            return "boolean";
        }
        if (dataType == FieldDataType.INTEGER || dataType == FieldDataType.DECIMAL) {
            return "number";
        }
        if (dataType == FieldDataType.DATE) {
            return "date";
        }
        if (dataType == FieldDataType.DATETIME) {
            return "datetime";
        }
        return "string";
    }
}
