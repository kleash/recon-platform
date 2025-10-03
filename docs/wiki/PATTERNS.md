# Coding Patterns & Conventions

This guide describes the recurring patterns used across the Universal Reconciliation Platform. Following these conventions keeps codebases consistent and eases onboarding for new contributors.

## Backend (Spring Boot)

### Dependency Injection & Constructors
- Prefer constructor injection for all services, controllers, and components (see `ReconciliationService`, `ReconciliationController`).
- Avoid field injection; this improves testability and enforces immutability for dependencies.

### DTO Mapping
- DTOs live under `domain/dto` and are immutable records when possible. Services convert entities to DTOs using dedicated mappers (e.g., `BreakMapper`).
- Controllers should never expose entities directly; always return DTOs to maintain a stable API contract.

### Service Responsibilities
- Keep services cohesive: `ReconciliationService` orchestrates runs, `BreakSearchService` handles search/pagination, `ExportService` manages file generation.
- Cross-cutting concerns (access control, analytics, activity logging) are encapsulated in helper services injected where needed.

### Repository Usage
- Use Spring Data JPA repositories for common queries and define custom methods when performance requires it. For example, `ReconciliationRunRepository.findTopByDefinitionOrderByRunDateTimeDesc` drives dashboard views.
- When pagination or filtering logic grows complex, push it into dedicated search services (`service/search`) rather than embedding query logic inside controllers.

### Validation & Error Handling
- Validate inputs using Jakarta validation annotations on DTOs and method parameters. `RestExceptionHandler` centralizes translation of exceptions into HTTP responses.
- Throw domain-specific exceptions (e.g., `AccessDeniedException`, `IllegalArgumentException`) with meaningful messages for UI display.

### Logging & Activity
- Emit structured system events through `SystemActivityService`. Log correlation IDs for runs and breaks to aid troubleshooting.
- Use SLF4J loggers sparingly in services; prefer the structured activity feed for audit trails.

### Testing
- Write Spring Boot tests in `backend/src/test/java` using `@SpringBootTest` or slice annotations as appropriate.
- Mock external dependencies (e.g., AI adapters) via interfaces to keep tests deterministic.

## Frontend (Angular 17)

### Standalone Components & Module-Free Design
- Build components as standalone to leverage Angular 17 features. Import Material modules directly within component definitions.
- Organize components by feature (`components/run-detail`, `components/result-grid`) and keep styling in component-scoped CSS/SCSS files.

### Reactive State Management
- Encapsulate API calls and derived state in services (`ReconciliationStateService`, `ResultGridStateService`, `AdminReconciliationStateService`).
- Expose read-only observables (`Observable<T>`) to components; trigger updates via explicit methods (e.g., `loadRun(id)`).

### HTTP & Authentication
- Use `ApiService` as the single integration point for backend HTTP calls. Keep request/response interfaces in `frontend/src/app/models`.
- `AuthInterceptor` attaches JWT tokens automatically; components should not manipulate headers directly.

### UI Interaction Patterns
- Use Angular Material tables and CDK virtual scrolling for large datasets (as implemented in `components/result-grid`).
- For modal/drawer interactions, maintain local component state and emit events upward to parent components (`breakSelected`, `filterChanged`).
- Surface notifications via `NotificationService` to ensure consistent toast styling and accessibility.

### Testing
- Store `.spec.ts` alongside each component/service. Use `TestBed` with standalone component configuration.
- Mock services using Angular dependency injection; verify DOM interactions and observable emissions.

## Automation & Tooling

### Playwright Tests
- Keep Playwright tests in `automation/regression/tests`; share fixtures via `tests/fixtures` to avoid duplication.
- Use the provided `scripts/prepare.mjs` to build backend/frontend before running tests locally or in CI.
- Capture screenshots and traces for failures; commit updates to fixtures when UI or workflows change intentionally.

### Seed & Example Scripts
- Standardize CLI usage via scripts under `scripts/` and `examples/integration-harness/scripts`. These scripts are part of the required validation checklist.
- Update both scripts and wiki documentation whenever new reconciliation examples are added.

## Documentation Practices
- Every feature touching behavior must reference or update the relevant wiki page (e.g., onboarding, API reference, this pattern guide).
- Use Mermaid diagrams for flows; keep file paths relative when linking inside the wiki.

## Code Review Checklist
- Ensure dependency injection follows conventions (constructor-based).
- Confirm DTOs shield internal entities from API consumers.
- Verify access control checks exist for maker/checker-sensitive operations.
- Update Playwright and seed scripts as needed.
- Include automated test updates and run results in PRs.

Adhering to these patterns ensures code remains maintainable, testable, and compliant with platform governance.
