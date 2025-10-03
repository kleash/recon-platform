# Component Catalog

The Universal Reconciliation Platform is composed of backend services, a modular analyst UI, automation harnesses, and supporting tooling. This catalog documents the key building blocks, their responsibilities, and example interactions so feature teams can onboard quickly.

## Backend Services (Spring Boot)

| Component | Location | Responsibilities | Usage Example |
| --- | --- | --- | --- |
| `UniversalReconciliationPlatformApplication` | `backend/src/main/java/com/universal/reconciliation/UniversalReconciliationPlatformApplication.java` | Boots the Spring application context and enables async execution for non-blocking workflow steps. | Start the service with `./mvnw spring-boot:run` to expose the REST APIs on port 8080. |
| Controllers | `backend/src/main/java/com/universal/reconciliation/controller` | Surface REST endpoints for reconciliation runs, approvals, break actions, exports, authentication, saved views, and system activity feeds. | Trigger a run: `POST /api/reconciliations/{id}/run` with optional `TriggerRunRequest` payload to kick off metadata-driven matching. |
| `ReconciliationService` | `backend/src/main/java/com/universal/reconciliation/service/ReconciliationService.java` | Orchestrates run execution, enforces access control, persists runs and breaks, computes analytics, and emits audit events. | Used by `ReconciliationController` to create a run and return `RunDetailDto` for UI dashboards. |
| Matching Engine | `backend/src/main/java/com/universal/reconciliation/service/matching` | Applies metadata-driven rules to staged data, generates break candidates, and aggregates matched/mismatched counts. | `MatchingEngine.execute(ReconciliationDefinition)` returns a `MatchingResult` consumed during run creation. |
| Break Management Services | `backend/src/main/java/com/universal/reconciliation/service/BreakService.java`, `backend/src/main/java/com/universal/reconciliation/service/BreakSelectionService.java`, `backend/src/main/java/com/universal/reconciliation/service/BreakAccessService.java`, `backend/src/main/java/com/universal/reconciliation/service/BreakMapper.java` | Manage break lifecycle transitions, enforce maker/checker permissions, materialize grid rows, and convert JPA entities into DTOs. | The UI invokes `PATCH /api/breaks/{id}` to update status; service validates role via `BreakAccessService`. |
| Search Services | `backend/src/main/java/com/universal/reconciliation/service/BreakSearchService.java`, `backend/src/main/java/com/universal/reconciliation/service/search/*` | Build dynamic SQL predicates, paginate results, and construct metadata describing columns and filters. | `GET /api/reconciliations/{id}/results` returns rows and `BreakSearchPageInfoDto` for infinite scrolling grids. |
| Export Services | `backend/src/main/java/com/universal/reconciliation/service/ExportService.java`, `backend/src/main/java/com/universal/reconciliation/service/ExportJobService.java`, `backend/src/main/java/com/universal/reconciliation/service/export/*` | Generate Excel extracts from templates, queue export jobs, and stream files back via `/api/exports/{id}` endpoints. | Schedule asynchronous export: `POST /api/exports/{definitionId}` returns a job ID polled by the UI. |
| Workflow & Activity | `backend/src/main/java/com/universal/reconciliation/service/SystemActivityService.java`, `backend/src/main/java/com/universal/reconciliation/domain/enums/SystemEventType.java`, `backend/src/main/java/com/universal/reconciliation/controller/SystemActivityController.java` | Persist structured activity events for reconciliation runs, approvals, and configuration changes; expose feeds for observability. | `GET /api/system-activity` streams the latest 200 events to populate the analyst activity pane. |
| Ingestion Services | `backend/src/main/java/com/universal/reconciliation/service/ingestion` | Normalize data feeds by delegating to adapters (`CsvIngestionAdapter`, `OpenAiDocumentIngestionAdapter`), then persist to staging tables. | `SourceIngestionService.ingest(IngestionAdapterRequest)` is invoked by admin workflows or CLI tooling to load source files. |
| Admin APIs | `backend/src/main/java/com/universal/reconciliation/controller/admin/*`, `backend/src/main/java/com/universal/reconciliation/service/admin/*`, `backend/src/main/java/com/universal/reconciliation/repository/*` | Maintain reconciliation definitions, transformation scripts, access control entries, and user groups. | `PUT /api/admin/reconciliations/{id}` updates metadata; the `AdminReconciliationController` validates maker-checker requirements. |
| Security Layer | `backend/src/main/java/com/universal/reconciliation/security/*` | Authenticate via LDAP/JWT, populate `UserContext`, and enforce role checks throughout services. | Incoming requests supply JWT tokens; `UserContext` exposes user/group info to controller methods. |
| Persistence Layer | `backend/src/main/java/com/universal/reconciliation/domain/entity/*`, `backend/src/main/java/com/universal/reconciliation/repository/*` | Define JPA entities for reconciliations, runs, breaks, approvals, exports, and metadata; repositories provide data access. | `ReconciliationRunRepository.findTopByDefinitionOrderByRunDateTimeDesc` fetches latest run for dashboards. |
| Utilities | `backend/src/main/java/com/universal/reconciliation/util/*`, `backend/src/main/java/com/universal/reconciliation/etl/*` | Provide CSV helpers, transformation registries, sample ETL blueprints, and integration with the ingestion SDK. | Admin UI references the ETL registry to preview available pipelines during onboarding. |

## Frontend (Angular 17)

| Component | Location | Responsibilities | Usage Example |
| --- | --- | --- | --- |
| Root Shell | `frontend/src/app/app.component.*` | Hosts global layout, navigation, notification toasts, and router outlet. | Boot via `npm start` to serve `http://localhost:4200` for local development. |
| Routing | `frontend/src/app/app.routes.ts` | Declares standalone route configuration for login, reconciliation workspace, admin console, and deep-linked run views. | `[{ path: 'reconciliations/:id', loadComponent: () => import('./components/run-detail/run-detail.component') }]`. |
| Auth Components | `components/login` | Manage credential capture, token exchange, and redirect to last location using `SessionService`. | When JWT expires, `AuthInterceptor` refreshes and re-routes to `/login`. |
| Analyst Workspace | `components/analyst-workspace` | Aggregate reconciliation list, run summary, result grid, break detail drawer, and checker queue panes into a responsive layout. | Handles `RunDetailDto` responses to synchronize grid filters and break details. |
| Reconciliation List | `components/reconciliation-list` | Presents accessible definitions via cards and emits selection events. | Binds to `ReconciliationStateService.reconciliations$`. |
| Run Detail | `components/run-detail` | Displays run summary cards, analytics charts, and trigger controls; emits filter selections to result grid. | Calls `ApiService.triggerRun(id, payload)` when analysts start manual runs. |
| Result Grid | `components/result-grid` | Renders paginated break records, infinite scroll, column definitions, and bulk selection using CDK virtual scroll. | Consumes `BreakSearchResponseDto` to stream 50-row pages. |
| Break Detail Drawer | `components/break-detail` | Shows contextual metadata, comments, and maker/checker actions for selected break items. | Invokes `ApiService.updateBreakStatus` with approval notes. |
| Checker Queue | `components/checker-queue` | Summarizes approvals awaiting checker review, limited by `app.approvals.queue-size`. | Subscribes to `ReconciliationStateService.approvalQueue$`. |
| System Activity Timeline | `components/system-activity` | Streams and renders `SystemEventDto` entries for transparency and audit. | Uses `ApiService.fetchSystemActivity` to load the feed on workspace initialization. |
| Admin Console | `components/admin/*` | Wizard-driven experience for maintaining definitions, transformations, and access control. | `AdminReconciliationStateService` caches drafts prior to publishing changes. |
| Services | `frontend/src/app/services` | Provide API bindings, application state stores, notification facade, and auth guard. | `ReconciliationStateService` composes multiple HTTP calls into reactive state for the analyst workspace. |
| Models | `frontend/src/app/models` | TypeScript interfaces mirroring backend DTOs for type-safe data binding. | `RunDetail` interface aligns with `RunDetailDto` schema. |

## Shared Libraries

| Library | Location | Description | Usage |
| --- | --- | --- | --- |
| Ingestion SDK | `libraries/ingestion-sdk` | Java toolkit that abstracts CSV ingestion, metadata validation, and CLI helpers. Bundled with scripts to package a runnable ingestion CLI. | Used by `examples/integration-harness` and admin workflows to load sample data sets into staging tables. |
| Frontend Shared Styles | `frontend/src/styles.*` (global) | Provide theming, typography, and spacing tokens aligning with enterprise design system. | Imported into Angular components via SCSS mixins. |
| Example Payloads | `examples/common`, `examples/*/payloads` | Seed files, transformation scripts, and run harnesses for canonical reconciliations. | Execute `examples/integration-harness/scripts/run_multi_example_e2e.sh` to provision and validate all examples. |

## Automation & Quality Gates

| Asset | Location | Purpose | Usage |
| --- | --- | --- | --- |
| Playwright Smoke Suite | `automation/regression` | Installs backend/frontend, launches stack, and runs end-to-end UI journeys. Includes fixtures for sample data. | `cd automation/regression && npm install && npm test`. |
| Maven Tests | `backend` | JUnit + Spring Boot tests covering services, repositories, and controllers. | `cd backend && ./mvnw test`. |
| Frontend Unit Tests | `frontend` | Jasmine/Karma specs for components and state services. | `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`. |
| Seed Scripts | `scripts/local-dev.sh` | Bootstraps dependencies and seeds historical data via CLI. | `./scripts/local-dev.sh bootstrap && ./scripts/local-dev.sh seed`. |

## Infrastructure & Operations

| Component | Location | Description | Usage |
| --- | --- | --- | --- |
| Docker Compose | `infra/docker-compose.yml` | Spins up MariaDB, LDAP, and supporting services for local development. | `docker-compose up` from the `infra` directory. |
| LDAP Fixtures | `infra/ldap` | Contains LDIF seeds representing enterprise groups used by `UserContext`. | Loaded automatically by `docker-compose` to provision sample users. |
| Historical Seed Scripts | `scripts/seed-historical.sh`, `scripts/verify-historical-seed.sh` | Populate and validate large data sets for performance testing. | Run with `--days 3 --runs-per-day 1 --skip-export-check` to mirror CI coverage. |

## Documentation Hub

| Resource | Location | Description |
| --- | --- | --- |
| Wiki Home | `docs/wiki/README.md` | Navigation entry point covering onboarding, development workflow, and feature overviews. |
| Getting Started | `docs/wiki/Getting-Started.md` | Step-by-step guide for setting up the local stack, running tests, and seeding data. |
| Architecture Briefing | `docs/wiki/Architecture-Review-Briefing.md` | Executive-level summary of platform capabilities and roadmap. |
| Database Schema | `docs/wiki/Database-Schema.md` | Entity relationship diagrams and table descriptions for reconciliation metadata, runs, and workflow artifacts. |

## Example End-to-End Flow

1. **Admin Configures** a reconciliation using the Angular admin console, storing metadata via `AdminReconciliationController`.
2. **Ingestion CLI** (powered by the ingestion SDK) loads source files using `SourceIngestionService` and writes staged records.
3. **Analyst Triggers** a run from the UI (`RunDetailComponent`), which calls `POST /api/reconciliations/{id}/run` and displays streaming analytics.
4. **Breaks Appear** in the result grid; analysts drill down via the break detail drawer and add comments.
5. **Checker Reviews** the approval queue, transitions breaks to `APPROVED`, and the system logs activity events.
6. **Exports** are requested by the analyst (`ExportController`), generating Excel files accessible via the UI download center.
7. **Automation Suite** runs nightly Playwright scenarios to verify critical journeys, while historical seed scripts ensure data volume scenarios stay healthy.

This catalog should serve as the definitive reference when navigating or extending the platform.
