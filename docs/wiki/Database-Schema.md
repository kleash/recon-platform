### 5.4 Database Schema Reference
The reconciliation service persists metadata, operational results, and workflow artefacts in MariaDB. The tables below list the
most frequently touched entities along with their key columns.

#### Table: `reconciliation_definitions`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `code` | VARCHAR(64) | No | Human-friendly unique identifier used by APIs and UI routes. |
| `name` | VARCHAR(128) | No | Display label shown to end users. |
| `description` | VARCHAR(512) | No | Business context and scope of the reconciliation. |
| `status` | ENUM(`DRAFT`,`PUBLISHED`,`RETIRED`) | No | Lifecycle state maintained by the admin configurator. |
| `version` | BIGINT | No | Increments on every publish to support optimistic locking. |
| `owner` | VARCHAR(128) | Yes | Owning team or operations group. |
| `maker_checker_enabled` | BOOLEAN | No | Enables dual-approval workflow when `true`. |
| `created_at` | TIMESTAMP | No | Creation timestamp (UTC). |
| `updated_at` | TIMESTAMP | No | Last updated timestamp (UTC). |
| `updated_by` | VARCHAR(128) | Yes | LDAP user that last published the definition. |

#### Table: `reconciliation_sources`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `code` | VARCHAR(64) | No | Short identifier (e.g., `CASH`, `GL`). |
| `adapter_type` | ENUM(`CSV`,`JDBC`,`API`) | No | Drives ingestion behaviour. |
| `anchor` | BOOLEAN | No | Marks the source used as the primary match anchor. |
| `options_json` | CLOB | Yes | Adapter-specific options such as delimiter, connection string, or endpoint URL. |
| `retired_at` | TIMESTAMP | Yes | Soft-delete marker when a source is removed. |

#### Table: `reconciliation_fields`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. Indexed for fast joins. |
| `canonical_name` | VARCHAR(128) | No | Internal name used by matching and reporting. |
| `source_field` | VARCHAR(128) | No | Raw column identifier from source feeds. |
| `display_name` | VARCHAR(128) | No | Friendly label rendered in the UI and reports. |
| `role` | ENUM(`KEY`,`COMPARE`,`DISPLAY`,`PRODUCT`,`SUB_PRODUCT`,`ENTITY`) | No | Drives grouping, matching, or descriptive behaviour. |
| `data_type` | ENUM(`STRING`,`DECIMAL`,`INTEGER`,`DATE`,`BOOLEAN`) | No | Controls parsing and tolerance rules. |
| `comparison_logic` | ENUM(`EXACT_MATCH`,`CASE_INSENSITIVE`,`NUMERIC_THRESHOLD`,`DATE_ONLY`,`CUSTOM`) | No | Names the evaluator strategy applied during matching. |
| `threshold_percentage` | DECIMAL(5,2) | Yes | Optional tolerance applied by numeric comparison strategies. |

#### Table: `report_templates`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `name` | VARCHAR(128) | No | Template label shown to users. |
| `description` | VARCHAR(256) | No | Business usage description. |
| `include_matched` | BOOLEAN | No | Include matched records when `true`. |
| `include_mismatched` | BOOLEAN | No | Include mismatched records when `true`. |
| `include_missing` | BOOLEAN | No | Include missing records when `true`. |
| `highlight_differences` | BOOLEAN | No | Toggle for conditional formatting. |

#### Table: `report_columns`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `template_id` | BIGINT (FK) | No | References `report_templates.id`. |
| `header` | VARCHAR(128) | No | Column header text. |
| `source` | ENUM(`SOURCE_A`,`SOURCE_B`,`BREAK_METADATA`) | No | Determines the data source for the column value. |
| `source_field` | VARCHAR(128) | Yes | Field key when sourcing from A/B datasets. |
| `display_order` | INT | No | Zero-based ordering for Excel generation. |
| `highlight_differences` | BOOLEAN | No | Overrides template-level highlight if necessary. |

#### Table: `reconciliation_runs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `run_date_time` | TIMESTAMP | No | Execution start time. |
| `trigger_type` | ENUM(`MANUAL_UI`,`MANUAL_API`,`SCHEDULED_CRON`,`EXTERNAL_API`,`KAFKA_EVENT`) | No | Origin of the run. |
| `status` | ENUM(`SUCCESS`,`FAILED`) | No | Aggregated lifecycle state. |
| `triggered_by` | VARCHAR(128) | Yes | User DN or service principal. |
| `trigger_comments` | VARCHAR(512) | Yes | Operator-supplied context. |
| `trigger_correlation_id` | VARCHAR(64) | Yes | External reference for tracking (e.g., job ID). |
| `matched_count` | INT | No | Number of records matched in the run. |
| `mismatched_count` | INT | No | Number of breaks created due to mismatches. |
| `missing_count` | INT | No | Number of missing records detected. |

#### Table: `ingestion_batches`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | Reconciliation that owns the batch. |
| `source_code` | VARCHAR(64) | No | Source identifier the batch was uploaded for. |
| `external_reference` | VARCHAR(128) | Yes | Optional upstream reference (file name, job ID). |
| `status` | ENUM(`RECEIVED`,`PROCESSING`,`PROCESSED`,`FAILED`) | No | Tracks ingestion lifecycle. |
| `record_count` | INT | Yes | Populated after processing completes. |
| `checksum` | VARCHAR(128) | Yes | Hash used for idempotency and audit. |
| `received_at` | TIMESTAMP | No | Timestamp when the batch was created. |
| `completed_at` | TIMESTAMP | Yes | Populated when the batch leaves `PROCESSING`. |

#### Table: `break_items`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `run_id` | BIGINT (FK) | No | References `reconciliation_runs.id`. |
| `break_type` | ENUM(`MISMATCH`,`MISSING_IN_SOURCE_A`,`MISSING_IN_SOURCE_B`) | No | Source of discrepancy. |
| `status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Workflow state; updated by maker/checker transitions. |
| `product` | VARCHAR(64) | Yes | Optional classification dimension. |
| `sub_product` | VARCHAR(64) | Yes | Secondary classification. |
| `entity_name` | VARCHAR(128) | Yes | Legal entity or fund name. |
| `submitted_by_dn` | VARCHAR(256) | Yes | Maker LDAP DN captured when requesting approval. |
| `submitted_by_group` | VARCHAR(256) | Yes | Maker LDAP group recorded for audit separation. |
| `detected_at` | TIMESTAMP | No | Time the break was generated. |
| `source_a_json` | CLOB | Yes | Serialized source A payload used for audit and drill-down. |
| `source_b_json` | CLOB | Yes | Serialized source B payload used for audit and drill-down. |

#### Table: `break_workflow_audit`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_id` | BIGINT (FK) | No | References `break_items.id`. |
| `previous_status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Status before the transition. |
| `new_status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`REJECTED`,`CLOSED`) | No | Status after the transition. |
| `actor_dn` | VARCHAR(256) | No | LDAP distinguished name of the actor. |
| `actor_role` | ENUM(`MAKER`,`CHECKER`,`SYSTEM`) | No | Resolved role at the time of the transition. |
| `comment` | VARCHAR(2000) | Yes | Mandatory for approvals/rejections. |
| `correlation_id` | VARCHAR(64) | Yes | Correlates bulk transitions. |
| `created_at` | TIMESTAMP | No | Audit timestamp in UTC. |

#### Table: `break_comments`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `actor_dn` | VARCHAR(256) | No | LDAP distinguished name of the actor. |
| `action` | VARCHAR(64) | No | Action code (e.g., `COMMENT`, `ASSIGN`, `CLOSE`). |
| `comment` | VARCHAR(2000) | No | Free-form text content. |
| `created_at` | TIMESTAMP | No | Audit timestamp in UTC. |

#### Table: `break_attachments`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `file_name` | VARCHAR(256) | No | Original filename uploaded. |
| `content_type` | VARCHAR(128) | No | MIME type. |
| `storage_key` | VARCHAR(256) | No | Location in object storage. |
| `uploaded_by` | VARCHAR(256) | No | LDAP DN of the uploader. |
| `uploaded_at` | TIMESTAMP | No | Upload timestamp. |

#### Table: `access_control_entries`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `ldap_group_dn` | VARCHAR(256) | No | Group distinguished name mapped from LDAP. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `product` | VARCHAR(64) | Yes | Optional dimension filter for entitlements. |
| `sub_product` | VARCHAR(64) | Yes | Optional secondary dimension filter. |
| `entity_name` | VARCHAR(128) | Yes | Optional legal entity filter. |
| `role` | ENUM(`VIEWER`,`MAKER`,`CHECKER`) | No | Determines UI capabilities and API scopes. |

#### Table: `system_activity_logs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `event_type` | ENUM(`RECONCILIATION_RUN`,`BREAK_STATUS_CHANGE`,`BREAK_COMMENT`,`BREAK_BULK_ACTION`,`REPORT_EXPORT`,`INGESTION_BATCH`,`CONFIGURATION_PUBLISH`) | No | Categorises the event for filtering. |
| `details` | VARCHAR(2000) | No | Human-readable summary or JSON payload. |
| `recorded_at` | TIMESTAMP | No | Event time captured in UTC. |

> ℹ️ **Indexing guidance:** Create composite indexes on `(definition_id, product, sub_product)` for break and access tables to accelerate entitlement filtering. When using JPA auto-DDL, apply these indexes manually in production databases to preserve query performance. Ingestion batch lookups benefit from `(definition_id, source_code, status)` indexes for run orchestration dashboards.
