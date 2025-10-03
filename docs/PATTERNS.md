# Patterns

## Source Schema Capture
- **Persist inferred metadata**: Always write schema inference results to `ReconciliationSource.schemaFields` so UI and
  backend share a consistent view of available columns.
- **Centralize parsing**: Reuse `AdminSourceSchemaService` to parse sample files instead of rolling ad-hoc parsers in
  component code. The service honours the same options (`fileType`, `delimiter`, `sheetNames`, `recordPath`) as the
  transformation preview pipeline.
- **Derive display labels**: When no display name is provided, generate one by humanising the raw column (snake case /
  camel case -> title case). Apply the same helper across backend and frontend to avoid drift.

## Wizard Cohesion
- **Single source of truth for columns**: Downstream steps (Transformations, Matching, Reports) should rely on
  `availableColumns` derived from schema fields and transformation outputs rather than maintaining duplicate lists.
- **Dirty state propagation**: Updating schema fields must mark the underlying `FormArray` as dirty/touched so the
  review step surfaces unsaved changes and guards navigation.
