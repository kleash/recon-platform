package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.AccessRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to manage access control entries for admin-created
 * reconciliations.
 */
public record AdminAccessControlEntryRequest(
        Long id,
        @NotBlank String ldapGroupDn,
        @NotNull AccessRole role,
        String product,
        String subProduct,
        String entityName,
        boolean notifyOnPublish,
        boolean notifyOnIngestionFailure,
        String notificationChannel) {}

