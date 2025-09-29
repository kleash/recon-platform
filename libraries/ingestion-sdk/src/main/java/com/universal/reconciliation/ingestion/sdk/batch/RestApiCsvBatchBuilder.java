package com.universal.reconciliation.ingestion.sdk.batch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.universal.reconciliation.ingestion.sdk.IngestionBatch;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Calls an HTTP API that returns a JSON payload and converts the record array into a CSV-backed batch.
 */
public final class RestApiCsvBatchBuilder {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RestApiCsvBatchBuilder(RestTemplate restTemplate) {
        this(restTemplate, new ObjectMapper());
    }

    public RestApiCsvBatchBuilder(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public IngestionBatch get(String sourceCode, String label, URI uri, List<String> columns, Map<String, Object> options)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options);
    }

    public IngestionBatch get(
            String sourceCode,
            String label,
            URI uri,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options, recordPointer);
    }

    public IngestionBatch get(
            String sourceCode,
            String label,
            URI uri,
            List<String> columns,
            Map<String, Object> options,
            RecordExtractor recordExtractor)
            throws IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        return exchange(sourceCode, label, request, columns, options, recordExtractor);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, (String) null);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, recordPointer, null);
    }

    public IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            RecordExtractor recordExtractor)
            throws IOException {
        return exchange(sourceCode, label, request, columns, options, null, recordExtractor);
    }

    private IngestionBatch exchange(
            String sourceCode,
            String label,
            RequestEntity<?> request,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer,
            RecordExtractor recordExtractor)
            throws IOException {
        try {
            return restTemplate.execute(
                    request.getUrl(),
                    request.getMethod(),
                    clientRequest -> prepareRequest(clientRequest, request),
                    clientResponse -> handleResponse(
                            sourceCode,
                            label,
                            columns,
                            options,
                            recordPointer,
                            recordExtractor,
                            clientResponse));
        } catch (RestClientException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private void prepareRequest(ClientHttpRequest clientRequest, RequestEntity<?> request) throws IOException {
        clientRequest.getHeaders().putAll(request.getHeaders());
        if (request.hasBody()) {
            writeRequestBody(clientRequest, request);
        }
    }

    private void writeRequestBody(ClientHttpRequest clientRequest, RequestEntity<?> request) throws IOException {
        Object body = request.getBody();
        if (body == null) {
            return;
        }
        for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
            if (converter.canWrite(body.getClass(), request.getHeaders().getContentType())) {
                @SuppressWarnings("unchecked")
                HttpMessageConverter<Object> writer = (HttpMessageConverter<Object>) converter;
                writer.write(body, request.getHeaders().getContentType(), clientRequest);
                return;
            }
        }
        throw new IOException("No HttpMessageConverter for request body type " + body.getClass());
    }

    private IngestionBatch handleResponse(
            String sourceCode,
            String label,
            List<String> columns,
            Map<String, Object> options,
            String recordPointer,
            RecordExtractor recordExtractor,
            ClientHttpResponse response)
            throws IOException {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("API call failed with status " + response.getStatusCode());
        }
        Path tempFile = Files.createTempFile("ingestion-rest-", ".csv");
        tempFile.toFile().deleteOnExit();
        try (InputStream bodyStream = response.getBody();
                JsonParser parser = objectMapper.getFactory().createParser(bodyStream);
                BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            List<String> headerColumns = columns == null ? null : new ArrayList<>(columns);
            if (recordExtractor != null) {
                Iterator<Map<String, Object>> iterator = recordExtractor.extract(parser, objectMapper);
                CsvRenderer.streamIterator(iterator, headerColumns, writer);
            } else if (recordPointer != null && !recordPointer.isBlank()) {
                streamJsonPointer(parser, recordPointer, headerColumns, writer);
            } else {
                streamJsonArray(parser, headerColumns, writer);
            }
            writer.flush();
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        return IngestionBatch.builder(sourceCode, label)
                .mediaType("text/csv")
                .payloadFile(tempFile, true)
                .options(options == null ? Map.of() : options)
                .build();
    }

    private void streamJsonArray(JsonParser parser, List<String> columns, Writer writer) throws IOException {
        JsonToken firstToken = parser.nextToken();
        if (firstToken == null) {
            CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
            printer.flush();
            return;
        }
        if (firstToken != JsonToken.START_ARRAY) {
            throw new IOException("Expected JSON array response but received: " + firstToken);
        }
        JsonToken elementToken = parser.nextToken();
        if (elementToken == JsonToken.END_ARRAY) {
            CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
            printer.flush();
            return;
        }
        ObjectReader reader = objectMapper.readerFor(Map.class);
        try (MappingIterator<Map<String, Object>> iterator = reader.readValues(parser)) {
            CsvRenderer.streamIterator(iterator, columns, writer);
        }
    }

    private void streamJsonPointer(JsonParser parser, String pointer, List<String> columns, Writer writer)
            throws IOException {
        String normalized = normalizePointer(pointer);
        if ("/".equals(normalized)) {
            streamJsonArray(parser, columns, writer);
            return;
        }

        JsonPointer jsonPointer = JsonPointer.compile(normalized);
        ObjectReader reader = objectMapper.readerFor(Map.class);
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new IOException("JSON pointer '" + pointer + "' did not resolve to any node");
            }
            if (token == JsonToken.FIELD_NAME) {
                continue;
            }
            JsonPointer contextPointer = parser.getParsingContext().pathAsPointer();
            if (jsonPointer.equals(contextPointer)) {
                if (token != JsonToken.START_ARRAY) {
                    throw new IOException(
                            "JSON pointer '" + pointer + "' resolved to " + token + " instead of array");
                }
                JsonToken elementToken = parser.nextToken();
                if (elementToken == JsonToken.END_ARRAY) {
                    CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
                    printer.flush();
                    return;
                }
                try (MappingIterator<Map<String, Object>> iterator = reader.readValues(parser)) {
                    CsvRenderer.streamIterator(iterator, columns, writer);
                }
                return;
            }
            if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                continue;
            }
        }
    }

    /**
     * Converts the SDK's simplified dot-notation (e.g. {@code payload.entries}) into a JSON Pointer.
     * This helper only supports basic property names and escapes {@code /} and {@code ~} characters to
     * maintain compatibility with RFC 6901 but does not implement the full pointer specification.
     */
    private static String normalizePointer(String pointer) {
        String trimmed = pointer.trim();
        if (trimmed.isEmpty()) {
            return "/";
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append('/')
                    .append(part.replace("~", "~0").replace("/", "~1"));
        }
        return builder.length() > 0 ? builder.toString() : "/";
    }

    /**
     * Supplies an iterator of CSV-ready rows from the API response. Implementations can use the provided
     * {@link JsonParser} to stream over large payloads without materializing the entire JSON document.
     */
    @FunctionalInterface
    public interface RecordExtractor {
        Iterator<Map<String, Object>> extract(JsonParser parser, ObjectMapper objectMapper) throws IOException;
    }
}
