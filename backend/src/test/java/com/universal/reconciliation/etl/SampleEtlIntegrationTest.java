package com.universal.reconciliation.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class SampleEtlIntegrationTest {

    @Autowired
    private ReconciliationDefinitionRepository definitionRepository;

    @Autowired
    private SourceRecordARepository sourceRecordARepository;

    @Autowired
    private SourceRecordBRepository sourceRecordBRepository;

    @Test
    @Transactional
    void pipelinesSeedSampleDefinitionsAndData() {
        ReconciliationDefinition simple = definitionRepository
                .findByCode("CASH_VS_GL_SIMPLE")
                .orElseThrow();
        ReconciliationDefinition complex = definitionRepository
                .findByCode("SEC_POSITION_COMPLEX")
                .orElseThrow();

        assertThat(simple.getFields()).isNotEmpty();
        assertThat(complex.getFields())
                .anyMatch(field -> "quantity".equals(field.getSourceField()));

        assertThat(sourceRecordARepository.findByDefinitionAndTransactionId(simple, "CASH-1001"))
                .isPresent();
        assertThat(sourceRecordBRepository.findByDefinitionAndTransactionId(simple, "CASH-1004"))
                .isPresent();
        assertThat(sourceRecordARepository.findByDefinitionAndTransactionId(complex, "COMPLEX-003"))
                .isPresent();
        assertThat(sourceRecordBRepository.findByDefinitionAndTransactionId(complex, "COMPLEX-004"))
                .isPresent();
    }
}
