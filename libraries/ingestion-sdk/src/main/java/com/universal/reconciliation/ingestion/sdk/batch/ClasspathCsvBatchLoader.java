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

    public static IngestionBatch load(String sourceCode, String label, String resourcePath, Map<String, Object> options) throws IOException {
        Resource resource = new ClassPathResource(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        if (!resource.exists()) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        try (InputStream stream = resource.getInputStream()) {
            byte[] payload = stream.readAllBytes();
            return IngestionBatch.builder(sourceCode, label)
                    .mediaType("text/csv")
                    .payload(payload)
                    .options(options == null ? Map.of() : options)
                    .build();
        }
    }
}
