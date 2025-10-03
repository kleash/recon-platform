# Decision Log

This page summarizes the architectural and process decisions that govern the Universal Reconciliation Platform. Each entry captures the context, the chosen approach, and the implications for future work. When a decision requires deeper discussion, link to the dedicated ADR file.

## D-001: Spring Boot Monolith with Modular Boundaries
- **Context:** Initial releases needed rapid iteration while supporting future decomposition.
- **Decision:** Implement a single Spring Boot application (`UniversalReconciliationPlatformApplication`) with package-level separation for controllers, services, domain entities, and adapters.
- **Rationale:** A monolith minimizes deployment overhead while allowing teams to extract modules later thanks to clean service interfaces (e.g., matching, exports, ingestion).
- **Implications:** Keep inter-service communication via method calls/interfaces rather than shared state; avoid cyclic dependencies between packages to ease eventual microservice extraction.

## D-002: Metadata-Driven Matching & UI Configuration
- **Context:** Business teams frequently onboard new reconciliations with varying match keys, tolerances, and workflows.
- **Decision:** Store reconciliation definitions, workflows, and grid metadata in MariaDB via entities under `domain/entity` and serve them through DTOs.
- **Rationale:** Metadata allows new reconciliations to be deployed without code changes, aligning with the configuration-over-code principle.
- **Implications:** Any new feature must include metadata schema updates and admin console support; tests must seed representative metadata before execution.

## D-003: Angular 17 Standalone Components
- **Context:** The analyst UI required fast navigation, modular widgets, and a design system aligned with Angular Material.
- **Decision:** Use Angular 17 with standalone components (`frontend/src/app/components`) and route-level lazy loading defined in `app.routes.ts`.
- **Rationale:** Standalone components reduce NgModule boilerplate, making it easier to share UI primitives across the workspace and admin console.
- **Implications:** Maintain pure presentational components where possible and keep stateful logic in services (`ReconciliationStateService`, `ResultGridStateService`).

## D-004: Playwright-Based Automation Harness
- **Context:** Regression coverage needed to span backend, frontend, and ingestion flows in a realistic environment.
- **Decision:** Build a Playwright smoke suite (`automation/regression`) that prepares both backend and frontend, seeds data, and exercises end-to-end scenarios.
- **Rationale:** Playwright provides cross-browser coverage with rich debugging artifacts while enabling headless CI execution.
- **Implications:** Changes to critical workflows must include Playwright updates; `npm test` in the automation directory becomes part of the PR checklist.

## D-005: Shared Ingestion SDK for CLI & Backend
- **Context:** Ingestion logic was duplicated between the platform and standalone examples, causing drift.
- **Decision:** Extract ingestion utilities into `libraries/ingestion-sdk`, consumed by backend services and CLI scripts.
- **Rationale:** A shared SDK enforces consistent validation, transformation, and error handling.
- **Implications:** New ingestion features must land in the SDK first, then be referenced by backend/CLI; semantic versioning of the SDK is required for external consumers.

## D-006: LDAP + JWT Security Model
- **Context:** Enterprise deployments require integration with corporate directories and auditable session handling.
- **Decision:** Authenticate against LDAP (with LDIF fixtures for dev) and issue JWTs consumed by the Angular app via `AuthInterceptor`.
- **Rationale:** Keeps identity management external while enabling stateless API servers.
- **Implications:** Route guards and service methods must validate maker/checker permissions based on LDAP group DNs. Token refresh/expiry policies must stay aligned with enterprise standards.

## D-007: System Activity Feed as Audit Backbone
- **Context:** Audit teams needed detailed, immutable timelines of reconciliation events.
- **Decision:** Persist structured system activity entries (`SystemActivityService`, `domain/enums/SystemEventType`) and expose them through `/api/system-activity` for UI dashboards and downstream observability.
- **Rationale:** Centralized event storage simplifies compliance reporting and incident investigations.
- **Implications:** Any new action with audit relevance must emit a `SystemEventType`. Events should include correlation IDs to trace runs, breaks, and exports.

## D-008: Maker/Checker Enforcement in Admin Workflows
- **Context:** Configuration changes must undergo dual control.
- **Decision:** Require access control entries and admin services (`service/admin/*`) to validate maker/checker roles before persisting metadata updates.
- **Rationale:** Embedding governance into services and the Angular admin console prevents policy violations.
- **Implications:** Admin UI must surface draft vs. published states; automated tests should verify that checkers cannot self-approve their own changes.

## D-009: Historical Seed Scripts for Scale Testing
- **Context:** Need to validate performance across large time windows and run volumes.
- **Decision:** Maintain shell scripts (`scripts/seed-historical.sh`, `scripts/verify-historical-seed.sh`) that generate multi-day datasets and run verification queries.
- **Rationale:** Keeps non-functional testing reproducible without manual database setup.
- **Implications:** Feature teams must update seed scripts when schema changes affect historical data; CI may run lighter variants during PR validation.

## D-010: Documentation-First Knowledge Base
- **Context:** Complex workflows span backend, frontend, ingestion, and automation.
- **Decision:** Centralize living documentation under `docs/wiki` with specialized guides (architecture, components, onboarding, development workflow, API reference).
- **Rationale:** Reduces ramp-up time for new contributors and provides a single source of truth for product, engineering, and operations.
- **Implications:** Every change affecting behavior must include wiki updates; PR templates should call out documentation impact.

Refer to this log when proposing new features to ensure alignment with past decisions and to identify whether a new ADR is required.
