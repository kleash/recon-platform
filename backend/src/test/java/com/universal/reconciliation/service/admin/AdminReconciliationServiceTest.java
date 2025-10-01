package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.AdminAccessControlEntryRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldRequest;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionBatchDto;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationDetailDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPageDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPatchRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationSchemaDto;
import com.universal.reconciliation.domain.dto.admin.AdminSourceRequest;
import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.CanonicalFieldMapping;
import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.entity.SourceDataBatch;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.domain.enums.TransformationType;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.service.SystemActivityService;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import com.universal.reconciliation.service.ingestion.SourceIngestionService;
import com.universal.reconciliation.service.transform.DataTransformationService;
import com.universal.reconciliation.service.transform.SourceTransformationPlanMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class AdminReconciliationServiceTest {

    @Mock
    private ReconciliationDefinitionRepository definitionRepository;

    @Mock
    private SystemActivityService systemActivityService;

    @Mock
    private AdminReconciliationValidator validator;

    @Mock
    private SourceIngestionService sourceIngestionService;

    @Mock
    private DataTransformationService transformationService;

    private AdminReconciliationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AdminReconciliationService(
                definitionRepository,
                systemActivityService,
                validator,
                sourceIngestionService,
                transformationService,
                new SourceTransformationPlanMapper(new ObjectMapper()));
    }

    @Test
    void list_appliesPaginationAndMapsSummaries() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(11L);
        definition.setCode("CUSTODY_GL");
        definition.setName("Custody vs GL");
        definition.setDescription("Custody vs general ledger");
        definition.setMakerCheckerEnabled(true);
        definition.setStatus(ReconciliationLifecycleStatus.PUBLISHED);
        definition.setOwnedBy("ops-team");
        definition.setUpdatedBy("admin.user");
        Instant updatedAt = Instant.parse("2024-05-02T09:15:30Z");
        definition.setCreatedAt(updatedAt.minusSeconds(3600));
        definition.setUpdatedAt(updatedAt);

        ReconciliationSource source = new ReconciliationSource();
        source.setDefinition(definition);
        source.setCode("CUSTODY");
        source.setDisplayName("Custody Feed");
        source.setAdapterType(IngestionAdapterType.CSV_FILE);
        source.setAnchor(true);
        SourceDataBatch batch = new SourceDataBatch();
        Instant ingestedAt = Instant.parse("2024-05-01T10:00:00Z");
        batch.setIngestedAt(ingestedAt);
        source.getBatches().add(batch);
        definition.getSources().add(source);

        PageImpl<ReconciliationDefinition> page =
                new PageImpl<>(List.of(definition), PageRequest.of(1, 20), 21);
        when(definitionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        AdminReconciliationPageDto result =
                service.list(ReconciliationLifecycleStatus.PUBLISHED, "ops", null, null, "custody", -1, 0);

        verify(definitionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(1);
        assertThat(pageable.getSort().getOrderFor("updatedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("updatedAt").isDescending()).isTrue();

        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0)).satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(11L);
            assertThat(summary.code()).isEqualTo("CUSTODY_GL");
            assertThat(summary.status()).isEqualTo(ReconciliationLifecycleStatus.PUBLISHED);
            assertThat(summary.owner()).isEqualTo("ops-team");
            assertThat(summary.updatedBy()).isEqualTo("admin.user");
            assertThat(summary.lastIngestionAt()).isEqualTo(ingestedAt);
        });
    }

    @Test
    void create_persistsNewDefinitionWithChildren() {
        when(definitionRepository.findByCode("CUSTODY_GL")).thenReturn(Optional.empty());
        when(definitionRepository.save(any())).thenAnswer(invocation -> {
            ReconciliationDefinition definition = invocation.getArgument(0);
            definition.setId(101L);
            long sourceId = 201L;
            for (ReconciliationSource source : definition.getSources()) {
                source.setId(sourceId++);
            }
            long fieldId = 301L;
            for (CanonicalField field : definition.getCanonicalFields()) {
                field.setId(fieldId++);
                long mappingId = 401L;
                for (CanonicalFieldMapping mapping : field.getMappings()) {
                    mapping.setId(mappingId++);
                }
            }
            return definition;
        });

        AdminReconciliationRequest request = new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                "Custody vs general ledger",
                "ops-team",
                true,
                "Pilot rollout",
                ReconciliationLifecycleStatus.DRAFT,
                false,
                null,
                null,
                null,
                null,
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody Feed",
                                IngestionAdapterType.CSV_FILE,
                                true,
                                "Custody",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        new AdminSourceRequest(
                                null,
                                "GL",
                                "General Ledger",
                                IngestionAdapterType.DATABASE,
                                false,
                                "Ledger",
                                "{}",
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(new AdminCanonicalFieldRequest(
                        null,
                        "tradeId",
                        "Trade ID",
                        FieldRole.KEY,
                        FieldDataType.STRING,
                        ComparisonLogic.EXACT_MATCH,
                        null,
                        null,
                        null,
                        1,
                        true,
                        List.of(
                                new AdminCanonicalFieldMappingRequest(
                                        null, "CUSTODY", "trade_id", null, null, 1, true, List.of()),
                                new AdminCanonicalFieldMappingRequest(
                                        null, "GL", "trade_id", null, null, 1, true, List.of())))),
                List.of(),
                List.of(new AdminAccessControlEntryRequest(
                        null,
                        "CN=RECON_ADMIN,OU=Groups,DC=corp,DC=example",
                        AccessRole.MAKER,
                        null,
                        null,
                        null,
                        true,
                        true,
                        null)));

        AdminReconciliationDetailDto detail = service.create(request, "admin.user");

        assertThat(detail.code()).isEqualTo("CUSTODY_GL");
        assertThat(detail.owner()).isEqualTo("ops-team");
        assertThat(detail.autoTriggerEnabled()).isFalse();
        assertThat(detail.sources()).hasSize(2);
        assertThat(detail.canonicalFields()).hasSize(1);
        assertThat(detail.canonicalFields().get(0).mappings()).hasSize(2);
        assertThat(detail.accessControlEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.ldapGroupDn()).contains("RECON_ADMIN");
            assertThat(entry.role()).isEqualTo(AccessRole.MAKER);
            assertThat(entry.notifyOnPublish()).isTrue();
        });

        ArgumentCaptor<ReconciliationDefinition> definitionCaptor =
                ArgumentCaptor.forClass(ReconciliationDefinition.class);
        verify(definitionRepository).save(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getStatus()).isEqualTo(ReconciliationLifecycleStatus.DRAFT);
        verify(systemActivityService)
                .recordEvent(SystemEventType.RECONCILIATION_CONFIG_CHANGE, "Reconciliation CUSTODY_GL created by admin.user");
        verify(validator).validate(request);
    }

    @Test
    void update_throwsOptimisticLockingWhenVersionMismatch() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(77L);
        definition.setCode("CUSTODY_GL");
        definition.setVersion(2L);
        when(definitionRepository.findById(77L)).thenReturn(Optional.of(definition));

        AdminReconciliationRequest request = new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                "Custody vs general ledger",
                null,
                true,
                null,
                ReconciliationLifecycleStatus.DRAFT,
                false,
                null,
                null,
                null,
                1L,
                List.of(new AdminSourceRequest(
                        null,
                        "CUSTODY",
                        "Custody Feed",
                        IngestionAdapterType.CSV_FILE,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                List.of(new AdminCanonicalFieldRequest(
                        null,
                        "tradeId",
                        "Trade ID",
                        FieldRole.KEY,
                        FieldDataType.STRING,
                        ComparisonLogic.EXACT_MATCH,
                        null,
                        null,
                        null,
                        1,
                        true,
                        List.of())),
                List.of(),
                List.of());

        assertThatThrownBy(() -> service.update(77L, request, "admin.user"))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(systemActivityService, never()).recordEvent(any(), any());
        verify(validator).validate(request);
    }

    @Test
    void exportSchema_projectsCanonicalMappings() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(501L);
        definition.setCode("CUSTODY_GL");
        definition.setName("Custody vs GL");

        ReconciliationSource source = new ReconciliationSource();
        source.setDefinition(definition);
        source.setId(601L);
        source.setCode("CUSTODY");
        source.setDisplayName("Custody Feed");
        source.setAdapterType(IngestionAdapterType.CSV_FILE);
        source.setAnchor(true);
        source.setConnectionConfig("{\"bucket\":\"custody\"}");
        source.setArrivalExpectation("Daily 6am Eastern");
        source.setArrivalTimezone("America/New_York");
        source.setArrivalSlaMinutes(90);
        source.setAdapterOptions("{\"delimiter\":\"comma\"}");
        definition.getSources().add(source);

        CanonicalField field = new CanonicalField();
        field.setDefinition(definition);
        field.setId(701L);
        field.setCanonicalName("tradeId");
        field.setDisplayName("Trade ID");
        field.setRole(FieldRole.KEY);
        field.setDataType(FieldDataType.STRING);
        field.setComparisonLogic(ComparisonLogic.EXACT_MATCH);
        field.setThresholdPercentage(new BigDecimal("0.5"));
        field.setFormattingHint("Uppercase");
        field.setRequired(true);

        CanonicalFieldMapping mapping = new CanonicalFieldMapping();
        mapping.setCanonicalField(field);
        mapping.setSource(source);
        mapping.setSourceColumn("trade_id");
        mapping.setTransformationExpression("TRIM(trade_id)");
        mapping.setDefaultValue("UNKNOWN");
        mapping.setOrdinalPosition(1);
        mapping.setRequired(true);
        CanonicalFieldTransformation transformation = new CanonicalFieldTransformation();
        transformation.setMapping(mapping);
        transformation.setType(TransformationType.FUNCTION_PIPELINE);
        transformation.setConfiguration("{\"steps\":[{\"function\":\"TRIM\"}]}");
        mapping.getTransformations().add(transformation);
        field.getMappings().add(mapping);
        source.getFieldMappings().add(mapping);
        definition.getCanonicalFields().add(field);

        when(definitionRepository.findById(501L)).thenReturn(Optional.of(definition));

        AdminReconciliationSchemaDto schema = service.exportSchema(501L);

        assertThat(schema.definitionId()).isEqualTo(501L);
        assertThat(schema.code()).isEqualTo("CUSTODY_GL");
        assertThat(schema.sources()).singleElement().satisfies(sourceDto -> {
            assertThat(sourceDto.code()).isEqualTo("CUSTODY");
            assertThat(sourceDto.adapterType()).isEqualTo(IngestionAdapterType.CSV_FILE);
            assertThat(sourceDto.anchor()).isTrue();
            assertThat(sourceDto.connectionConfig()).isEqualTo("{\"bucket\":\"custody\"}");
            assertThat(sourceDto.arrivalExpectation()).isEqualTo("Daily 6am Eastern");
            assertThat(sourceDto.arrivalTimezone()).isEqualTo("America/New_York");
            assertThat(sourceDto.arrivalSlaMinutes()).isEqualTo(90);
            assertThat(sourceDto.adapterOptions()).isEqualTo("{\"delimiter\":\"comma\"}");
            assertThat(sourceDto.ingestionEndpoint()).isEqualTo("/api/admin/reconciliations/501/sources/CUSTODY/batches");
        });
        assertThat(schema.fields()).hasSize(1);
        AdminReconciliationSchemaDto.SchemaFieldDto schemaField = schema.fields().get(0);
        assertThat(schemaField.canonicalName()).isEqualTo("tradeId");
        assertThat(schemaField.displayName()).isEqualTo("Trade ID");
        assertThat(schemaField.role()).isEqualTo(FieldRole.KEY);
        assertThat(schemaField.dataType()).isEqualTo(FieldDataType.STRING);
        assertThat(schemaField.comparisonLogic()).isEqualTo(ComparisonLogic.EXACT_MATCH);
        assertThat(schemaField.thresholdPercentage()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(schemaField.formattingHint()).isEqualTo("Uppercase");
        assertThat(schemaField.required()).isTrue();
        assertThat(schemaField.mappings()).singleElement().satisfies(m -> {
            assertThat(m.sourceCode()).isEqualTo("CUSTODY");
            assertThat(m.sourceColumn()).isEqualTo("trade_id");
            assertThat(m.transformationExpression()).isEqualTo("TRIM(trade_id)");
            assertThat(m.defaultValue()).isEqualTo("UNKNOWN");
            assertThat(m.ordinalPosition()).isEqualTo(1);
            assertThat(m.required()).isTrue();
            assertThat(m.transformations()).hasSize(1);
            assertThat(m.transformations().get(0).type()).isEqualTo("FUNCTION_PIPELINE");
        });
    }

    @Test
    void ingest_delegatesToSourceIngestionService() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(15L);
        definition.setCode("CUSTODY_GL");

        ReconciliationSource source = new ReconciliationSource();
        source.setCode("CUSTODY");
        source.setAdapterType(IngestionAdapterType.CSV_FILE);
        source.setDefinition(definition);
        definition.getSources().add(source);

        when(definitionRepository.findById(15L)).thenReturn(Optional.of(definition));

        SourceDataBatch batch = new SourceDataBatch();
        batch.setId(31L);
        batch.setLabel("custody-batch");
        batch.setStatus(DataBatchStatus.COMPLETE);
        batch.setRecordCount(10L);
        batch.setChecksum(UUID.randomUUID().toString());

        when(sourceIngestionService.ingest(eq(definition), eq("CUSTODY"), eq(IngestionAdapterType.CSV_FILE), any()))
                .thenReturn(batch);

        AdminIngestionRequest metadata = new AdminIngestionRequest(IngestionAdapterType.CSV_FILE, null, "custody-batch");
        AdminIngestionBatchDto response =
                service.ingest(15L, "CUSTODY", metadata, new IngestionAdapterRequest(() -> null, null), "admin.user");

        assertThat(response.id()).isEqualTo(31L);
        assertThat(response.label()).isEqualTo("custody-batch");
        verify(systemActivityService)
                .recordEvent(
                        eq(SystemEventType.INGESTION_BATCH_ACCEPTED),
                        org.mockito.ArgumentMatchers.contains("Ingestion batch"));
    }

    @Test
    void patch_updatesLifecycleAndNotes() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setId(88L);
        definition.setCode("CUSTODY_GL");
        definition.setStatus(ReconciliationLifecycleStatus.DRAFT);
        when(definitionRepository.findById(88L)).thenReturn(Optional.of(definition));

        AdminReconciliationPatchRequest request = new AdminReconciliationPatchRequest(
                ReconciliationLifecycleStatus.PUBLISHED,
                Boolean.FALSE,
                "Updated notes",
                null,
                null,
                null,
                null,
                null);

        AdminReconciliationDetailDto detail = service.patch(88L, request, "admin.user");

        assertThat(detail.status()).isEqualTo(ReconciliationLifecycleStatus.PUBLISHED);
        assertThat(detail.notes()).isEqualTo("Updated notes");
        assertThat(definition.getPublishedAt()).isNotNull();
        verify(systemActivityService)
                .recordEvent(SystemEventType.RECONCILIATION_CONFIG_CHANGE, "Reconciliation CUSTODY_GL patched by admin.user");
    }
}
