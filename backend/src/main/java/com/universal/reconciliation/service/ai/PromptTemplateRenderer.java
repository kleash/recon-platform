package com.universal.reconciliation.service.ai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for rendering simple double-brace template tokens (e.g. {{value}}) used in
 * LLM prompt construction. Tokens may contain alphanumeric characters, underscores, and dots.
 */
public final class PromptTemplateRenderer {

    private static final Pattern TEMPLATE_TOKEN =
            Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}}", Pattern.MULTILINE);

    private PromptTemplateRenderer() {}

    public static String render(String template, Map<String, String> substitutions) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuilder builder = new StringBuilder();
        int lastIndex = 0;
        while (matcher.find()) {
            builder.append(template, lastIndex, matcher.start());
            String key = matcher.group(1);
            String replacement = substitutions.getOrDefault(key, "");
            builder.append(replacement);
            lastIndex = matcher.end();
        }
        builder.append(template.substring(lastIndex));
        return builder.toString();
    }
}
