package com.universal.reconciliation.domain.dto.admin;

import com.universal.reconciliation.domain.enums.AccessRole;

/**
 * Access control metadata surfaced to admin clients.
 */
public record AdminAccessControlEntryDto(
        Long id,
        String ldapGroupDn,
        AccessRole role,
        String product,
        String subProduct,
        String entityName,
        boolean notifyOnPublish,
        boolean notifyOnIngestionFailure,
        String notificationChannel) {}

