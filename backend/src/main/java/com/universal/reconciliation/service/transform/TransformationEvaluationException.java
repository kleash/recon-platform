package com.universal.reconciliation.service.transform;

/**
 * Signals that a transformation rule failed during compilation or evaluation.
 */
public class TransformationEvaluationException extends RuntimeException {

    public TransformationEvaluationException(String message) {
        super(message);
    }

    public TransformationEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

