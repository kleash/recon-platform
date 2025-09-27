package com.universal.reconciliation.domain.dto;

import java.time.Instant;

/**
 * Exposes saved view metadata to the frontend.
 */
public record SavedViewDto(
        Long id,
        String name,
        String description,
        boolean shared,
        boolean defaultView,
        String sharedToken,
        String settingsJson,
        Instant updatedAt) {}

