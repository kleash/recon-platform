### 5.4 Database Schema Reference
The reconciliation service persists metadata, operational results, and workflow artefacts in MariaDB. The tables below list the most frequently touched entities along with their key columns.

#### Table: `reconciliation_definitions`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `code` | VARCHAR(64) | No | Human-friendly unique identifier used by APIs and UI routes. |
| `name` | VARCHAR(128) | No | Display label shown to end users. |
| `description` | VARCHAR(512) | No | Business context and scope of the reconciliation. |
| `maker_checker_enabled` | BOOLEAN | No | Enables dual-approval workflow when `true`. |

#### Table: `reconciliation_fields`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. Indexed for fast joins. |
| `source_field` | VARCHAR(128) | No | Raw column identifier from source feeds. |
| `display_name` | VARCHAR(128) | No | Friendly label rendered in the UI and reports. |
| `role` | ENUM(`KEY`,`COMPARE`,`DISPLAY`,`PRODUCT`,`SUB_PRODUCT`,`ENTITY`) | No | Drives grouping, matching, or descriptive behavior. |
| `data_type` | ENUM(`STRING`,`DECIMAL`,`INTEGER`,`DATE`) | No | Controls parsing and tolerance rules. |
| `comparison_logic` | ENUM(`EXACT_MATCH`,`CASE_INSENSITIVE`,`NUMERIC_THRESHOLD`,`DATE_ONLY`) | No | Names the evaluator strategy applied during matching. |
| `threshold_percentage` | DECIMAL(5,2) | Yes | Optional tolerance applied by numeric comparison strategies. |

#### Table: `reconciliation_runs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `run_date_time` | TIMESTAMP | No | Execution start time. |
| `trigger_type` | ENUM(`MANUAL_API`,`SCHEDULED_CRON`,`EXTERNAL_API`,`KAFKA_EVENT`) | No | Origin of the run. |
| `status` | ENUM(`SUCCESS`,`FAILED`) | No | Aggregated lifecycle state. |
| `triggered_by` | VARCHAR(128) | Yes | User DN or service principal. |
| `trigger_comments` | VARCHAR(512) | Yes | Operator-supplied context. |
| `trigger_correlation_id` | VARCHAR(64) | Yes | External reference for tracking (e.g., job ID). |
| `matched_count` | INT | No | Number of records matched in the run. |
| `mismatched_count` | INT | No | Number of breaks created due to mismatches. |
| `missing_count` | INT | No | Number of missing records detected. |

#### Table: `break_items`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `run_id` | BIGINT (FK) | No | References `reconciliation_runs.id`. |
| `break_type` | ENUM(`MISMATCH`,`MISSING_IN_SOURCE_A`,`MISSING_IN_SOURCE_B`) | No | Source of discrepancy. |
| `status` | ENUM(`OPEN`,`PENDING_APPROVAL`,`CLOSED`) | No | Workflow state; updated by maker/checker transitions. |
| `product` | VARCHAR(64) | Yes | Optional classification dimension. |
| `sub_product` | VARCHAR(64) | Yes | Secondary classification. |
| `entity_name` | VARCHAR(128) | Yes | Legal entity or fund name. |
| `detected_at` | TIMESTAMP | No | Time the break was generated. |
| `source_a_json` | CLOB | Yes | Serialized source A payload used for audit and drill-down. |
| `source_b_json` | CLOB | Yes | Serialized source B payload used for audit and drill-down. |

#### Table: `break_comments`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `break_item_id` | BIGINT (FK) | No | References `break_items.id`. |
| `actor_dn` | VARCHAR(256) | No | LDAP distinguished name of the actor. |
| `action` | VARCHAR(64) | No | Action code (e.g., `COMMENT`, `ASSIGN`, `CLOSE`). |
| `comment` | VARCHAR(2000) | No | Free-form text content. |
| `created_at` | TIMESTAMP | No | Audit timestamp in UTC. |

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

#### Table: `source_a_records` & `source_b_records`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `definition_id` | BIGINT (FK) | No | References `reconciliation_definitions.id`. |
| `transaction_id` | VARCHAR(64) | No | Unique identifier used for pairing records. |
| `amount` | DECIMAL(19,4) | No | Monetary amount normalized to base precision. |
| `currency` | VARCHAR(3) | No | ISO-4217 currency code. |
| `trade_date` | DATE | No | Transaction execution date. |
| `product` | VARCHAR(64) | No | Product family, used for access scoping. |
| `sub_product` | VARCHAR(64) | No | Product subtype. |
| `entity_name` | VARCHAR(128) | No | Legal entity or fund. |
| `account_id` | VARCHAR(64) | Yes | External account identifier. |
| `isin` | VARCHAR(12) | Yes | Security identifier if applicable. |
| `quantity` | DECIMAL(19,4) | Yes | Trade quantity. |
| `market_value` | DECIMAL(19,4) | Yes | Valuation amount. |
| `valuation_currency` | VARCHAR(3) | Yes | Currency of the valuation. |
| `valuation_date` | DATE | Yes | Date of valuation snapshot. |
| `custodian` | VARCHAR(128) | Yes | Holding custodian. |
| `portfolio_manager` | VARCHAR(128) | Yes | Responsible manager, used for workflow routing. |

#### Table: `system_activity_logs`
| Column | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `id` | BIGINT (PK) | No | Auto-increment primary key. |
| `event_type` | ENUM(`RECONCILIATION_RUN`,`BREAK_STATUS_CHANGE`,`BREAK_COMMENT`,`BREAK_BULK_ACTION`,`REPORT_EXPORT`) | No | Categorizes the event for filtering. |
| `details` | VARCHAR(2000) | No | Human-readable summary or JSON payload. |
| `recorded_at` | TIMESTAMP | No | Event time captured in UTC. |

> ℹ️ **Indexing guidance:** Create composite indexes on `(definition_id, product, sub_product)` for break and access tables to accelerate entitlement filtering. When using JPA auto-DDL, apply these indexes manually in production databases to preserve query performance.
