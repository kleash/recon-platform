package com.universal.reconciliation.service.ai;

/**
 * Signals that a call to the OpenAI API failed or returned an unexpected
 * payload.
 */
public class OpenAiClientException extends RuntimeException {

    public OpenAiClientException(String message) {
        super(message);
    }

    public OpenAiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

