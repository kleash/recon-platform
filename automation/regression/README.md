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

## Generated reports

Each regression run generates two artifacts rooted in this package:

- `playwright-report/` contains Playwright's interactive HTML report.
- `reports/latest/` captures a Markdown summary (`report.md`), structured JSON
  coverage data (`coverage.json`) and screenshots for every verified screen.

The `reports/` directory is ignored by git so each run generates a fresh set of
artifacts without polluting the repository history.

Open `reports/latest/report.md` to quickly confirm which application surfaces
the automation touched and review the captured evidence.
