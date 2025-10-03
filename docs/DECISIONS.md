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
