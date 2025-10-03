package com.universal.reconciliation.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceRequest;
import com.universal.reconciliation.domain.dto.admin.SourceSchemaInferenceResponse;
import com.universal.reconciliation.domain.enums.FieldDataType;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import com.universal.reconciliation.service.transform.TransformationSampleFileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

class AdminSourceSchemaServiceTest {

    @Mock
    private TransformationSampleFileService sampleFileService;

    @Mock
    private MultipartFile sample;

    private AdminSourceSchemaService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AdminSourceSchemaService(sampleFileService);
    }

    @Test
    void inferSchema_derivesColumnsAndTypes() {
        when(sampleFileService.parseSamples(any(), eq(sample))).thenReturn(List.of(
                Map.of("trade_id", "12345", "amount", "10.50", "tradeDate", "2024-05-01"),
                Map.of("trade_id", "54321", "amount", "11.00", "tradeDate", "2024-05-02")));

        SourceSchemaInferenceRequest request = new SourceSchemaInferenceRequest(
                TransformationSampleFileType.CSV,
                true,
                ",",
                null,
                List.of(),
                false,
                null,
                null,
                10,
                0);

        SourceSchemaInferenceResponse response = service.inferSchema(request, sample);

        assertThat(response.fields()).hasSize(3);
        assertThat(response.fields())
                .anySatisfy(field -> {
                    if ("trade_id".equals(field.name())) {
                        assertThat(field.dataType()).isEqualTo(FieldDataType.INTEGER);
                        assertThat(field.required()).isTrue();
                    }
                })
                .anySatisfy(field -> {
                    if ("amount".equals(field.name())) {
                        assertThat(field.dataType()).isEqualTo(FieldDataType.DECIMAL);
                    }
                })
                .anySatisfy(field -> {
                    if ("tradeDate".equals(field.name())) {
                        assertThat(field.dataType()).isEqualTo(FieldDataType.DATE);
                    }
                });
        assertThat(response.sampleRows()).hasSize(2);
    }
}
