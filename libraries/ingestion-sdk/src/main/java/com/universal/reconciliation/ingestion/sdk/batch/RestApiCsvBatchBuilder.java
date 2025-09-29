package com.universal.reconciliation.ingestion.sdk.batch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.Function;
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
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
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
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
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
            Function<JsonNode, Iterable<JsonNode>> recordExtractor)
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
            Function<JsonNode, Iterable<JsonNode>> recordExtractor,
            ClientHttpResponse response)
            throws IOException {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("API call failed with status " + response.getStatusCode());
        }
        Path tempFile = Files.createTempFile("ingestion-rest-", ".csv");
        tempFile.toFile().deleteOnExit();
        try (InputStream bodyStream = response.getBody();
                BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            if (recordExtractor != null || (recordPointer != null && !recordPointer.isBlank())) {
                JsonNode root = objectMapper.readTree(bodyStream);
                Iterable<JsonNode> records = selectRecords(root, recordPointer, recordExtractor);
                streamJsonNodes(records, columns == null ? null : new ArrayList<>(columns), writer);
            } else {
                streamJsonArray(bodyStream, columns == null ? null : new ArrayList<>(columns), writer);
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

    private void streamJsonArray(InputStream bodyStream, List<String> columns, Writer writer) throws IOException {
        JsonParser parser = objectMapper.getFactory().createParser(bodyStream);
        JsonToken firstToken = parser.nextToken();
        if (firstToken == null) {
            CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
            printer.flush();
            return;
        }
        if (firstToken != JsonToken.START_ARRAY) {
            throw new IOException("Expected JSON array response but received: " + firstToken);
        }
        JsonToken nextToken = parser.nextToken();
        if (nextToken == JsonToken.END_ARRAY) {
            CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
            printer.flush();
            return;
        }
        ObjectReader reader = objectMapper.readerFor(Map.class);
        try (MappingIterator<Map<String, Object>> iterator = reader.readValues(parser)) {
            streamMaps(iterator, columns, writer);
        }
    }

    private void streamJsonNodes(Iterable<JsonNode> nodes, List<String> columns, Writer writer)
            throws IOException {
        Iterator<JsonNode> iterator = nodes.iterator();
        if (iterator == null) {
            CSVPrinter printer = CsvRenderer.createPrinter(writer, columns == null ? List.of() : columns);
            printer.flush();
            return;
        }
        streamMaps(new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                JsonNode node = iterator.next();
                return objectMapper.convertValue(node, Map.class);
            }
        }, columns, writer);
    }

    private void streamMaps(Iterator<Map<String, Object>> iterator, List<String> columns, Writer writer)
            throws IOException {
        List<String> headers = columns != null && !columns.isEmpty() ? new ArrayList<>(columns) : null;
        CSVPrinter printer;
        if (iterator.hasNext()) {
            Map<String, Object> first = CsvRenderer.normalizeKeys(iterator.next());
            if (headers == null) {
                headers = CsvRenderer.determineHeadersFromRow(first, null);
            }
            printer = CsvRenderer.createPrinter(writer, headers);
            CsvRenderer.printRow(printer, first, headers);
        } else {
            headers = headers != null ? headers : List.of();
            printer = CsvRenderer.createPrinter(writer, headers);
        }
        while (iterator.hasNext()) {
            Map<String, Object> row = CsvRenderer.normalizeKeys(iterator.next());
            CsvRenderer.printRow(printer, row, headers);
        }
        printer.flush();
    }

    private Iterable<JsonNode> selectRecords(
            JsonNode root, String recordPointer, Function<JsonNode, Iterable<JsonNode>> recordExtractor) throws IOException {
        if (recordExtractor != null) {
            Iterable<JsonNode> extracted = recordExtractor.apply(root);
            if (extracted == null) {
                throw new IOException("Record extractor returned null for response body");
            }
            return extracted;
        }

        if (recordPointer != null && !recordPointer.isBlank()) {
            JsonNode pointerNode = root.at(normalizePointer(recordPointer));
            if (pointerNode.isMissingNode()) {
                throw new IOException("JSON pointer '" + recordPointer + "' did not resolve to any node");
            }
            if (!pointerNode.isArray()) {
                throw new IOException(
                        "JSON pointer '" + recordPointer + "' resolved to " + pointerNode.getNodeType() + " instead of array");
            }
            return pointerNode;
        }

        if (!root.isArray()) {
            throw new IOException("Expected JSON array response but received: " + root.getNodeType());
        }
        return root;
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
}
