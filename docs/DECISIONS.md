- **ADR: Reconciliation workflow observability (2025-10-06)**
  - Problem: Correlating reconciliation run execution with downstream break persistence required manual database queries and system activity lookups; bulk maker/checker operations left sparse traces in application logs.
  - Decision: Instrument `ReconciliationService`, `DynamicMatchingEngine`, and `BreakService` with structured INFO/DEBUG logs keyed by definition code, correlation ID, and match/break counts. Surface inline documentation in the Angular workspace to make the refresh and locking model explicit.
  - Consequences: Support teams can replay reconciliation events from log streams without ad-hoc SQL, and engineers extending the workspace have guide rails that explain when to emit break events or respect the bulk-edit selection lock.
- **ADR: Source schema as first-class metadata (2025-10-03)**
  - Problem: The legacy schema step duplicated matching controls, stored transformation snippets inline, and did not
    persist a reusable view of raw source columns. Dropdowns in subsequent steps routinely drifted from the actual
    source payload.
  - Decision: Introduce a dedicated Source Schema step that persists `schemaFields` with name, display label, data
    type, and required flag per source. The step supports manual editing and automated inference from sample files
    using the existing transformation preview pipeline.
  - Consequences: Matching and transformation steps consume a single source-of-truth column catalogue, making
    dropdowns deterministic and keeping metadata changes auditable. Field-level transformation editing moved fully
    into the transformations step.
- **ADR: Excel ingestion adapter and multi-format showcase (2025-10-04)**
  - Problem: The automation harness relied on CSV-only fixtures, limiting our ability to validate Admin Configurator
    support for native workbooks and complex, multi-source reconciliations.
  - Decision: Implement a production-grade `ExcelIngestionAdapter` that mirrors preview semantics and extend the
    harness with a six-source scenario (`GLOBAL_MULTI_ASSET_COMPLEX`) covering Excel, CSV, and delimited text inputs.
  - Consequences: Admins author multi-sheet configurations without file conversions, the ingestion SDK derives the
    correct adapter type based on media type, and regression suites can exercise every transformation and matching
    feature in a single run.
- **ADR: Angular 20 migration baseline (2025-10-04)**
  - Problem: Angular 17/19 dependency lag blocked adoption of Ivy build fixes, signal-based APIs, and required Node 20 support across tooling. Tooling also emitted unsupported runtime warnings (Node 24) and TS 5.2 could not satisfy Angular 20 schematics.
  - Decision: Sequentially run `ng update` through v18, v19, and v20, pin TypeScript 5.8.2 ahead of schematics, accept the CLI's `moduleResolution: bundler` migration, and embrace the new default `standalone` semantics (removing redundant `standalone: true`). Node 20.19.5 is now the documented floor for local and CI environments.
  - Consequences: Frontend builds used Angular 20.3.x without warnings, quality gates use a consistent Node toolchain, and future upgrades can assume bundler resolution plus standalone-by-default components. Documentation now captures the required Node/Bash setup and the cleanup expected after migrations.
- **ADR: Transformation exemplars baked into seed data (2025-10-05)**
  - Problem: The multi-source historical seed shipped with empty transformation plans, leaving analysts without concrete
    examples of dataset Groovy, row filtering, or column pipelines when cloning the scenario.
  - Decision: Populate every `GLOBAL_MULTI_ASSET` source with a dataset script plus representative row and column
    operations, and assert this contract in automation so accidental regressions surface immediately.
  - Consequences: New reconciliations cloned from the showcase begin with working transformations, regression suites
    guarantee the payloads stay enriched, and onboarding documentation can point to living examples instead of stale snippets.
