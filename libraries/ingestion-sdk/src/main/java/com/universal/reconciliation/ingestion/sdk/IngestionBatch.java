package com.universal.reconciliation.ingestion.sdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single source batch submission destined for the reconciliation platform.
 */
public final class IngestionBatch {

    private final String sourceCode;
    private final String label;
    private final byte[] payload;
    private final Path payloadFile;
    private final boolean deleteFileAfterWrite;
    private final PayloadWriter payloadWriter;
    private final long contentLength;
    private final String mediaType;
    private final Map<String, Object> options;

    private IngestionBatch(Builder builder) {
        this.sourceCode = Objects.requireNonNull(builder.sourceCode, "sourceCode");
        this.label = Objects.requireNonNull(builder.label, "label");
        if (builder.payload == null && builder.payloadFile == null && builder.payloadWriter == null) {
            throw new IllegalArgumentException("payload, payloadFile, or payloadWriter must be provided");
        }
        this.payload = builder.payload;
        this.payloadFile = builder.payloadFile;
        this.deleteFileAfterWrite = builder.deleteFileAfterWrite;
        this.payloadWriter = builder.payloadWriter;
        this.mediaType = builder.mediaType != null ? builder.mediaType : "text/csv";
        this.options = builder.options != null ? Map.copyOf(builder.options) : Collections.emptyMap();
        this.contentLength = builder.contentLength;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getLabel() {
        return label;
    }

    public byte[] getPayload() {
        if (payload != null) {
            return payload;
        }
        if (payloadFile != null) {
            try {
                return Files.readAllBytes(payloadFile);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read payload file", e);
            }
        }
        if (payloadWriter != null) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                payloadWriter.write(buffer);
                return buffer.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to materialize payload", e);
            }
        }
        throw new IllegalStateException("No payload available");
    }

    public String getMediaType() {
        return mediaType;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void writePayload(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        if (payload != null) {
            outputStream.write(payload);
            return;
        }
        if (payloadFile != null) {
            try (InputStream in = Files.newInputStream(payloadFile)) {
                in.transferTo(outputStream);
            }
            return;
        }
        if (payloadWriter != null) {
            payloadWriter.write(outputStream);
            return;
        }
        throw new IllegalStateException("No payload available");
    }

    public Optional<Path> getPayloadFile() {
        return Optional.ofNullable(payloadFile);
    }

    public void discardPayload() {
        if (payloadFile != null && deleteFileAfterWrite) {
            try {
                Files.deleteIfExists(payloadFile);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete temporary payload file", e);
            }
        }
    }

    public static Builder builder(String sourceCode) {
        return new Builder().sourceCode(sourceCode);
    }

    public static Builder builder(String sourceCode, String label) {
        return new Builder().sourceCode(sourceCode).label(label);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sourceCode;
        private String label;
        private byte[] payload;
        private Path payloadFile;
        private boolean deleteFileAfterWrite;
        private PayloadWriter payloadWriter;
        private String mediaType;
        private Map<String, Object> options;
        private long contentLength = -1L;

        private Builder() {
        }

        public Builder sourceCode(String sourceCode) {
            this.sourceCode = sourceCode;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload;
            this.payloadFile = null;
            this.deleteFileAfterWrite = false;
            this.payloadWriter = null;
            this.contentLength = payload != null ? payload.length : -1L;
            return this;
        }

        public Builder payloadFromString(String text) {
            this.payload = text == null ? null : text.getBytes(StandardCharsets.UTF_8);
            this.payloadFile = null;
            this.deleteFileAfterWrite = false;
            this.payloadWriter = null;
            this.contentLength = payload != null ? payload.length : -1L;
            return this;
        }

        public Builder payloadFile(Path payloadFile) {
            return payloadFile(payloadFile, false);
        }

        public Builder payloadFile(Path payloadFile, boolean deleteAfterWrite) {
            this.payload = null;
            this.payloadFile = payloadFile;
            this.deleteFileAfterWrite = deleteAfterWrite;
            this.payloadWriter = null;
            if (payloadFile != null) {
                try {
                    this.contentLength = Files.size(payloadFile);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to determine payload file size", e);
                }
            } else {
                this.contentLength = -1L;
            }
            return this;
        }

        public Builder payloadWriter(PayloadWriter payloadWriter) {
            this.payload = null;
            this.payloadFile = null;
            this.deleteFileAfterWrite = false;
            this.payloadWriter = payloadWriter;
            this.contentLength = -1L;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }

        public IngestionBatch build() {
            return new IngestionBatch(this);
        }
    }

    @FunctionalInterface
    public interface PayloadWriter {
        void write(OutputStream outputStream) throws IOException;
    }
}
