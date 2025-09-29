package com.universal.reconciliation.ingestion.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single source batch submission destined for the reconciliation platform.
 */
public final class IngestionBatch {

    private final String sourceCode;
    private final String label;
    private final byte[] payload;
    private final String mediaType;
    private final Map<String, Object> options;

    private IngestionBatch(Builder builder) {
        this.sourceCode = Objects.requireNonNull(builder.sourceCode, "sourceCode");
        this.label = Objects.requireNonNull(builder.label, "label");
        this.payload = Objects.requireNonNull(builder.payload, "payload");
        this.mediaType = builder.mediaType != null ? builder.mediaType : "text/csv";
        this.options = builder.options != null ? Map.copyOf(builder.options) : Collections.emptyMap();
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getLabel() {
        return label;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Map<String, Object> getOptions() {
        return options;
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
        private String mediaType;
        private Map<String, Object> options;

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
            return this;
        }

        public Builder payloadFromString(String text) {
            this.payload = text == null ? null : text.getBytes(StandardCharsets.UTF_8);
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
}
