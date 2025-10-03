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
