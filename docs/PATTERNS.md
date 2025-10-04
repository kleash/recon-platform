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

## Spring Boot 3.5 Backend
- **Upgrade via parent BOM**: Prefer bumping `spring-boot-starter-parent` and letting the managed BOM resolve
  dependency versions instead of pinning starters manually.
- **Monitor virtual thread defaults**: Spring Boot 3.5 auto-enables virtual threads on Java 21+. Running on Java 17 keeps
  them disabled, so no extra configuration was required during the 2025-10-04 upgrade.
- **Re-run full automation**: Framework upgrades can surface subtle behaviour changes; always run backend tests,
  Playwright automation, the integration harness, and seed scripts before shipping the change.
