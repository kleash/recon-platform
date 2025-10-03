package com.universal.reconciliation.domain.entity;

import com.universal.reconciliation.domain.enums.FieldDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Embedded value representing a declared column inside a source schema.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class SourceSchemaField {

    @Column(name = "field_name", nullable = false, length = 128)
    private String name;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 32)
    private FieldDataType dataType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "description", length = 512)
    private String description;
}
