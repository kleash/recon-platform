package com.universal.reconciliation.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating or updating an analyst saved view.
 */
public record SavedViewRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 512) String description,
        @NotBlank String settingsJson,
        boolean shared,
        boolean defaultView) {}

