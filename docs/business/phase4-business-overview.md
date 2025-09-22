# Phase 4 Business Release Overview

## Executive Summary
Phase 4 packages the Universal Reconciliation Platform with two fully documented example reconciliations and their supporting ETL blueprints. The release demonstrates how configuration-driven onboarding works end-to-end—from ingesting source system files to enabling maker-checker governance—so business stakeholders can visualise how future reconciliations will be introduced without custom code.

## Key Capabilities

### Reference Reconciliation Catalog
* **Cash vs General Ledger (Simple)** shows a lightweight workflow without maker-checker, focused on basic matching keys, currency harmonisation, and missing-item detection.
* **Global Securities Positions (Complex)** exercises multi-key reconciliation, numeric tolerance rules, maker-checker approvals, and richer metadata (custodian, portfolio manager) to model the production target state.
* Both reconciliations ship with configuration metadata, access control scopes, report templates, and pre-populated source data so that demos and training sessions start with meaningful content.

### Blueprint ETL Pipelines
* Startup pipelines convert curated CSV extracts into the platform's canonical schema, documenting each transformation step. They prove how teams can encapsulate extraction, transformation, and load logic per reconciliation without touching the matching engine.
* The simple pipeline highlights straight-through transformations, while the complex pipeline applies tolerance-aware numeric conversions and populates maker/checker scopes.
* Runbook guidance now points operations teams to these blueprints as templates when onboarding future reconciliations.

### Audit & Training Enhancements
* Maker-checker assignments, workflow statuses, and export templates are seeded alongside the data so operations, risk, and audit partners can rehearse full break lifecycles.
* Break data intentionally spans matched, mismatched, and missing scenarios across multiple products/entities, enabling scenario-based training.
* The activity log captures ETL-seeded workflows once runs are triggered, demonstrating the audit trail expected in production.

## Operational Considerations
* Restarting the backend replays the ETL pipelines, guaranteeing a clean baseline before demonstrations or testing cycles.
* LDAP demo groups (`recon-makers`, `recon-checkers`) receive access to both reconciliations; these mappings can be adjusted in `access_control_entries` to rehearse segregation-of-duty scenarios.
* Documentation for business, operations, and developers has been updated to reflect the new datasets and ETL orchestration.

## Release Readiness Checklist
* ✅ ETL pipelines generate both reference reconciliations with auditable configuration and sample data.
* ✅ Integration and unit tests cover the new pipelines, data access changes, and matching behaviour above the 70% Jacoco threshold.
* ✅ Updated documentation guides business users, operations, and developers through the Phase 4 examples.
