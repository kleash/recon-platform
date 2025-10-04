# Admin Configurator Components

## Wizard Flow

- **Source Schema step** sits between Sources and Transformations. Administrators can upload a sample file or add
  fields manually to define the raw schema for each source.
- **Transformations** reuse the declared schema to surface column dropdowns and preview data consistently across the
  wizard.
- **Matching rules** draw from the schema-driven column catalogue, eliminating the need to maintain duplicate mappings
  in multiple steps.

## Backend Services

- `AdminSourceSchemaService` reuses the transformation sample parser to infer field names, data types, and sample rows
  from uploaded files.
- `AdminReconciliationService` now persists `schemaFields` on each source. The stored schema is surfaced in
  `AdminSourceDto` and exported through the reconciliation schema endpoint for downstream consumers.

## Ingestion Adapters

- `ExcelIngestionAdapter` extends the ingestion surface to multi-sheet workbooks. It honours the same parsing options as
  the preview service (`hasHeader`, `includeAllSheets`, `sheetNameColumn`, `skipRows`) so admins can configure and ingest
  Excel sources without re-uploading CSV conversions.
- Harness scenario **GLOBAL_MULTI_ASSET_COMPLEX** uses the Excel adapter alongside CSV and pipe-delimited feeds to prove
  cross-format parity. Adapter options are supplied through automation metadata to tag the originating worksheet for each
  record.

## Frontend Shell (Angular 20)

- All UI components remain standalone but no longer need the explicit `standalone: true` flag after the Angular 19+ migration.
  Keep the `imports` arrays minimal and remove unused Angular pipes (`JsonPipe`, `AsyncPipe`, etc.) to avoid compiler warnings.
- `tsconfig.json` now leverages `moduleResolution: "bundler"`; reuse the workspace config when adding tooling or storybook
  builds so imports resolve consistently across unit tests, Playwright specs, and prod builds.
- Karma tests continue to run via `ChromeHeadlessNoSandbox`; the npm script stays `ng test --watch=false --browsers=ChromeHeadlessNoSandbox`.
