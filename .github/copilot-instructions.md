# GitHub Copilot Instructions

Welcome, AI contributor! This guide will help you understand the Universal Reconciliation Platform and contribute effectively.

## Architecture & Core Concepts

The platform is a metadata-driven reconciliation engine with a Spring Boot backend and an Angular 17 frontend. The core principle is **configuration over code**. Most reconciliation logic (matching rules, fields, tolerances) is defined in the database, not hardcoded in Java.

- **Backend (`backend/`):** Spring Boot services using a hexagonal architecture. Controllers in `.../http` adapt requests, services in `.../service` contain domain logic, and repositories in `.../repository` handle persistence.
- **Frontend (`frontend/`):** An Angular single-page application with standalone components. State is managed in services, particularly `ReconciliationStateService.ts`.
- **Data Model:** The database schema is critical. Key tables include `reconciliation_definitions`, `reconciliation_fields`, `reconciliation_runs`, and `break_items`. Refer to `docs/wiki/Architecture.md` for diagrams.
- **ETL Pipelines:** In development, data is seeded via ETL pipelines that extend `AbstractSampleEtlPipeline`. These classes are the primary mechanism for defining and onboarding new reconciliations.

## Primary Workflow: Creating a New Reconciliation

Your most common task will be to create a new reconciliation pipeline. Follow the pattern in `docs/wiki/Tutorial-Creating-a-New-Reconciliation.md`.

1.  **Create a Pipeline Class:** Create a new Java class in `backend/src/main/java/com/universal/reconciliation/etl/` that extends `AbstractSampleEtlPipeline`.
2.  **Define Metadata:** In the `run()` method, use the provided helper methods to:
    -   Create the `ReconciliationDefinition`.
    -   Register fields (`addField`) with roles (`KEY`, `COMPARE`, `DISPLAY`, etc.). This is the most important step.
    -   Configure Excel report layouts (`configureReportTemplate`).
    -   Set up access control for user groups (`accessControlEntryRepository`).
3.  **Load Data:**
    -   Add sample CSV data files to `backend/src/main/resources/etl/`.
    -   Implement the data loading logic in your pipeline's `run()` method, using the `readCsv` and mapping helpers to populate `SourceRecordA` and `SourceRecordB`.

**Example:** See `TradingFeeEtlPipeline.java` (from the tutorial) or other classes in the `etl` package for a complete implementation.

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