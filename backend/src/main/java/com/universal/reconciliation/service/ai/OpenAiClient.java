package com.universal.reconciliation.service.ai;

/**
 * Contract used by ingestion adapters and transformation evaluators when
 * delegating structured extraction to OpenAI.
 */
public interface OpenAiClient {

    /**
     * Executes the supplied prompt and returns the aggregated assistant
     * response. The response is expected to be JSON when a schema or
     * {@code json_object} format is requested.
     */
    String completeJson(OpenAiPromptRequest request);
}

