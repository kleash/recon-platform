package com.universal.reconciliation.domain.enums;

/**
 * Enumerates the supported transformation rule families that can be chained
 * together ahead of canonical value normalisation.
 */
public enum TransformationType {
    GROOVY_SCRIPT,
    EXCEL_FORMULA,
    FUNCTION_PIPELINE,
    LLM_PROMPT
}

