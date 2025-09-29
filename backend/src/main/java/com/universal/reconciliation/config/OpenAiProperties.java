package com.universal.reconciliation.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties controlling the OpenAI integration. Defaults are
 * intentionally conservative so administrators can enable the integration per
 * environment without code changes.
 */
@Component
@ConfigurationProperties(prefix = "app.integrations.openai")
@Validated
public class OpenAiProperties {

    /** API key used when calling the OpenAI REST API. */
    private String apiKey;

    /** Base URL for the OpenAI API. Defaults to the public SaaS endpoint. */
    private String baseUrl = "https://api.openai.com/v1";

    /** Model used when none is supplied by the caller. */
    private String defaultModel = "gpt-4o-mini";

    /** Default temperature applied to completions unless overridden. */
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "2.0", inclusive = true)
    private double defaultTemperature = 0.0d;

    /** Default maximum output tokens. */
    @Min(1)
    private int defaultMaxOutputTokens = 800;

    /** Maximum number of characters forwarded from a document into prompts. */
    @Min(1)
    private int documentCharacterLimit = 15000;

    /** Number of characters preserved in the metadata preview snippet. */
    @Min(1)
    private int metadataPreviewCharacters = 1200;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl;
        }
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        if (defaultModel != null && !defaultModel.isBlank()) {
            this.defaultModel = defaultModel;
        }
    }

    public double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(double defaultTemperature) {
        if (defaultTemperature < 0.0d || defaultTemperature > 2.0d) {
            throw new IllegalArgumentException("defaultTemperature must be between 0.0 and 2.0 inclusive");
        }
        this.defaultTemperature = defaultTemperature;
    }

    public int getDefaultMaxOutputTokens() {
        return defaultMaxOutputTokens;
    }

    public void setDefaultMaxOutputTokens(int defaultMaxOutputTokens) {
        if (defaultMaxOutputTokens <= 0) {
            throw new IllegalArgumentException("defaultMaxOutputTokens must be greater than zero");
        }
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
    }

    public int getDocumentCharacterLimit() {
        return documentCharacterLimit;
    }

    public void setDocumentCharacterLimit(int documentCharacterLimit) {
        if (documentCharacterLimit <= 0) {
            throw new IllegalArgumentException("documentCharacterLimit must be greater than zero");
        }
        this.documentCharacterLimit = documentCharacterLimit;
    }

    public int getMetadataPreviewCharacters() {
        return metadataPreviewCharacters;
    }

    public void setMetadataPreviewCharacters(int metadataPreviewCharacters) {
        if (metadataPreviewCharacters <= 0) {
            throw new IllegalArgumentException("metadataPreviewCharacters must be greater than zero");
        }
        this.metadataPreviewCharacters = metadataPreviewCharacters;
    }
}

