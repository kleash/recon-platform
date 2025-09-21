package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import com.universal.reconciliation.service.matching.ExactMatchingEngine;
import com.universal.reconciliation.service.matching.MatchingResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verifies the exact matching logic for the MVP matching engine.
 */
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
    void execute_identifiesMismatchesAndMissingRecords() {
        when(sourceRecordARepository.findAll()).thenReturn(List.of(recordA("TXN-1", 100), recordA("TXN-2", 200)));
        when(sourceRecordBRepository.findAll()).thenReturn(List.of(recordB("TXN-1", 100), recordB("TXN-3", 300)));

        MatchingResult result = matchingEngine.execute(new ReconciliationDefinition());

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.mismatchedCount()).isEqualTo(0);
        assertThat(result.missingCount()).isEqualTo(2);
        assertThat(result.breaks())
                .hasSize(2)
                .extracting("type")
                .containsExactlyInAnyOrder(BreakType.MISSING_IN_SOURCE_B, BreakType.MISSING_IN_SOURCE_A);
    }

    private SourceRecordA recordA(String id, int amount) {
        SourceRecordA record = new SourceRecordA();
        record.setTransactionId(id);
        record.setAmount(BigDecimal.valueOf(amount));
        record.setCurrency("USD");
        record.setTradeDate(LocalDate.of(2024, 1, 1));
        return record;
    }

    private SourceRecordB recordB(String id, int amount) {
        SourceRecordB record = new SourceRecordB();
        record.setTransactionId(id);
        record.setAmount(BigDecimal.valueOf(amount));
        record.setCurrency("USD");
        record.setTradeDate(LocalDate.of(2024, 1, 1));
        return record;
    }
}
