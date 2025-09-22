# Platform Feature Matrix

## Backend Features
- Spring Boot REST API surface for authentication, reconciliation discovery, run execution, break management, exports, and system activity feeds.
- Metadata-driven matching engine that enforces access control, persists runs, calculates analytics, and records audit events for every reconciliation execution.
- Maker-checker aware workflow services supporting comments, status transitions, and bulk updates on breaks.
- Excel export engine backed by configurable report templates and audit logging for download events.
- Startup ETL pipelines that register reconciliation definitions, provision LDAP-scoped access control, and load sample source data from CSV into MariaDB tables.

## Frontend Features
- Angular standalone application with authenticated sessions, reconciliation selection, trigger configuration, and responsive dashboard layout.
- Shared state service coordinating reconciliation lists, run summaries, break inventories, filters, and system activity streams.
- UI workflows for adding comments, updating statuses, applying bulk actions, and exporting Excel reports directly from the dashboard.
- REST client wrappers that translate UI interactions into backend API calls, including filter-aware run retrieval and export downloads.
- Activity feed and break detail components that present audit trails, side-by-side source data, and workflow context for analysts.
