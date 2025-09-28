# Admin Reconciliation Configurator Guide

The Admin Reconciliation Configurator lets platform administrators author, publish, and operate
reconciliations without redeploying the Universal Reconciliation Platform. This guide walks through
the end-to-end workflow, highlights key validation rules, and points to supporting automation and
examples.

## Prerequisites

- Your user must carry the `ROLE_RECON_ADMIN` LDAP group. The embedded demo directory ships with the
  `admin1/password` account assigned to that role (`cn=ROLE_RECON_ADMIN,ou=groups,...`).
- The backend application is running locally (`./mvnw spring-boot:run`). Start the Angular frontend
  (`npm start`) if you want to follow the UI flow in a browser.

## Navigating the Administration Workspace

1. Log in via the SPA and click the **Administration** tab that appears for admin users.
2. The catalog displays all configured reconciliations with lifecycle badges, owner details, last
   updated timestamps, and maker/checker state. Use the search, owner, and date filters to narrow the
   list.
3. Selecting a row opens the detail view with schema download links, ingestion helpers (curl
   snippets, endpoint URLs), auto-trigger configuration, and the recent ingestion activity feed.

## Authoring a Reconciliation

The **New reconciliation** action launches a six-step wizard:

1. **Definition** – Capture the code, name, description, owner, maker/checker toggle, and optional
   auto-trigger schedule. Optimistic locking relies on the `version` field; the UI surfaces conflicts
   from the backend if another admin saves concurrently.
2. **Sources** – Register each data source with adapter type (CSV, JDBC, REST, etc.), anchor flag,
   connection metadata, arrival expectations, and adapter-specific options (stored as JSON).
3. **Schema** – Define canonical fields, choose roles (e.g., `KEY`, `COMPARE`, `CLASSIFIER`), set
   comparison logic, tolerances, and formatting hints, then map each field to source columns. The
   backend enforces at least one anchor source, unique field names, and key-field presence.
   - **Transformation rules:** Each mapping can chain transformation rules before values hit the
     canonical layer. Choose between Groovy scripts (executed in a sandbox), Excel-style formulas, or
     the function pipeline builder (trim, case conversions, replacements, date formatting, and more).
     Use the inline validation button to compile scripts and formulas before saving, and leverage the
     preview panel with sample rows to confirm the final output.
4. **Reports** – Optional templates for downstream exports. Define column order, highlight settings,
   and whether to include matched/mismatched records.
5. **Access** – Assign LDAP groups with maker/checker/viewer roles, notification preferences, and
   optional product/entity metadata.
6. **Review & Publish** – Review a summary of the configuration, publish the reconciliation, or save
   as draft.

## Schema Export & Ingestion Helpers

- The detail view exposes a **Download schema** button and REST snippets for the ingestion endpoint.
  These mirror `GET /api/admin/reconciliations/{id}/schema` and
  `POST /api/admin/reconciliations/{id}/sources/{code}/batches`.
- Schema exports include arrival expectations, adapter options, canonical field roles, and mapping
  metadata so ETL developers can build ingestion jobs without inspecting the database directly.
- The ingestion endpoint accepts multipart requests with the batch file and a JSON metadata part
  (`adapterType`, `options`, `label`). The backend records audit events and shows the most recent 20
  batches in the detail view.
- The integration harness (`examples/integration-harness`) includes a reusable ingestion CLI
  (`integration-ingestion-cli.jar`) that demonstrates how to authenticate, discover reconciliation IDs,
  and submit CSV payloads programmatically.

## Automation & Quality Gates

- **Backend integration**: `AdminReconciliationServiceIntegrationTest` persists a full definition,
  exports the schema, and ingests a CSV batch using the real `SourceIngestionService` to guard against
  mapping regressions.
- **Playwright coverage**: `automation/regression/tests/smoke.spec.ts` now verifies the admin navigation
  entry, catalog, and wizard UI, capturing screenshots for the regression gallery.
- **Example bootstrap**: `examples/admin-configurator/scripts/bootstrap.sh` provisions the same
  reconciliation via REST, exports the schema, and submits a CSV batch—ideal for ETL teams exploring
  the API contract.
- **Integration harness**: `examples/integration-harness/scripts/run_multi_example_e2e.sh` launches the
  platform, applies admin payloads for the cash vs GL, custodian trade, and securities position
  examples, runs the bundled ingestion CLI, and asserts the resulting run summaries. This is now the
  recommended regression loop for admin-authored reconciliations.

## Operational Tips

- Use lifecycle filters to stage migrations: keep existing reconciliations in **Draft** while you
  iterate, publish when the schema is ready, and retire definitions when they should be hidden from
  analysts.
- Auto-trigger schedules require a cron expression, timezone, and optional grace period. The backend
  blocks invalid combinations (e.g., enabling auto-trigger without a cron string).
- Notification preferences on access control entries allow you to wire maker/checker or operations
  alerts into existing channels (email, Slack) via the configured notification bridge.

## Further Reading

- [API Reference – Administration](./API-Reference.md#74-administration)
- [Admin Configurator Example](../examples/admin-configurator/README.md)
- [Development Workflow](./Development-Workflow.md#81-onboarding-a-new-reconciliation-definition)
