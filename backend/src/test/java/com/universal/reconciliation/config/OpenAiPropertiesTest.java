package com.universal.reconciliation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiPropertiesTest {

    @Test
    void settersApplyDefaultsWhenBlankValuesProvided() {
        OpenAiProperties properties = new OpenAiProperties();

        properties.setBaseUrl("  ");
        properties.setDefaultModel(null);

        assertThat(properties.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-4o-mini");

        properties.setBaseUrl("http://example");
        properties.setDefaultModel("gpt-test");

        assertThat(properties.getBaseUrl()).isEqualTo("http://example");
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-test");
    }

    @Test
    void settersValidateNumericRanges() {
        OpenAiProperties properties = new OpenAiProperties();

        properties.setDefaultTemperature(1.5d);
        properties.setDefaultMaxOutputTokens(123);
        properties.setDocumentCharacterLimit(42);
        properties.setMetadataPreviewCharacters(21);

        assertThat(properties.getDefaultTemperature()).isEqualTo(1.5d);
        assertThat(properties.getDefaultMaxOutputTokens()).isEqualTo(123);
        assertThat(properties.getDocumentCharacterLimit()).isEqualTo(42);
        assertThat(properties.getMetadataPreviewCharacters()).isEqualTo(21);

        assertThatThrownBy(() -> properties.setDefaultTemperature(2.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setDefaultTemperature(-0.1d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setDefaultMaxOutputTokens(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setDocumentCharacterLimit(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMetadataPreviewCharacters(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

