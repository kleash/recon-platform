package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldRequest;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionBatchDto;
import com.universal.reconciliation.domain.dto.admin.AdminIngestionRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationDetailDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPageDto;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationPatchRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationSchemaDto;
import com.universal.reconciliation.domain.dto.admin.AdminReportColumnRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReportTemplateRequest;
import com.universal.reconciliation.domain.dto.admin.AdminSourceRequest;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.DataBatchStatus;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import com.universal.reconciliation.domain.enums.ReportColumnSource;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceDataBatchRepository;
import com.universal.reconciliation.repository.SystemActivityLogRepository;
import com.universal.reconciliation.service.ingestion.IngestionAdapterRequest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for {@link AdminReconciliationService}. The test exercises the primary
 * lifecycle flows (create, update, publish, schema export, ingestion) against a real in-memory
 * database to guard against mapping regressions.
 */
@SpringBootTest
@Transactional
class AdminReconciliationServiceIntegrationTest {

    @Autowired
    private AdminReconciliationService adminReconciliationService;

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private SystemActivityLogRepository activityLogRepository;

    @Autowired
    private SourceDataBatchRepository batchRepository;

    @Test
    void createPublishExportAndIngestDefinition() {
        long definitionsBefore = definitionRepository.count();
        long batchesBefore = batchRepository.count();
        long activityBefore = activityLogRepository.count();

        AdminReconciliationRequest createRequest = buildRequest(
                "Custody vs General Ledger",
                "Pilot created via integration test",
                null);

        AdminReconciliationDetailDto created =
                adminReconciliationService.create(createRequest, "admin.user");

        assertThat(created.id()).isNotNull();
        assertThat(created.sources()).hasSize(2);
        assertThat(created.canonicalFields()).hasSize(3);
        assertThat(created.status()).isEqualTo(ReconciliationLifecycleStatus.DRAFT);

        AdminReconciliationRequest updateRequest = buildRequest(
                "Custody vs General Ledger",
                "Updated via integration test",
                created.version());

        AdminReconciliationDetailDto updated =
                adminReconciliationService.update(created.id(), updateRequest, "admin.user");

        assertThat(updated.notes()).isEqualTo("Updated via integration test");
        assertThat(updated.version()).isNotNull();

        AdminReconciliationDetailDto published = adminReconciliationService.patch(
                created.id(),
                new AdminReconciliationPatchRequest(
                        ReconciliationLifecycleStatus.PUBLISHED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "admin.user");

        assertThat(published.status()).isEqualTo(ReconciliationLifecycleStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();

        AdminReconciliationSchemaDto schema =
                adminReconciliationService.exportSchema(published.id());
        assertThat(schema.sources()).extracting(AdminReconciliationSchemaDto.SchemaSourceDto::code)
                .containsExactly("CUSTODY_FEED", "GL_LEDGER");
        assertThat(schema.fields()).extracting(AdminReconciliationSchemaDto.SchemaFieldDto::canonicalName)
                .contains("tradeId", "netAmount", "currency");

        AdminIngestionRequest ingestionMetadata = new AdminIngestionRequest(
                IngestionAdapterType.CSV_FILE,
                Map.of("delimiter", ","),
                "Custody batch");

        String csvPayload = "trade_id,net_amount,currency\nTRD-1,100.25,USD\n";
        Map<String, Object> adapterOptions = new java.util.LinkedHashMap<>();
        adapterOptions.put("delimiter", ",");
        adapterOptions.put("label", "Custody batch");
        IngestionAdapterRequest adapterRequest = new IngestionAdapterRequest(
                () -> new ByteArrayInputStream(csvPayload.getBytes(StandardCharsets.UTF_8)),
                adapterOptions);

        AdminIngestionBatchDto batch = adminReconciliationService.ingest(
                published.id(),
                "CUSTODY_FEED",
                ingestionMetadata,
                adapterRequest,
                "admin.user");

        assertThat(batch.status()).isEqualTo(DataBatchStatus.COMPLETE);
        assertThat(batch.recordCount()).isEqualTo(1L);

        AdminReconciliationDetailDto withIngestion = adminReconciliationService.get(published.id());
        assertThat(withIngestion.ingestionBatches()).isNotEmpty();
        assertThat(withIngestion.ingestionBatches().get(0).label()).isEqualTo("Custody batch");

        AdminReconciliationPageDto page = adminReconciliationService.list(
                null, null, null, null, "custody", 0, 10);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.items()).extracting(item -> item.code()).contains("CUSTODY_GL");

        assertThat(definitionRepository.count()).isEqualTo(definitionsBefore + 1);
        assertThat(batchRepository.count()).isEqualTo(batchesBefore + 1);
        assertThat(activityLogRepository.count()).isGreaterThanOrEqualTo(activityBefore + 3);
    }

    private AdminReconciliationRequest buildRequest(
            String description, String notes, Long version) {
        AdminSourceRequest custodySource = new AdminSourceRequest(
                null,
                "CUSTODY_FEED",
                "Custody CSV",
                IngestionAdapterType.CSV_FILE,
                true,
                "Daily custody export",
                "s3://bucket/custody",
                "Weekdays by 18:00",
                "America/New_York",
                60,
                "{\"delimiter\":\",\"}");
        AdminSourceRequest ledgerSource = new AdminSourceRequest(
                null,
                "GL_LEDGER",
                "General Ledger",
                IngestionAdapterType.CSV_FILE,
                false,
                "Ledger staging extract",
                null,
                null,
                null,
                null,
                null);

        AdminCanonicalFieldRequest tradeId = new AdminCanonicalFieldRequest(
                null,
                "tradeId",
                "Trade ID",
                FieldRole.KEY,
                FieldDataType.STRING,
                ComparisonLogic.EXACT_MATCH,
                null,
                null,
                null,
                0,
                true,
                List.of(
                        new AdminCanonicalFieldMappingRequest(
                                null, "CUSTODY_FEED", "trade_id", null, null, 0, true, List.of()),
                        new AdminCanonicalFieldMappingRequest(
                                null, "GL_LEDGER", "trade_id", null, null, 0, true, List.of())));

        AdminCanonicalFieldRequest netAmount = new AdminCanonicalFieldRequest(
                null,
                "netAmount",
                "Net Amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                new BigDecimal("0.5"),
                null,
                null,
                1,
                true,
                List.of(
                        new AdminCanonicalFieldMappingRequest(
                                null, "CUSTODY_FEED", "net_amount", null, null, 0, true, List.of()),
                        new AdminCanonicalFieldMappingRequest(
                                null, "GL_LEDGER", "net_amount", null, null, 0, true, List.of())));

        AdminCanonicalFieldRequest currency = new AdminCanonicalFieldRequest(
                null,
                "currency",
                "Currency",
                FieldRole.CLASSIFIER,
                FieldDataType.STRING,
                ComparisonLogic.CASE_INSENSITIVE,
                null,
                null,
                null,
                2,
                true,
                List.of(
                        new AdminCanonicalFieldMappingRequest(
                                null, "CUSTODY_FEED", "currency", null, null, 0, true, List.of()),
                        new AdminCanonicalFieldMappingRequest(
                                null, "GL_LEDGER", "currency", null, null, 0, true, List.of())));

        AdminReportTemplateRequest reportTemplate = new AdminReportTemplateRequest(
                null,
                "Break Investigation",
                "Highlights mismatches for custody vs ledger",
                false,
                true,
                true,
                true,
                List.of(
                        new AdminReportColumnRequest(
                                null, "Trade ID", ReportColumnSource.SOURCE_A, "tradeId", 0, false),
                        new AdminReportColumnRequest(
                                null,
                                "Net Amount (Custody)",
                                ReportColumnSource.SOURCE_A,
                                "netAmount",
                                1,
                                true)));

        return new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                description,
                "Custody Operations",
                true,
                notes,
                ReconciliationLifecycleStatus.DRAFT,
                true,
                "0 2 * * *",
                "UTC",
                45,
                version,
                List.of(custodySource, ledgerSource),
                List.of(tradeId, netAmount, currency),
                List.of(reportTemplate),
                List.of(new com.universal.reconciliation.domain.dto.admin.AdminAccessControlEntryRequest(
                        null,
                        "cn=ROLE_RECON_ADMIN,ou=groups,dc=universal,dc=local",
                        AccessRole.MAKER,
                        "Custody",
                        "Settlements",
                        "Global",
                        true,
                        true,
                        "custody-ops@example.com")));
    }
}
