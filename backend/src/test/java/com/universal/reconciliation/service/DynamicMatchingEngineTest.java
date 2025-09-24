package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.entity.CanonicalField;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationSource;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.service.matching.BreakCandidate;
import com.universal.reconciliation.service.matching.DynamicMatchingEngine;
import com.universal.reconciliation.service.matching.DynamicReconciliationContext;
import com.universal.reconciliation.service.matching.DynamicReconciliationContextLoader;
import com.universal.reconciliation.service.matching.DynamicSourceDataset;
import com.universal.reconciliation.service.matching.MatchingResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DynamicMatchingEngineTest {

    @Mock
    private DynamicReconciliationContextLoader contextLoader;

    private DynamicMatchingEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        engine = new DynamicMatchingEngine(contextLoader);
    }

    @Test
    void execute_detectsMismatchesAndMissingRecordsAcrossSources() {
        ReconciliationDefinition definition = new ReconciliationDefinition();

        CanonicalField transactionId = canonicalField(
                definition, "transactionId", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        CanonicalField amount = canonicalField(
                definition, "amount", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.EXACT_MATCH, null);
        CanonicalField currency = canonicalField(
                definition, "currency", FieldRole.COMPARE, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        CanonicalField product = canonicalField(
                definition, "product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
        product.setClassifierTag("product");

        ReconciliationSource cashSource = new ReconciliationSource();
        cashSource.setCode("CASH");
        cashSource.setAnchor(true);

        ReconciliationSource glSource = new ReconciliationSource();
        glSource.setCode("GL");
        glSource.setAnchor(false);

        Map<String, Map<String, Object>> cashRecords = new LinkedHashMap<>();
        cashRecords.put("TXN-1", Map.of(
                "transactionId", "TXN-1",
                "amount", BigDecimal.valueOf(100),
                "currency", "USD",
                "product", "Payments"));
        cashRecords.put("TXN-2", Map.of(
                "transactionId", "TXN-2",
                "amount", BigDecimal.valueOf(200),
                "currency", "USD",
                "product", "Payments"));

        Map<String, Map<String, Object>> glRecords = new LinkedHashMap<>();
        glRecords.put("TXN-1", Map.of(
                "transactionId", "TXN-1",
                "amount", BigDecimal.valueOf(95),
                "currency", "USD",
                "product", "Payments"));
        glRecords.put("TXN-3", Map.of(
                "transactionId", "TXN-3",
                "amount", BigDecimal.valueOf(50),
                "currency", "USD",
                "product", "Payments"));

        DynamicReconciliationContext context = new DynamicReconciliationContext(
                definition,
                List.of(transactionId, amount, currency, product),
                List.of(transactionId),
                List.of(amount, currency),
                List.of(product),
                new DynamicSourceDataset(cashSource, null, cashRecords),
                List.of(new DynamicSourceDataset(glSource, null, glRecords)));

        when(contextLoader.load(definition)).thenReturn(context);

        MatchingResult result = engine.execute(definition);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.mismatchedCount()).isEqualTo(1);
        assertThat(result.missingCount()).isEqualTo(2);

        List<BreakCandidate> breaks = result.breaks();
        assertThat(breaks).hasSize(3);
        assertThat(breaks)
                .extracting(BreakCandidate::type)
                .containsExactlyInAnyOrder(BreakType.MISMATCH, BreakType.SOURCE_MISSING, BreakType.ANCHOR_MISSING);

        BreakCandidate mismatch = breaks.stream()
                .filter(candidate -> candidate.type() == BreakType.MISMATCH)
                .findFirst()
                .orElseThrow();
        assertThat(mismatch.sources()).containsKeys("CASH", "GL");

        BreakCandidate missing = breaks.stream()
                .filter(candidate -> candidate.type() == BreakType.SOURCE_MISSING)
                .findFirst()
                .orElseThrow();
        assertThat(missing.missingSources()).containsExactly("GL");
        assertThat(missing.classifications().get("product")).isEqualTo("Payments");

        BreakCandidate anchorMissing = breaks.stream()
                .filter(candidate -> candidate.type() == BreakType.ANCHOR_MISSING)
                .findFirst()
                .orElseThrow();
        assertThat(anchorMissing.missingSources()).containsExactly("CASH");
    }

    private CanonicalField canonicalField(
            ReconciliationDefinition definition,
            String name,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            BigDecimal threshold) {
        CanonicalField field = new CanonicalField();
        field.setDefinition(definition);
        field.setCanonicalName(name);
        field.setDisplayName(name);
        field.setRole(role);
        field.setDataType(dataType);
        field.setComparisonLogic(logic);
        field.setThresholdPercentage(threshold);
        field.setRequired(true);
        return field;
    }
}
