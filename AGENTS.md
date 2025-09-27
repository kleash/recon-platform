# AI Contributor Guide

Welcome, automated contributor! Follow these ground rules to collaborate effectively on the Universal Reconciliation Platform repository.

## Repository Orientation
- **Backend:** `backend/` holds the Spring Boot services, matching engine, workflow logic, and ETL pipelines.
- **Frontend:** `frontend/` contains the Angular 17 single-page application with standalone components and a shared state service.
- **Documentation:** Centralized under `docs/wiki`. Start with `docs/wiki/README.md` for navigation, then update specialized guides as needed.
- **Data Model:** The database schema is critical. Refer to `docs/wiki/Architecture.md` for diagrams.
- **Historical context:** `docs/Bootstrap.md` stores the original charter and phased rollout plan.

## Contribution Checklist
1. **Plan before you code.** Review related wiki pages to ensure solutions align with platform patterns.
2. **Keep documentation in sync.** Any code change that affects features, workflows, or onboarding must update the relevant page in `docs/wiki`.
3. **Prefer incremental commits.** Group logically-related changes and provide descriptive commit messages.
4. **Respect configuration over code.** Extend metadata-driven constructs instead of hardcoding reconciliation behavior.
5. **Keep test cases up to date.** Any code change that affects features, workflows, or onboarding must update the relevant test cases in backend, frontend, automation smoke, example integration harness, and bootstrap scripts.

## Quality Gates
- **Backend tests:** `cd backend && ./mvnw test`
- **Frontend tests:** `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
- **Frontend lint (optional):** `cd frontend && npm run lint`
- **Automation smoke (Playwright):** `cd automation/regression && npm install && npm test`
- **Examples integration harness:** `examples/integration-harness/scripts/run_multi_example_e2e.sh`
- **Bootstrap scripts:** `./scripts/local-dev.sh bootstrap` (and `seed` once the stack is running) to confirm local helpers stay healthy.
- **Historical volume seed:** `./scripts/seed-historical.sh` followed by `./scripts/verify-historical-seed.sh` (use the lighter `--days 3 --runs-per-day 1 --report-format NONE --ci-mode --skip-export-check` combo to mirror CI) to ensure large-scale data workflows continue to pass.
- Include command outputs in pull request descriptions when applicable.

## Automation & E2E Toolkit
- **Playwright harness:** `automation/regression/` bundles the cross-stack smoke suite. The `npm test` command triggers `scripts/prepare.mjs` to build the backend, install/frontend, download browsers, and launch both services before running Playwright.
- **Headed/debug runs:** Use `npm run test:headed` in the same directory to observe interactions and surface Playwright's debug console when triaging failures.
- **Evidence capture:** Review `automation/regression/playwright-report/` for the interactive HTML report and `automation/regression/reports/latest/` for Markdown/JSON artifacts when attaching run evidence.
- **Extending coverage:** Add scenarios under `automation/regression/tests/` and refresh fixtures in `automation/regression/tests/fixtures/` to keep parity with new reconciliation features.

## Examples Validation
- **Multi-scenario smoke:** Run `examples/integration-harness/scripts/run_multi_example_e2e.sh` to provision all example reconciliations, drive ingestion via the bundled CLI, and assert reconciliation summaries.
- **Port considerations:** Override `APP_PORT` when your local stack already occupies `8080` (e.g., `APP_PORT=9090 examples/integration-harness/scripts/run_multi_example_e2e.sh`).
- **Troubleshooting artifacts:** Inspect the temporary log path printed by the harness when diagnosing build or runtime issues; the script cleans it up automatically after a successful run.

## Bootstrap & Seed Scripts
- **Dependency bootstrap:** Use `./scripts/local-dev.sh bootstrap` to exercise the dependency setup flow (backend Maven cache + frontend npm install). Failures signal drift in required toolchain versions.
- **Seed data freshness:** After the platform is running (local stack or integration harness), run `./scripts/local-dev.sh seed` to reapply reconciliation payloads and ingest fixtures. Keep `examples/integration-harness/payloads/*.json` and fixture CSVs current whenever metadata changes so seeded data matches production expectations.
- **CLI health:** The seed command builds the ingestion CLI under `examples/integration-harness/target/`; rerun it when modifying ingestion flows to ensure packaging still succeeds.

## Documentation Standards
- Write in Markdown with clear headings and use Mermaid diagrams for complex flows when possible.
- Link to related wiki sections using relative paths (`[Developer Guide](docs/wiki/developer-guide.md)`).
- Keep README concise and defer deep dives to the wiki.

## Decision Records
If you introduce significant architectural or process changes, capture the rationale in a new page under `docs/wiki/adr-<topic>.md` and link it from the wiki home.

Thank you for helping us build a resilient reconciliation platform!
