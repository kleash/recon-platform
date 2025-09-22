package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.ReconciliationField;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.domain.enums.ComparisonLogic;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.FieldRole;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import com.universal.reconciliation.service.matching.BreakCandidate;
import com.universal.reconciliation.service.matching.ExactMatchingEngine;
import com.universal.reconciliation.service.matching.MatchingResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExactMatchingEngineTest {

    private SourceRecordARepository sourceRecordARepository;
    private SourceRecordBRepository sourceRecordBRepository;
    private ExactMatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        sourceRecordARepository = Mockito.mock(SourceRecordARepository.class);
        sourceRecordBRepository = Mockito.mock(SourceRecordBRepository.class);
        matchingEngine = new ExactMatchingEngine(sourceRecordARepository, sourceRecordBRepository);
    }

    @Test
    void execute_appliesConfiguredComparisonLogicAndMetadata() {
        ReconciliationDefinition definition = definition();
        when(sourceRecordARepository.streamByDefinition(definition))
                .thenReturn(Stream.of(recordA(definition, "TXN-1", 100, "Payments", "Wire", "US"),
                        recordA(definition, "TXN-2", 200, "Payments", "Wire", "US")));
        when(sourceRecordBRepository.streamByDefinition(definition))
                .thenReturn(Stream.of(recordB(definition, "TXN-1", 104, "payments", "wire", "US"),
                        recordB(definition, "TXN-3", 50, "Payments", "Wire", "US")));

        MatchingResult result = matchingEngine.execute(definition);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.mismatchedCount()).isZero();
        assertThat(result.missingCount()).isEqualTo(2);

        List<BreakCandidate> candidates = result.breaks();
        assertThat(candidates)
                .hasSize(2)
                .extracting(BreakCandidate::type)
                .containsExactlyInAnyOrder(BreakType.MISSING_IN_SOURCE_B, BreakType.MISSING_IN_SOURCE_A);

        BreakCandidate missingInB = candidates.stream()
                .filter(candidate -> candidate.type() == BreakType.MISSING_IN_SOURCE_B)
                .findFirst()
                .orElseThrow();
        assertThat(missingInB.product()).isEqualTo("Payments");
        assertThat(missingInB.subProduct()).isEqualTo("Wire");
        assertThat(missingInB.entity()).isEqualTo("US");
    }

    private ReconciliationDefinition definition() {
        ReconciliationDefinition definition = new ReconciliationDefinition();
        definition.setFields(Set.of(
                field(definition, "transactionId", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null),
                field(definition, "amount", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, BigDecimal.valueOf(5)),
                field(definition, "currency", FieldRole.COMPARE, FieldDataType.STRING, ComparisonLogic.CASE_INSENSITIVE, null),
                field(definition, "tradeDate", FieldRole.COMPARE, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null),
                field(definition, "product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null),
                field(definition, "subProduct", FieldRole.SUB_PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null),
                field(definition, "entityName", FieldRole.ENTITY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null)));
        return definition;
    }

    private ReconciliationField field(
            ReconciliationDefinition definition,
            String sourceField,
            FieldRole role,
            FieldDataType dataType,
            ComparisonLogic logic,
            BigDecimal threshold) {
        ReconciliationField field = new ReconciliationField();
        field.setDefinition(definition);
        field.setSourceField(sourceField);
        field.setDisplayName(sourceField);
        field.setRole(role);
        field.setDataType(dataType);
        field.setComparisonLogic(logic);
        field.setThresholdPercentage(threshold);
        return field;
    }

    private SourceRecordA recordA(
            ReconciliationDefinition definition,
            String id,
            int amount,
            String product,
            String subProduct,
            String entity) {
        SourceRecordA record = new SourceRecordA();
        record.setDefinition(definition);
        record.setTransactionId(id);
        record.setAmount(BigDecimal.valueOf(amount));
        record.setCurrency("USD");
        record.setTradeDate(LocalDate.of(2024, 1, 1));
        record.setProduct(product);
        record.setSubProduct(subProduct);
        record.setEntityName(entity);
        return record;
    }

    private SourceRecordB recordB(
            ReconciliationDefinition definition,
            String id,
            int amount,
            String product,
            String subProduct,
            String entity) {
        SourceRecordB record = new SourceRecordB();
        record.setDefinition(definition);
        record.setTransactionId(id);
        record.setAmount(BigDecimal.valueOf(amount));
        record.setCurrency("USD");
        record.setTradeDate(LocalDate.of(2024, 1, 1));
        record.setProduct(product);
        record.setSubProduct(subProduct);
        record.setEntityName(entity);
        return record;
    }
}

