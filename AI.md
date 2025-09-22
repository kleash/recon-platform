# Universal Reconciliation Platform Knowledge Base

## Mission and Domain Context
- The platform delivers a configurable reconciliation engine that automates matching, powers break investigation workflows, and enforces strict access controls for financial operations teams.
- Business value is organised into epics that cover metadata-driven reconciliation setup, LDAP-based security, analyst dashboards, maker-checker governance, reporting, and audit-grade monitoring.
- Example reconciliations (Cash vs GL and Global Securities Positions) demonstrate both simple and complex onboarding patterns and ship with ETL blueprints, seeded data, and runbook guidance.

## Repository Topology
- `backend/` — Spring Boot service that exposes reconciliation APIs, executes the matching engine, manages workflow state, emits Excel exports, and bootstraps demo data via startup ETL pipelines.
- `frontend/` — Angular 17 single-page application with standalone components, a shared state service, and API adapters that orchestrate authentication, reconciliation execution, break management, and exports.
- `docs/` — Business release narratives, phase-by-phase technical references, developer workflow guidance, and operations runbooks for the seeded demo environments.

## Key Business Capabilities
1. **Reconciliation configuration** — Define reconciliations by metadata (fields, roles, comparison logic) and trigger matching through manual, scheduled, or event-based execution paths.
2. **Security & access control** — Delegate authentication to enterprise LDAP, enforce scope-based access via LDAP group membership, and capture maker/checker roles without duplicating entitlements.
3. **Analyst experience** — Provide a dynamic reconciliation list, run summary analytics, rich break filtering, side-by-side drill-down views, and Excel exports for offline analysis.
4. **Workflow governance** — Automate break creation, support annotations and attachments, require maker-checker approvals when configured, and track bulk updates.
5. **Reporting & auditing** — Generate configurable Excel reports and surface activity feeds that log reconciliations, status transitions, comments, exports, and other operational events.

## Backend Architecture Overview
- **Entry point & configuration** — `UniversalReconciliationPlatformApplication` boots the Spring context, while configuration classes wire JWT security, LDAP integration, and application properties.
- **REST APIs** — Controllers surface endpoints for authentication, reconciliation discovery and execution, break actions (comments, status updates, bulk operations), exports, and system activity feeds.
- **Service layer** — `ReconciliationService` enforces access, drives the matching engine, persists runs and breaks, calculates analytics, and records audit events. Supporting services handle break lifecycle updates, Excel generation, LDAP-backed session context, and system activity logging.
- **Domain model** — Entities capture reconciliation definitions, fields, report templates, runs, break items, comments, source records, and access control entries. Enumerations express field roles, comparison logic, workflow status, trigger types, and audit event categories.
- **Matching & analytics** — The matching engine executes metadata-driven comparisons across source datasets, produces break candidates, and feeds analytics calculators for dashboard metrics.
- **Security context** — `UserContext` extracts the authenticated principal and LDAP groups so access enforcement and audit trails are group-aware.
- **ETL pipelines** — Startup runners execute modular pipelines (simple cash vs GL, complex securities positions) that register metadata, seed access scopes, load source A/B records from CSV, and provision report templates.
- **Persistence** — Repositories wrap JPA access to reconciliation definitions, runs, breaks, comments, source records, templates, and ACLs, enabling the services to query and persist state consistently.

## Frontend Architecture Overview
- **Composition** — The standalone `AppComponent` orchestrates login, reconciliation selection, run execution, break triage, exports, and state reset; it delegates to dedicated UI components for reconciliation lists, run summaries, break detail, workflow actions, and system activity.
- **State management** — `ReconciliationStateService` centralises BehaviourSubject stores for reconciliations, current selection, run detail, break filters, and activity feed; it calls the API, reacts to updates, and synchronises UI selections.
- **API integration** — `ApiService` wraps REST endpoints for authentication, reconciliation runs, filters, break updates, exports, and activity retrieval, translating UI filters into HTTP parameters.
- **Workflow UX** — Components emit events for triggering runs, applying filters, adding comments, changing statuses, performing bulk updates, and exporting Excel, while templates render responsive layouts and highlight workflow state.
- **Session handling** — `SessionService` (injected into the app component) persists login state so authenticated sessions survive refresh and coordinate with the API headers.

## Data & ETL Patterns
- `SampleEtlRunner` discovers all `EtlPipeline` beans and executes them at startup, ensuring demo reconciliations are seeded automatically.
- Shared helpers in `AbstractSampleEtlPipeline` build reconciliation definitions, register fields with comparison logic and tolerances, create report templates, assign LDAP-based access scopes, and load CSV data into source repositories.
- `SimpleCashGlEtlPipeline` and `SecuritiesPositionEtlPipeline` demonstrate how to onboard reconciliations ranging from straightforward cash matching to tolerance-based securities workflows, including maker-checker enablement and rich report configurations.

## Development Workflow & Tooling
- Install prerequisites (JDK 17+, Maven 3.9+, Node.js 18+) and bootstrap dependencies via Maven and npm.
- Run the backend with `./mvnw spring-boot:run` and the frontend with `npm start`; the dev server proxies API calls to the backend URL configured in the environment file.
- Coding standards emphasise constructor injection, immutable DTOs, Angular standalone components, and comprehensive technical documentation updates for each change.
- Quality gates require `mvn test` with ≥70% coverage, `npm test -- --watch=false --browsers=ChromeHeadless`, and optional linting via `npm run lint` before raising pull requests.

## Operational Notes
- Demo authentication accepts any credentials and populates sessions with LDAP-style group authorities for makers and checkers.
- Restarting the backend reruns ETL pipelines to refresh sample data, report templates, and access control entries.
- Activity feeds, Excel exports, and audit logs provide visibility into reconciliation runs, break actions, and reporting events for support teams.

## Further Reading
- Business overviews in `docs/business/` explain phased feature delivery and stakeholder value.
- Line-by-line technical references in `docs/technical/` describe every backend and frontend source file across all phases.
- `docs/developer-guide.md` and `docs/operations-guide.md` cover day-to-day development, deployment, and troubleshooting practices.
