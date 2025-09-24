# Custodian Trade Reconciliation Example

This example demonstrates how to run the Universal Reconciliation Platform in a fully standalone mode
while orchestrating a complex multi-source trade reconciliation. It showcases:

- Loading three custodian CSV feeds plus an internal trading platform extract.
- Automatically triggering reconciliations twice per day when inputs are available.
- Enforcing a 6pm cutoff when a custodian feed is late.
- Scheduling reports at 3pm, 9pm, and 2am for the previous day's activity.

## Scenario overview

* **Custodians:** Alpha Bank, Beta Trust, and Omega Clear deliver files (20+ columns) via SFTP.
* **Trading platform:** Provides a consolidated Excel extract containing source attribution.
* **Reconciliation key:** Composite of `trade_id` and `source`.
* **Cutoffs:** 11:00 ET (morning) and 18:00 ET (evening). Runs are auto-triggered when all files are
  present; otherwise the scheduler forces execution at the cutoff time.
* **Reporting cadence:**
  * 15:00 ET – midday report using the morning run.
  * 21:00 ET – evening report using the 18:00 run.
  * 02:00 ET next day – overnight report for compliance operations.

The ETL pipeline stores the custodian name alongside each record and mirrors that source into the
platform file. Variances are intentionally injected so the example produces mismatches and missing
records.

## Running the end-to-end test

From the module directory run:

```bash
./scripts/run_e2e.sh
```

The helper script installs the backend jar (skipping backend tests for speed) and then executes the
`CustodianTradeEndToEndTest`. The integration test bootstraps the platform, runs both cutoffs, verifies
that the evening run was forced at 18:00 due to a missing custodian file, and asserts that the three
scheduled reports were generated with non-empty Excel workbooks.

## Data files

All sample inputs are stored as comma-delimited files under `src/main/resources/etl/custodian`:

- `custodian_*_morning.csv` and `custodian_*_evening.csv` represent the individual custodian deliveries.
- `trading_platform_morning.csv` and `trading_platform_evening.csv` contain the internal platform
  perspective with embedded source names.

The ETL pipeline ingests these resources through the platform's CSV adapter while the canonical field
configuration maps them into the dynamic metadata model. Replace the CSV files with environment-specific
extracts to adapt the scenario for real testing or demos.
