package com.universal.reconciliation.ingestion.sdk.batch;

import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Utility that loads CSV payloads from the application classpath.
 */
public final class ClasspathCsvBatchLoader {

    private ClasspathCsvBatchLoader() {
    }

    public static IngestionBatch load(String sourceCode, String label, String resourcePath, Map<String, Object> options)
            throws IOException {
        return load(sourceCode, label, resourcePath, options, null);
    }

    public static IngestionBatch load(
            String sourceCode, String label, String resourcePath, Map<String, Object> options, String mediaType)
            throws IOException {
        Resource resource = new ClassPathResource(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        if (!resource.exists()) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        try (InputStream stream = resource.getInputStream()) {
            byte[] payload = stream.readAllBytes();
            String resolvedMediaType = mediaType != null ? mediaType : inferMediaType(resourcePath);
            return IngestionBatch.builder(sourceCode, label)
                    .mediaType(resolvedMediaType)
                    .payload(payload)
                    .options(options == null ? Map.of() : options)
                    .build();
        }
    }

    private static String inferMediaType(String resourcePath) {
        String lowerCase = resourcePath.toLowerCase();
        if (lowerCase.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerCase.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lowerCase.endsWith(".txt")) {
            return "text/plain";
        }
        return "text/csv";
    }
}
