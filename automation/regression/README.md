# Regression smoke automation

This package boots the Spring Boot backend and Angular frontend and exercises the
UI using Playwright. It serves as a reproducible end-to-end smoke regression for
local development or CI pipelines.

## Prerequisites

- Node.js 18+
- npm 9+
- Java 17 (the same runtime used by the backend `mvnw` wrapper)

## Running the checks

```bash
cd automation/regression
npm install
npm test
```

The `pretest` hook builds the backend jar, installs/builds the Angular
application, downloads the required Playwright browser binaries and then launches
both services. The Playwright suite logs into the application as the seeded `ops1`
operations user and the `admin1` administrator, exercising both the analyst
workspace and the administration catalog/wizard flows.

Use `npm run test:headed` to execute the same flow in a headed browser while
surfacing Playwright's debug console.

## Coverage

The Playwright suite mirrors the full reconciliation lifecycle so release teams can rely on a single automation pack for smoke and regression sign-off.

1. **Author reconciliations via the admin dashboard** – Logs in as an administrator, steps through the "New reconciliation" wizard, defines metadata, enables maker/checker controls, and clones the definition to verify template reuse.
2. **Ingest source data** – Switches to the operations workspace, uploads cash and general-ledger CSV batches from `tests/fixtures/`, and waits for processing to complete.
3. **Review ingestion data** – Confirms record counts and totals within the ingestion dashboards, capturing screenshots for evidence.
4. **Execute reconciliation and review results** – Triggers a reconciliation run, validates analytics, and downloads the break report export.
5. **Maker/checker workflow** – Submits the run for approval as the maker, re-authenticates as the checker, approves or rejects, and validates the workflow audit trail.

## Generated reports

Each regression run generates two artifacts rooted in this package:

- `playwright-report/` contains Playwright's interactive HTML report.
- `reports/latest/` captures a Markdown summary (`report.md`), structured JSON
  coverage data (`coverage.json`) and screenshots for every verified screen.

The `reports/` directory is ignored by git so each run generates a fresh set of
artifacts without polluting the repository history.

Open `reports/latest/report.md` to quickly confirm which application surfaces
the automation touched and review the captured evidence. Include both the HTML
report and Markdown bundle in release notes or go/no-go communications.

## Extending the suite

- Add new scenarios to `tests/end-to-end.spec.ts` to keep parity with newly delivered features.
- Use the fixtures under `tests/fixtures/` as the canonical data set; refresh them whenever reconciliation metadata evolves.
- Update this guide and the [Documentation Navigator](../../docs/wiki/Documentation-Navigator.md) whenever you add new scripts or reporting steps.
