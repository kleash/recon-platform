# Universal Reconciliation Platform

## Introduction
The Universal Reconciliation Platform delivers a configurable matching engine, workflow automation, and analytics toolkit that financial operations teams can tailor to any reconciliation use case. Metadata-driven definitions, prebuilt ETL blueprints, and a modular UI make it simple to onboard new reconciliations while preserving auditability and governance.

## High-Level Features
- **Configurable reconciliation engine** – Define data sources, matching tolerances, and workflow rules entirely through metadata so new reconciliations can be deployed without code changes.
- **Maker-checker governance** – Automate break creation, comments, approvals, and audit logs to enforce separation of duties and regulatory compliance.
- **Dynamic analyst workspace** – Provide dashboards, drill-down views, filters, and Excel exports that help analysts investigate breaks and share findings quickly.
- **Extensible integrations** – Trigger reconciliations via manual actions, schedules, API calls, or messaging events, and stream activity to downstream monitoring tools.
- **End-to-end observability** – Capture activity feeds, system metrics, and reconciled datasets with traceable lineage across the lifecycle of each reconciliation run.

## Project Snapshot
| Layer | Technology | Purpose |
| --- | --- | --- |
| Backend | Spring Boot (Java 17) | Hosts reconciliation services, workflow APIs, and ETL runners. |
| Frontend | Angular 17 | Delivers the analyst-facing single-page application. |
| Database | MariaDB / H2 (dev) | Stores reconciliation metadata, runs, breaks, and audit artifacts. |
| Identity | LDAP + JWT session tokens | Provides enterprise-aligned authentication and granular access control. |

## Documentation Hub
The complete project wiki, including feature deep dives, developer workflows, and reconciliation onboarding playbooks, lives under [`docs/wiki`](docs/wiki/README.md). Start with the wiki home for navigation to specialized guides and diagrams.

For the original project charter and phased rollout notes, refer to [`docs/Bootstrap.md`](docs/Bootstrap.md).

## Standalone Examples
Ready-to-run reconciliation samples now live in the [`examples/`](examples/README.md) directory. Each
example seeds the platform with its own ETL pipeline, data files, and an end-to-end integration test
that can be executed via the module-specific `scripts/run_e2e.sh` helper. Examples cover scenarios
ranging from introductory cash versus GL workflows to complex multi-custodian trade reconciliations
with automated scheduling.
