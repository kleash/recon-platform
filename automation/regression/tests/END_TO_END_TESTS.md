# End-to-End Regression Coverage

The `end-to-end.spec.ts` Playwright suite automates the full reconciliation lifecycle across the
administrator and operations workspaces. The flow intentionally mirrors the manual run books used by
our operations teams and captures screenshots at each major milestone.

## 1. Author reconciliations via the admin dashboard
- Logs in as an administrator and navigates to the "New reconciliation" wizard.
- Defines canonical metadata, sources, and matching rules for a cash vs. general-ledger pair.
- Publishes the definition and enables maker/checker approvals.
- Duplicates the definition to exercise the catalog cloning path and confirm that templates can be reused.

## 2. Ingest source data
- Opens the ingestion workspace as the `ops1` operator.
- Uploads cash and general-ledger CSV batches that mirror the fixtures stored under `tests/fixtures/`.
- Validates that the uploads reach the "Processed" state and are ready for reconciliation.

## 3. Review ingestion data in the UI
- Drills into the uploaded batches to verify record counts, totals, and sampled transactions.
- Captures screenshots of the ingestion dashboards for audit evidence.

## 4. Execute reconciliation and review results
- Kicks off a reconciliation run that consumes the uploaded batches.
- Waits for the run to complete, reviewing matched, unmatched, and exception counts.
- Opens the generated break report, downloading the export artifact to confirm the reporting pipeline.

## 5. Maker/checker workflow
- Submits the reconciliation run for approval as the maker (`ops1`).
- Switches identities to the checker (`ops2`) via token re-issuance, mirroring the dual-control process.
- Approves the run and verifies that the status transitions to "Approved" with an audit trail entry.

The regression produces a markdown/JSON evidence bundle under `automation/regression/reports/latest/`
plus a Playwright HTML report. Review these artifacts after each run to confirm healthy coverage.
