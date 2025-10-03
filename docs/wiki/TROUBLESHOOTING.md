# Troubleshooting Guide

This guide documents common issues encountered while developing or operating the Universal Reconciliation Platform, along with root causes and proven fixes.

## Backend Issues

### Application Fails to Start (Port Already in Use)
- **Symptoms:** `java.net.BindException: Address already in use` when launching the Spring Boot app.
- **Cause:** Another service is bound to port 8080.
- **Resolution:** Stop the conflicting service or override the port using `SERVER_PORT=9090 ./mvnw spring-boot:run`. Update frontend `.env` or proxy config if running locally.

### LDAP Authentication Errors
- **Symptoms:** Login attempts return 401 with `Invalid credentials` despite using sample users.
- **Cause:** LDAP container not running or LDIF fixtures not loaded.
- **Resolution:**
  1. Start the infrastructure stack via `cd infra && docker-compose up`.
  2. Verify `ldap` service logs show LDIF import success.
  3. Re-run backend tests; if using H2 profile, ensure `application-h2.yml` points to the embedded LDIF file.

### Matching Engine Returns Empty Breaks
- **Symptoms:** Runs complete successfully but no breaks appear despite known differences.
- **Cause:** Ingestion metadata mismatch (wrong keys, tolerances) or staging tables not seeded.
- **Resolution:**
  - Confirm ingestion completed by checking activity feed for `INGESTION_COMPLETED` events.
  - Review reconciliation definition in admin console; ensure match keys align with source data columns.
  - Re-run ingestion CLI (`examples/integration-harness/scripts/run_multi_example_e2e.sh` or targeted scripts) to refresh staging data.

### Export Job Stuck in `PENDING`
- **Symptoms:** UI download center shows pending export indefinitely.
- **Cause:** Background job executor missing or errors in template rendering.
- **Resolution:**
  - Check backend logs for stack traces from `ExportService`.
  - Ensure `app.export.template-dir` property points to valid templates under `src/main/resources`.
  - Re-run export after fixing template issues; stuck jobs can be cleared by deleting rows from `export_job` table in non-prod environments.

## Frontend Issues

### HTTP 401 After Login
- **Symptoms:** UI redirects to `/login` immediately after authentication.
- **Cause:** `AuthInterceptor` failed to attach JWT because `SessionService` storage is empty or browser blocked cookies/localStorage.
- **Resolution:** Clear browser storage, log in again, ensure backend `/api/auth/login` returns `token` field. In dev mode, verify proxy configuration forwards `/api` to correct backend port.

### Infinite Spinner on Result Grid
- **Symptoms:** Grid displays loading indicator without data.
- **Cause:** Break search request failed; often due to invalid filters or backend error.
- **Resolution:**
  - Open browser dev tools, inspect network call to `/api/reconciliations/{id}/results`.
  - If 400, reset filters in UI; if 500, review backend logs for query errors.
  - Ensure metadata definitions include column configurations so the grid can render headers.

### Angular Build Failures
- **Symptoms:** `ng build` fails with missing module or type errors.
- **Cause:** TypeScript interfaces not updated after backend DTO change.
- **Resolution:** Update models under `frontend/src/app/models`, regenerate API typings if using OpenAPI, and run `npm test` to validate.

## Ingestion & Examples

### Ingestion CLI Cannot Connect to Database
- **Symptoms:** CLI errors with `Communications link failure` when seeding examples.
- **Cause:** Database container not running or incorrect JDBC URL.
- **Resolution:** Ensure Docker Compose stack is up, confirm JDBC settings in `examples/integration-harness/application.properties`, and re-run the CLI.

### OpenAI Adapter Timeouts
- **Symptoms:** `OpenAiDocumentIngestionAdapter` throws timeout exceptions during ingestion.
- **Cause:** Missing or invalid API credentials, or rate limiting.
- **Resolution:** Provide valid API keys via environment variables, configure retry/backoff in adapter properties, or switch to offline CSV ingestion during local development.

## Automation & Testing

### Playwright Suite Fails During Setup
- **Symptoms:** `npm test` in `automation/regression` fails before tests execute.
- **Cause:** Dependencies missing or backend/frontend builds failing in prep script.
- **Resolution:**
  - Run `npm install` inside `automation/regression` to install Playwright and browsers.
  - Inspect `automation/regression/scripts/prepare.mjs` logs for backend/frontend build errors and fix underlying issues.
  - Clean previous builds via `rm -rf automation/regression/tmp` if present.

### Historical Seed Verification Fails
- **Symptoms:** `scripts/verify-historical-seed.sh` exits non-zero complaining about missing runs.
- **Cause:** Seed script not executed or environment variables set incorrectly.
- **Resolution:**
  - Run `./scripts/seed-historical.sh --days 3 --runs-per-day 1 --skip-export-check`.
  - Ensure the backend is running during verification so APIs can be queried.
  - Check database for expected runs; adjust configuration to match local constraints.

## Infrastructure

### Docker Compose Containers Unhealthy
- **Symptoms:** `docker ps` shows `unhealthy` for `db` or `ldap` services.
- **Cause:** Resource constraints or incorrect environment variables.
- **Resolution:**
  - Increase Docker memory allocation if running out of resources.
  - Remove existing volumes (`docker-compose down -v`) and restart to regenerate databases.
  - Confirm `.env` files in `infra/` are aligned with expected credentials.

### Timezone or Locale Mismatches
- **Symptoms:** Run timestamps differ between UI and database.
- **Cause:** Backend runs in UTC while frontend expects local timezone.
- **Resolution:** Ensure frontend converts timestamps using the `runDateTimeZone` provided in `BreakSearchResultRowDto`. Adjust browser locale settings as needed for testing.

Use this guide as the first line of defense when diagnosing issues. If a problem persists, capture relevant logs, command outputs, and environment details before escalating to the platform maintainers.
