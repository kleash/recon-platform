# Scripts

The `scripts/` directory provides reproducible helpers for local development, data seeding, and automated validation.

## Quick reference

| Script | Description |
| --- | --- |
| `local-dev.sh` | Entrypoint for developer workflows (bootstrap dependencies, start/stop infra, seed small demo data). |
| `seed-historical.sh` | Generates five high-volume reconciliation scenarios with 30 days of activity, multiple daily runs, maker/checker approvals and completed exports. |
| `verify-historical-seed.sh` | Smoke test that the historical seed produced the expected reconciliations, run coverage and workflow outcomes. |

All scripts respect the `BASE_URL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `MAKER_USERNAME`, and `MAKER_PASSWORD` environment variables for convenience.

## Historical seed

`seed-historical.sh` provisions the following out-of-the-box scenarios:

- Treasury cash versus general ledger (`CASH_GL_HISTORY`)
- FX confirmations (`FX_SETTLEMENT_HISTORY`)
- Securities positions (`SECURITIES_POSITION_HISTORY`)
- Fee revenue controls (`FEE_REVENUE_HISTORY`)
- Card settlements (`CARD_SETTLEMENT_HISTORY`)

Each scenario receives:

- 30 days of back-dated runs (configurable via `--days`)
- Multiple runs per day (`--runs-per-day`)
- 100–200 records per source and run (tunable via `--min-records`/`--max-records`)
- Maker comments and maker/checker sign-off for data older than one day
- Completed export jobs (CSV by default)

Example usage for the full dataset (in a shell where the platform is already running):

```bash
./scripts/seed-historical.sh --base-url http://localhost:8080
```

For faster execution in CI or local smoke testing you can override the defaults:

```bash
./scripts/seed-historical.sh --days 3 --runs-per-day 1 --min-records 50 --max-records 60 --report-format NONE --ci-mode
```

## Verifying the seed

`verify-historical-seed.sh` validates that the heavy seed is still producing the expected results. It checks:

- All five reconciliations exist
- Run counts satisfy the configured horizon and cadence
- Maker/checker workflow is complete for any run older than 24 hours
- Export jobs have been generated and completed

The verifier accepts the same `--days` and `--runs-per-day` flags used during seeding so that CI and local runs remain in sync. When exports are skipped (`--report-format NONE`), pass `--skip-export-check` to avoid an unnecessary failure.

```bash
./scripts/verify-historical-seed.sh --days 30 --runs-per-day 3 --skip-export-check
```

Run the verifier after any code changes that affect ingestion, reconciliation, workflow, or export behaviour.

## Local development helper

`local-dev.sh` remains the primary convenience wrapper around:

- `bootstrap` – installs Maven and npm dependencies
- `infra start|stop|status|logs` – manages the Docker-based MariaDB and OpenLDAP stack
- `backend` / `frontend` – launches the Spring Boot API or Angular dev server
- `seed` – provisions the lightweight demo scenarios used in quick start guides
- `stop` – stops both app processes and the supporting Docker containers

Refer to [`docs/wiki/Getting-Started.md`](../docs/wiki/Getting-Started.md) for the broader onboarding guide.
