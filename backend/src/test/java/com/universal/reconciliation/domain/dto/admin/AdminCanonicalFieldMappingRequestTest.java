package com.universal.reconciliation.domain.dto.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AdminCanonicalFieldMappingRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializationFailsWhenUnknownPropertyProvided() {
        String payload = "{" +
                "\"id\":null," +
                "\"sourceCode\":\"CUSTODY\"," +
                "\"sourceColumn\":\"trade_id\"," +
                "\"transformationExpression\":\"TRIM(trade_id)\"," +
                "\"defaultValue\":null," +
                "\"ordinalPosition\":0," +
                "\"required\":true," +
                "\"transformations\":[]" +
                "}";

        assertThatThrownBy(() -> read(payload))
                .isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("transformationExpression");
    }

    @Test
    void deserializationSucceedsWithoutUnknownProperties() throws IOException {
        String payload = "{" +
                "\"id\":1," +
                "\"sourceCode\":\"CUSTODY\"," +
                "\"sourceColumn\":\"trade_id\"," +
                "\"defaultValue\":\"UNKNOWN\"," +
                "\"ordinalPosition\":5," +
                "\"required\":false," +
                "\"transformations\":[]" +
                "}";

        AdminCanonicalFieldMappingRequest request = read(payload);

        assertThat(request.sourceCode()).isEqualTo("CUSTODY");
        assertThat(request.sourceColumn()).isEqualTo("trade_id");
        assertThat(request.defaultValue()).isEqualTo("UNKNOWN");
        assertThat(request.ordinalPosition()).isEqualTo(5);
        assertThat(request.required()).isFalse();
        assertThat(request.transformations()).isEmpty();
    }

    private AdminCanonicalFieldMappingRequest read(String payload) throws IOException {
        return objectMapper.readValue(payload, AdminCanonicalFieldMappingRequest.class);
    }
}
