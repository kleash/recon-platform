# Securities Position Reconciliation Example

This example represents a global securities position reconciliation that exercises the platform's
maker-checker workflow, tolerance-based comparisons, and Excel export capabilities.

## Scenario

* **Business context:** Global markets operations must reconcile custodian positions with an internal
  portfolio accounting system.
* **Sources:**
  * Source A is fed by global custodians.
  * Source B is sourced from the internal portfolio accounting platform.
* **Matching logic:** Composite key on account and ISIN with tolerance-based comparisons for quantity
  and market value. Valuation currency and date must also align.
* **Workflow:** Maker-checker is enabled and access entries are provisioned for both maker and checker
  groups covering the securities equities desk.

## Running the end-to-end test

Run the provided helper script from the module directory:

```bash
./scripts/run_e2e.sh
```

The script installs the backend jar (skipping backend tests for speed) and executes the
`SecuritiesPositionEndToEndTest` integration test. The test validates data seeding, tolerance-aware
matching, and generation of an export workbook.

## Data files

Sample CSV files are stored in `src/main/resources/etl/securities`. Replace them with custodian and
book-of-record extracts when adapting this module to a production integration.
