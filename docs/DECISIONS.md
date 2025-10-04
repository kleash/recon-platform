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
