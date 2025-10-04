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

## Cross-format Ingestion
- **Prefix raw columns per source**: The multi-source example uses `gm_*`, `apac_*`, `emea_*`, `amer_*`, `deriv_*`, and
  `cust_*` naming to guarantee uniqueness. Canonical mappings then target shared `derived_*` columns to keep matching
  logic consistent.
- **Carry sheet metadata forward**: When ingesting Excel workbooks, always enable `includeSheetNameColumn` and store the
  sheet marker in a dedicated derived field. This keeps provenance visible in analyst views and simplifies
  troubleshooting.
- **Mirror preview options**: Adapter metadata submitted during ingestion should reuse the same keys (`hasHeader`,
  `sheetNames`, `includeAllSheets`, `skipRows`) as the admin preview API so configuration and automation remain aligned.
- **Ship working transformations**: When publishing reference reconciliations (integration harness, historical seed),
  include a dataset Groovy script plus at least one row and column operation per source so downstream teams can copy an
  end-to-end normalization pattern.

## Angular 20 Frontend
- **Embrace standalone-by-default**: Angular 19+ removes the need to specify `standalone: true` on components that are
  not declared in NgModules. Continue to declare explicit `imports` arrays and only add `standalone: false` when a
  declaration lives inside a module.
- **Keep `imports` minimal**: The Angular compiler now surfaces `NG8113` warnings for unused pipes/directives. Remove
  any unused entries (e.g., legacy `JsonPipe` or `AsyncPipe`) immediately after migrations to keep builds clean.
- **Bundler module resolution**: The CLI migrates `tsconfig.json` to `moduleResolution: "bundler"`. Rely on explicit
  relative imports (no implicit index barrel resolution) and ensure custom tooling (Jest, ESLint, ts-node) reuses the
  Angular-provided TS configuration.
