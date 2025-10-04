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
- **ADR: Spring Boot 3.5.6 adoption (2025-10-04)**
  - Problem: Remaining on Spring Boot 3.2.5 limited framework, security, and servlet container fixes and left us
    unprepared for Java 21 enablement.
  - Decision: Upgrade the backend parent BOM to Spring Boot 3.5.6, validate behaviour on Java 17, and execute all
    quality gates (backend unit tests, Playwright, integration harness, seeding scripts) to confirm runtime parity.
  - Consequences: The service now inherits Spring Framework 6.2.11, Spring Security 6.5.x, and Hibernate 6.6.29. The
    application continues to run on Java 17 with virtual threads disabled by default; future work can focus on Java 21
    adoption instead of dependency catch-up.
