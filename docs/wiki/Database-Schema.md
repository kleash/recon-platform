### 5.4 Database Schema Reference

The reconciliation service persists configuration metadata, source ingestion telemetry, run outcomes, and workflow artefacts in
MariaDB. The tables below reflect the current JPA model. Column types are expressed using logical types—adjust precision/length
for the target environment as needed.

#### Table: `reconciliation_definitions`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `code` | VARCHAR(64) | No | Unique business identifier referenced by APIs and UI routes. |
| `name` | VARCHAR(128) | No | Display label for end users. |
| `description` | VARCHAR(512) | No | Business context for the reconciliation. |
| `maker_checker_enabled` | BOOLEAN | No | Enables dual-control workflow when true. |
| `status` | ENUM(`DRAFT`,`PUBLISHED`,`RETIRED`) | No | Lifecycle state managed by admins. |
| `notes` | TEXT | Yes | Rich notes captured during configuration reviews. |
| `owned_by` | VARCHAR(128) | Yes | Owning team or cost centre. |
| `created_by` | VARCHAR(128) | Yes | LDAP user that created the definition. |
| `updated_by` | VARCHAR(128) | Yes | LDAP user that last modified the draft. |
| `auto_trigger_enabled` | BOOLEAN | No | When true the definition is eligible for cron triggers. |
| `auto_trigger_cron` | VARCHAR(64) | Yes | Cron expression interpreted in `auto_trigger_timezone`. |
| `auto_trigger_timezone` | VARCHAR(64) | Yes | Olson timezone for the schedule. |
| `auto_trigger_grace_minutes` | INT | Yes | Delay tolerated after the cron fires before alerting. |
| `created_at` | TIMESTAMP | No | Creation timestamp (UTC). |
| `updated_at` | TIMESTAMP | No | Last update timestamp (UTC). |
| `published_at` | TIMESTAMP | Yes | Populated on publish. |
| `published_by` | VARCHAR(128) | Yes | Actor that published the definition. |
| `retired_at` | TIMESTAMP | Yes | Populated on retirement. |
| `retired_by` | VARCHAR(128) | Yes | Actor that retired the definition. |
| `version` | BIGINT | No | Optimistic locking token incremented on update. |

#### Table: `reconciliation_sources`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `code` | VARCHAR(64) | No | Short identifier (e.g., `CASH`, `GL`). |
| `display_name` | VARCHAR(128) | No | Friendly label shown in the admin UI. |
| `adapter_type` | ENUM(`CSV_FILE`,`FIXED_WIDTH_FILE`,`XML_FILE`,`JSON_FILE`,`DATABASE`,`REST_API`,`MESSAGE_QUEUE`,`LLM_DOCUMENT`) | No | Ingestion adapter implementation. |
| `anchor` | BOOLEAN | No | Identifies the primary matching source. |
| `description` | VARCHAR(512) | Yes | Business description for operators. |
| `connection_config` | TEXT | Yes | Connection metadata for JDBC/API adapters. |
| `arrival_expectation` | VARCHAR(256) | Yes | Human-readable SLA descriptor. |
| `arrival_timezone` | VARCHAR(64) | Yes | Timezone for the arrival SLA. |
| `arrival_sla_minutes` | INT | Yes | SLA window in minutes. |
| `adapter_options` | TEXT | Yes | Adapter-specific options (CSV delimiter, authentication tokens, LLM prompt templates, etc.). |
| `transformation_plan` | TEXT | Yes | Serialized function/Groovy/Excel pipeline applied before canonical mapping. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last modification timestamp. |

Normalization logic is stored as ordered entries in `canonical_field_transformations`, allowing Groovy, Excel, or
pipeline steps without relying on inline expression columns.

#### Table: `canonical_fields`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `canonical_name` | VARCHAR(128) | No | Internal attribute key used across sources. |
| `display_name` | VARCHAR(128) | No | Label surfaced in UI grids and reports. |
| `role` | ENUM(`KEY`,`COMPARE`,`DISPLAY`,`PRODUCT`,`SUB_PRODUCT`,`ENTITY`,`CLASSIFIER`,`ATTRIBUTE`) | No | Drives matching, filtering, and entitlements. |
| `data_type` | ENUM(`STRING`,`DECIMAL`,`INTEGER`,`DATE`,`DATETIME`,`BOOLEAN`) | No | Influences parsing and formatting. |
| `comparison_logic` | ENUM(`EXACT_MATCH`,`CASE_INSENSITIVE`,`NUMERIC_THRESHOLD`,`DATE_ONLY`) | No | Comparator applied during matching. |
| `threshold_percentage` | DECIMAL(7,3) | Yes | Tolerance for numeric comparisons. |
| `classifier_tag` | VARCHAR(64) | Yes | Optional tag used for downstream grouping. |
| `formatting_hint` | VARCHAR(128) | Yes | Presentation hint for UI/export rendering. |
| `display_order` | INT | Yes | Suggested ordering for UI grids. |
| `required` | BOOLEAN | No | Indicates whether the field must be populated for matching. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last modification timestamp. |

#### Table: `reconciliation_source_schema_fields`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `source_id` | BIGINT (FK) | No | References `reconciliation_sources.id`. |
| `position` | INT | No | Order of the field in the source schema definition. |
| `field_name` | VARCHAR(128) | No | Raw column name as published by the upstream source. |
| `display_name` | VARCHAR(256) | Yes | Friendly label surfaced in the configurator. |
| `data_type` | ENUM(`STRING`,`DECIMAL`,`INTEGER`,`DATE`,`DATETIME`,`BOOLEAN`) | Yes | Declared raw data type. |
| `required` | BOOLEAN | No | Whether the upstream feed guarantees the column. |
| `description` | VARCHAR(512) | Yes | Business context or parsing hints for the column. |

#### Table: `canonical_field_mappings`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `canonical_field_id` | BIGINT (FK) | No | References `canonical_fields.id`. |
| `source_id` | BIGINT (FK) | No | References `reconciliation_sources.id`. |
| `source_column` | VARCHAR(128) | No | Raw column or attribute from the source payload. |
| `default_value` | VARCHAR(256) | Yes | Applied when the source column is missing. |
| `source_date_format` | VARCHAR(64) | Yes | Optional format string used to parse incoming date/datetime values. |
| `target_date_format` | VARCHAR(64) | Yes | Optional format string applied after parsing when emitting canonical values. |
| `ordinal_position` | INT | Yes | Ordering when multiple mappings exist for the same field. |
| `required` | BOOLEAN | No | Enforced during ingestion. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last modification timestamp. |

#### Table: `canonical_field_transformations`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `mapping_id` | BIGINT (FK) | No | References `canonical_field_mappings.id`. |
| `type` | ENUM(`GROOVY_SCRIPT`,`EXCEL_FORMULA`,`FUNCTION_PIPELINE`) | No | Transformation type (see `TransformationType`). |
| `expression` | TEXT | Yes | Expression content (Groovy script, regex, etc.). |
| `configuration` | TEXT | Yes | Serialized configuration map. |
| `display_order` | INT | No | Execution order within the transformation chain. |
| `active` | BOOLEAN | No | Toggle for staged transformations. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last modification timestamp. |

#### Table: `access_control_entries`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `ldap_group_dn` | VARCHAR(256) | No | Group distinguished name mapped from LDAP. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `product` | VARCHAR(64) | Yes | Optional entitlement filter. |
| `sub_product` | VARCHAR(64) | Yes | Secondary entitlement filter. |
| `entity_name` | VARCHAR(128) | Yes | Legal entity or fund filter. |
| `role` | ENUM(`VIEWER`,`MAKER`,`CHECKER`) | No | Determines UI capabilities and workflow permissions. |
| `notify_on_publish` | BOOLEAN | No | Sends notifications when configuration is published. |
| `notify_on_ingestion_failure` | BOOLEAN | No | Sends notifications when batch ingestion fails. |
| `notification_channel` | VARCHAR(256) | Yes | Optional Teams/Slack/Email channel reference. |

#### Table: `report_templates`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `name` | VARCHAR(128) | No | Template name displayed in UI. |
| `description` | VARCHAR(256) | No | Business usage description. |
| `include_matched` | BOOLEAN | No | Include matched rows when true. |
| `include_mismatched` | BOOLEAN | No | Include mismatched rows when true. |
| `include_missing` | BOOLEAN | No | Include missing rows when true. |
| `highlight_differences` | BOOLEAN | No | Enables conditional formatting. |

#### Table: `report_columns`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `template_id` | BIGINT (FK) | No | References `report_templates.id`. |
| `header` | VARCHAR(128) | No | Column header text. |
| `source` | ENUM(`SOURCE_A`,`SOURCE_B`,`BREAK_METADATA`) | No | Data source for column values. |
| `source_field` | VARCHAR(128) | Yes | Source key when pulling from A/B payloads or break metadata. |
| `display_order` | INT | No | Zero-based ordering. |
| `highlight_differences` | BOOLEAN | No | Overrides template-level highlight behaviour. |

#### Table: `reconciliation_runs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `run_date_time` | TIMESTAMP | No | Execution start time (UTC). |
| `trigger_type` | ENUM(`MANUAL_API`,`SCHEDULED_CRON`,`EXTERNAL_API`,`KAFKA_EVENT`) | No | Trigger origin. |
| `status` | ENUM(`SUCCESS`,`FAILED`) | No | Run outcome. |
| `triggered_by` | VARCHAR(128) | Yes | User DN or service principal. |
| `trigger_comments` | VARCHAR(512) | Yes | Operator-supplied comments. |
| `trigger_correlation_id` | VARCHAR(64) | Yes | External reference ID. |
| `matched_count` | INT | No | Number of matched records. |
| `mismatched_count` | INT | No | Number of mismatches promoted to breaks. |
| `missing_count` | INT | No | Number of missing records. |

#### Table: `break_items`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `run_id` | BIGINT (FK) | No | References `reconciliation_runs.id`. |
| `break_type` | ENUM(`MISMATCH`,`ANCHOR_MISSING`,`SOURCE_MISSING`,`MISSING_IN_SOURCE_A`,`MISSING_IN_SOURCE_B`) | No | Type of exception (`MISSING_IN_SOURCE_*` are legacy values retained for backward compatibility). |
| `status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Workflow state. |
| `detected_at` | TIMESTAMP | No | Detection timestamp. |
| `submitted_by_dn` | VARCHAR(256) | Yes | Maker identity for approvals. |
| `submitted_by_group` | VARCHAR(256) | Yes | Maker group at submission time. |
| `submitted_at` | TIMESTAMP | Yes | When approval was requested. |
| `product` | VARCHAR(64) | Yes | Classification dimension. |
| `sub_product` | VARCHAR(64) | Yes | Secondary classification. |
| `entity_name` | VARCHAR(128) | Yes | Entity dimension. |
| `source_payload_json` | LONGTEXT | Yes | Consolidated source payload for UI drill-down. |
| `classification_json` | LONGTEXT | Yes | Serialized classification map. |
| `missing_sources_json` | LONGTEXT | Yes | Serialised list of missing sources. |
| `source_a_json` | LONGTEXT | Yes | Legacy payload for source A (deprecated). |
| `source_b_json` | LONGTEXT | Yes | Legacy payload for source B (deprecated). |

#### Table: `break_classification_values`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `attribute_key` | VARCHAR(128) | No | Flattened key for analytics queries. |
| `attribute_value` | VARCHAR(256) | Yes | Value used for aggregations and filters. |

#### Table: `break_workflow_audit`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `previous_status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Status before transition. |
| `new_status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Status after transition. |
| `actor_dn` | VARCHAR(256) | No | LDAP DN of the actor. |
| `actor_role` | ENUM(`VIEWER`,`MAKER`,`CHECKER`) | No | Role resolved at execution time. |
| `comment` | VARCHAR(2000) | Yes | Approval/rejection commentary. |
| `correlation_id` | VARCHAR(64) | Yes | Correlates bulk operations. |
| `created_at` | TIMESTAMP | No | Audit timestamp. |

#### Table: `break_comments`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `actor_dn` | VARCHAR(256) | No | LDAP DN of the actor. |
| `action` | VARCHAR(64) | No | Action code (COMMENT, ASSIGN, etc.). |
| `comment` | VARCHAR(2000) | No | Free-form note. |
| `created_at` | TIMESTAMP | No | Timestamp recorded in UTC. |

#### Table: `analyst_saved_views`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `owner` | VARCHAR(128) | No | Username of the view owner. |
| `name` | VARCHAR(120) | No | Saved view name. |
| `description` | TEXT | Yes | Optional description shown in UI. |
| `settings_json` | LONGTEXT | No | Serialized grid configuration (filters, column order, density). |
| `shared_token` | VARCHAR(64) | Yes | Unique token used for shareable links. |
| `is_shared` | BOOLEAN | No | Controls visibility beyond the owner. |
| `is_default` | BOOLEAN | No | Marks the owner’s default view. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last update timestamp. |

#### Table: `export_jobs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `owner` | VARCHAR(128) | No | Username that queued the export. |
| `job_type` | ENUM(`RESULT_DATASET`,`RUN_REPORT`) | No | Export job type. |
| `format` | ENUM(`CSV`,`JSONL`,`XLSX`,`PDF`) | No | Output format. |
| `status` | ENUM(`QUEUED`,`PROCESSING`,`COMPLETED`,`FAILED`) | No | Job lifecycle status. |
| `filters_json` | LONGTEXT | Yes | Snapshot of applied filters. |
| `settings_json` | LONGTEXT | Yes | Additional export settings (include metadata, timezone, etc.). |
| `owner_groups_json` | LONGTEXT | Yes | Caller’s groups persisted for entitlement validation. |
| `payload` | LONGBLOB | Yes | Generated file content (null until completion). |
| `file_name` | VARCHAR(256) | Yes | Suggested filename for downloads. |
| `content_hash` | VARCHAR(128) | Yes | SHA-256 hash of the payload. |
| `row_count` | BIGINT | Yes | Number of rows exported. |
| `timezone` | VARCHAR(64) | Yes | Timezone applied to timestamps inside the export. |
| `error_message` | TEXT | Yes | Populated if the job fails. |
| `created_at` | TIMESTAMP | No | Creation timestamp. |
| `updated_at` | TIMESTAMP | No | Last update timestamp. |
| `completed_at` | TIMESTAMP | Yes | Completion timestamp. |

#### Table: `system_activity_logs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `event_type` | ENUM(`RECONCILIATION_RUN`,`BREAK_STATUS_CHANGE`,`BREAK_COMMENT`,`BREAK_BULK_ACTION`,`REPORT_EXPORT`,`RECONCILIATION_CONFIG_CHANGE`,`INGESTION_BATCH_ACCEPTED`) | No | Event classification. |
| `details` | VARCHAR(2000) | No | Summary or JSON payload used in dashboards. |
| `recorded_at` | TIMESTAMP | No | Event timestamp (UTC). |

#### Table: `source_data_batches`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `source_id` | BIGINT (FK) | No | References `reconciliation_sources.id`. |
| `label` | VARCHAR(128) | No | Human-readable batch name (often the business date). |
| `status` | ENUM(`PENDING`,`LOADING`,`COMPLETE`,`FAILED`,`ARCHIVED`) | No | Batch lifecycle status. |
| `ingested_at` | TIMESTAMP | No | Ingestion timestamp. |
| `record_count` | BIGINT | Yes | Row count populated on completion. |
| `checksum` | VARCHAR(128) | Yes | Hash for idempotency. |

#### Table: `source_data_records`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `batch_id` | BIGINT (FK) | No | References `source_data_batches.id`. |
| `external_reference` | VARCHAR(128) | Yes | Optional upstream identifier (file name, trade ID). |
| `canonical_key` | VARCHAR(256) | No | Pre-computed matching key used by the engine. |
| `payload_json` | TEXT | No | Normalised source payload. |
| `metadata_json` | TEXT | Yes | Additional metadata (ingestion diagnostics, lineage). |
| `ingested_at` | TIMESTAMP | No | Record ingestion timestamp. |

#### Legacy Sample Tables
Legacy entities (`source_a_records`, `source_b_records`) remain in the codebase for backwards compatibility with historical demos. Modern pipelines seeded by `EtlPipeline` implementations now persist through the canonical staging tables above and only touch the legacy tables when explicitly requested.

> ℹ️ **Indexing guidance:** Create composite indexes on `(definition_id, product, sub_product)` for `break_items` and
> `access_control_entries` to accelerate entitlement filtering. For high-volume exports add `(definition_id, owner, status)` indexes
> on `export_jobs`. Cursor pagination over break searches relies on covering indexes for `run_id` and `status` in tandem with the
> canonical classification values.
