# GitHub Copilot Instructions

Welcome, AI contributor! This guide will help you understand the Universal Reconciliation Platform and contribute effectively.

## Architecture & Core Concepts

The platform is a metadata-driven reconciliation engine with a Spring Boot backend and an Angular 17 frontend. The core principle is **configuration over code**. Most reconciliation logic (matching rules, fields, tolerances) is defined in the database, not hardcoded in Java.

- **Backend (`backend/`):** Spring Boot services using a hexagonal architecture. Controllers in `.../http` adapt requests, services in `.../service` contain domain logic, and repositories in `.../repository` handle persistence.
- **Frontend (`frontend/`):** An Angular single-page application with standalone components. State is managed in services, particularly `ReconciliationStateService.ts`.
- **Data Model:** The database schema is critical. Key tables include `reconciliation_definitions`, `reconciliation_fields`, `reconciliation_runs`, and `break_items`. Refer to `docs/wiki/Architecture.md` for diagrams.
- **ETL Pipelines:** In development, data is seeded via ETL pipelines that extend `AbstractExampleEtlPipeline`. These classes are the primary mechanism for defining and onboarding new reconciliations.

## Primary Workflow: Creating a New Reconciliation

Your most common task will be to create a new reconciliation pipeline. Follow the pattern in `docs/wiki/Tutorial-Creating-a-New-Reconciliation.md`.

1.  **Create a Pipeline Class:** Add a Java class under `examples/<scenario>/src/main/java/` that implements `EtlPipeline` and extends `AbstractExampleEtlPipeline`.
2.  **Define Metadata:** In the `run()` method, call the helper methods to:
    -   Create the `ReconciliationDefinition` and one or more `ReconciliationSource` records.
    -   Register canonical fields with their roles (`KEY`, `COMPARE`, `DISPLAY`, etc.) and comparison logic.
    -   Map physical source columns to canonical fields using `CanonicalFieldMapping` helpers.
    -   Configure report templates and access control entries as needed.
3.  **Load Data:**
    -   Place sample CSV files under `examples/<scenario>/src/main/resources/etl/`.
    -   Use the provided `ingestCsv` helper (backed by `SourceIngestionService`) to load each configured `ReconciliationSource`. This ensures raw data is transformed into canonical payloads using the metadata you defined.

**Example:** See `CashVsGlEtlPipeline.java` or the other classes in `examples/` for a complete implementation of the dynamic ingestion workflow.

## Development & Testing

- **Backend Build & Test:**
  ```sh
  cd backend
  ./mvnw test
  ```
- **Frontend Build & Test:**
  ```sh
  cd frontend
  npm test -- --watch=false --browsers=ChromeHeadless
  ```
- **Running Locally:** Follow the `docs/wiki/Getting-Started.md` guide to run the backend and frontend services simultaneously.

## Key Files & Directories

- `docs/wiki/README.md`: Your starting point for all documentation.
- `docs/wiki/Architecture.md`: Essential reading for understanding the system design.
- `backend/src/main/java/com/universal/reconciliation/service/matching/`: Contains the core matching engine logic.
- `frontend/src/app/services/`: Location of Angular services for API interaction and state management.
- `examples/`: Contains standalone, runnable reconciliation scenarios with their own data and tests.

When making changes, always update the relevant documentation in `docs/wiki`. Prefer small, incremental commits that are logically grouped.