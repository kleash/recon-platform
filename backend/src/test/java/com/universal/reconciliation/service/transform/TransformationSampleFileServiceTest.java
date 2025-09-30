package com.universal.reconciliation.service.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.admin.TransformationFilePreviewUploadRequest;
import com.universal.reconciliation.domain.dto.admin.TransformationPreviewRequest;
import com.universal.reconciliation.domain.enums.TransformationSampleFileType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class TransformationSampleFileServiceTest {

    private final TransformationSampleFileService service = new TransformationSampleFileService(new ObjectMapper());

    @Test
    void parsesCsvWithHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "Amount,Fee\n100,10\n200,20".getBytes(StandardCharsets.UTF_8));
        TransformationFilePreviewUploadRequest request = new TransformationFilePreviewUploadRequest(
                TransformationSampleFileType.CSV,
                true,
                ",",
                null,
                null,
                "Amount",
                null,
                null,
                List.of(new TransformationPreviewRequest.PreviewTransformationDto(null, null, null, 1, true)));

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("Amount", "100");
    }

    @Test
    void assignsSyntheticHeadersWhenMissing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "100|OK\n200|FAIL".getBytes(StandardCharsets.UTF_8));
        TransformationFilePreviewUploadRequest request = new TransformationFilePreviewUploadRequest(
                TransformationSampleFileType.DELIMITED,
                false,
                "|",
                null,
                null,
                "COLUMN_1",
                null,
                null,
                List.of(new TransformationPreviewRequest.PreviewTransformationDto(null, null, null, 1, true)));

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows.get(0)).containsEntry("COLUMN_1", "100");
        assertThat(rows.get(0)).containsEntry("COLUMN_2", "OK");
    }

    @Test
    void parsesJsonUsingRecordPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.json",
                "application/json",
                "{\"data\":{\"items\":[{\"amount\":100},{\"amount\":200}]}}".getBytes(StandardCharsets.UTF_8));
        TransformationFilePreviewUploadRequest request = new TransformationFilePreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                "data.items",
                "amount",
                null,
                null,
                List.of(new TransformationPreviewRequest.PreviewTransformationDto(null, null, null, 1, true)));

        List<Map<String, Object>> rows = service.parseSamples(request, file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsEntry("amount", 200);
    }

    @Test
    void enforcesUploadSizeLimit() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(TransformationSampleFileService.MAX_UPLOAD_BYTES + 1);

        TransformationFilePreviewUploadRequest request = new TransformationFilePreviewUploadRequest(
                TransformationSampleFileType.JSON,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new TransformationPreviewRequest.PreviewTransformationDto(null, null, null, 1, true)));

        assertThatThrownBy(() -> service.parseSamples(request, file))
                .isInstanceOf(TransformationEvaluationException.class)
                .hasMessageContaining("upload limit");
    }
}
