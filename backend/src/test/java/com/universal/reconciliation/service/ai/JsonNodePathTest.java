package com.universal.reconciliation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonNodePathTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void navigate_returnsRootWhenPathIsBlank() throws Exception {
        JsonNode root = MAPPER.readTree("{\"value\":1}");

        JsonNode result = JsonNodePath.navigate(root, " ");

        assertThat(result).isSameAs(root);
    }

    @Test
    void navigate_handlesSimpleDotSeparatedPath() throws Exception {
        JsonNode root = MAPPER.readTree("{\"details\":{\"amount\":{\"value\":42}}}");

        JsonNode result = JsonNodePath.navigate(root, "details.amount.value");

        assertThat(result).isNotNull();
        assertThat(result.asInt()).isEqualTo(42);
    }

    @Test
    void navigate_supportsEscapedDotsInSegments() throws Exception {
        JsonNode root = MAPPER.readTree("{\"details.total\":{\"value\":5}}");

        JsonNode result = JsonNodePath.navigate(root, "details\\.total.value");

        assertThat(result).isNotNull();
        assertThat(result.asInt()).isEqualTo(5);
    }

    @Test
    void navigate_returnsNullWhenSegmentMissing() throws Exception {
        JsonNode root = MAPPER.readTree("{\"details\":{\"amount\":{\"value\":42}}}");

        JsonNode result = JsonNodePath.navigate(root, "details.amount.currency");

        assertThat(result).isNull();
    }

    @Test
    void navigate_returnsNullWhenRootIsNull() {
        JsonNode result = JsonNodePath.navigate(null, "any.path");

        assertThat(result).isNull();
    }

    @Test
    void navigate_supportsSegmentsEndingWithBackslash() throws Exception {
        JsonNode root = MAPPER.readTree("{\"value\\\\\":5}");

        JsonNode result = JsonNodePath.navigate(root, "value\\");

        assertThat(result).isNotNull();
        assertThat(result.asInt()).isEqualTo(5);
    }
}
