package com.universal.reconciliation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {

    @Test
    void render_returnsEmptyStringForNullTemplate() {
        String result = PromptTemplateRenderer.render(null, Map.of("value", "ignored"));

        assertThat(result).isEmpty();
    }

    @Test
    void render_substitutesAllTokens() {
        String template = "Hello {{ name }}! You have {{count}} tasks.";

        String result = PromptTemplateRenderer.render(template, Map.of(
                "name", "Alex",
                "count", "3"));

        assertThat(result).isEqualTo("Hello Alex! You have 3 tasks.");
    }

    @Test
    void render_ignoresMissingKeysAndLeavesNonTokens() {
        String template = "{{known}} {{unknown}} static";

        String result = PromptTemplateRenderer.render(template, Map.of("known", "value"));

        assertThat(result).isEqualTo("value  static");
    }

    @Test
    void render_preservesTextOutsideTokens() {
        String template = "prefix {{token}} suffix";

        String result = PromptTemplateRenderer.render(template, Map.of("token", "middle"));

        assertThat(result).isEqualTo("prefix middle suffix");
    }
}
