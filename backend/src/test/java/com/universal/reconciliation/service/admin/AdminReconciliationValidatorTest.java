package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.universal.reconciliation.domain.dto.admin.AdminAccessControlEntryRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldMappingRequest;
import com.universal.reconciliation.domain.dto.admin.AdminCanonicalFieldRequest;
import com.universal.reconciliation.domain.dto.admin.AdminReconciliationRequest;
import com.universal.reconciliation.domain.dto.admin.AdminSourceRequest;
import com.universal.reconciliation.domain.enums.AccessRole;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.domain.enums.IngestionAdapterType;
import com.universal.reconciliation.domain.enums.ReconciliationLifecycleStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminReconciliationValidatorTest {

    private final AdminReconciliationValidator validator = new AdminReconciliationValidator();

    @Test
    void validate_acceptsValidConfiguration() {
        AdminReconciliationRequest request = buildRequest(
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody",
                                IngestionAdapterType.CSV_FILE,
                                true,
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
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(buildKeyField()));

        assertThatNoException().isThrownBy(() -> validator.validate(request));
    }

    @Test
    void validate_rejectsMultipleAnchorSources() {
        AdminReconciliationRequest request = buildRequest(
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody",
                                IngestionAdapterType.CSV_FILE,
                                true,
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
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(buildKeyField()));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single anchor");
    }

    @Test
    void validate_rejectsMissingKeyField() {
        AdminCanonicalFieldRequest compareField = new AdminCanonicalFieldRequest(
                null,
                "netAmount",
                "Net Amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                new BigDecimal("0.1"),
                null,
                null,
                2,
                true,
                List.of(new AdminCanonicalFieldMappingRequest(
                        null, "CUSTODY", "net_amount", null, null, 1, true, List.of())));

        AdminReconciliationRequest request = buildRequest(
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody",
                                IngestionAdapterType.CSV_FILE,
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(compareField));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KEY role");
    }

    @Test
    void validate_rejectsMappingToUnknownSource() {
        AdminCanonicalFieldRequest keyField = new AdminCanonicalFieldRequest(
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
                List.of(new AdminCanonicalFieldMappingRequest(
                        null, "UNKNOWN", "trade_id", null, null, 1, true, List.of())));

        AdminReconciliationRequest request = buildRequest(
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody",
                                IngestionAdapterType.CSV_FILE,
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(keyField));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown source code");
    }

    @Test
    void validate_rejectsMissingThresholdForNumericComparison() {
        AdminCanonicalFieldRequest compareField = new AdminCanonicalFieldRequest(
                null,
                "netAmount",
                "Net Amount",
                FieldRole.COMPARE,
                FieldDataType.DECIMAL,
                ComparisonLogic.NUMERIC_THRESHOLD,
                null,
                null,
                null,
                2,
                true,
                List.of(new AdminCanonicalFieldMappingRequest(
                        null, "CUSTODY", "net_amount", null, null, 1, true, List.of())));

        AdminReconciliationRequest request = buildRequest(
                List.of(
                        new AdminSourceRequest(
                                null,
                                "CUSTODY",
                                "Custody",
                                IngestionAdapterType.CSV_FILE,
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                List.of(compareField, buildKeyField()));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thresholdPercentage");
    }

    @Test
    void validate_rejectsNegativeArrivalSla() {
        AdminSourceRequest source = new AdminSourceRequest(
                null,
                "CUSTODY",
                "Custody",
                IngestionAdapterType.CSV_FILE,
                true,
                null,
                null,
                null,
                null,
                -5,
                null);

        AdminReconciliationRequest request = buildRequest(List.of(source), List.of(buildKeyField()));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Arrival SLA minutes");
    }

    @Test
    void validate_requiresCronWhenAutoTriggerEnabled() {
        AdminReconciliationRequest request = new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                "Custody vs general ledger",
                "ops-team",
                true,
                "Pilot",
                ReconciliationLifecycleStatus.DRAFT,
                true,
                null,
                "UTC",
                15,
                null,
                List.of(new AdminSourceRequest(
                        null,
                        "CUSTODY",
                        "Custody",
                        IngestionAdapterType.CSV_FILE,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                List.of(buildKeyField()),
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

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron expression");
    }

    @Test
    void validate_requiresTimezoneWhenAutoTriggerEnabled() {
        AdminReconciliationRequest request = new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                "Custody vs general ledger",
                "ops-team",
                true,
                "Pilot",
                ReconciliationLifecycleStatus.DRAFT,
                true,
                "0 0 * * *",
                null,
                15,
                null,
                List.of(new AdminSourceRequest(
                        null,
                        "CUSTODY",
                        "Custody",
                        IngestionAdapterType.CSV_FILE,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                List.of(buildKeyField()),
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

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timezone");
    }

    private AdminCanonicalFieldRequest buildKeyField() {
        return new AdminCanonicalFieldRequest(
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
                                null, "CUSTODY", "trade_id", null, null, 1, true, List.of())));
    }

    private AdminReconciliationRequest buildRequest(
            List<AdminSourceRequest> sources, List<AdminCanonicalFieldRequest> fields) {
        return new AdminReconciliationRequest(
                "CUSTODY_GL",
                "Custody vs GL",
                "Custody vs general ledger",
                "ops-team",
                true,
                "Pilot",
                ReconciliationLifecycleStatus.DRAFT,
                false,
                null,
                null,
                null,
                null,
                sources,
                fields,
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
    }
}
