# Cash vs. General Ledger Example

This example seeds the Universal Reconciliation Platform with a classic cash versus general ledger
reconciliation. It demonstrates how an independent ETL module can:

- Provision reconciliation definitions, fields, and report templates.
- Load sample source system data.
- Trigger the matching engine and export a report end-to-end.

## Scenario

* **Business context:** Payments operations validates that cash movements booked in the general ledger
  match activity reported by a treasury bank feed.
* **Sources:**
  * Source A represents the treasury feed.
  * Source B represents ledger entries.
* **Matching logic:** The reconciliation key is `transactionId` with exact amount and trade-date
  comparisons, including product segmentation.
* **Workflow:** Maker-checker is disabled to keep the illustration lightweight, but access control is
  still configured for maker and checker groups in both US and EU regions.

## Running the end-to-end test

1. Build the platform backend so that the example module can depend on the jar.
2. Execute the Maven-based end-to-end test, which starts the platform, executes the ETL pipeline,
   triggers a scheduled run, and generates an Excel report.

```bash
./scripts/run_e2e.sh
```

The script uses the backend Maven wrapper and therefore works without requiring a global Maven
installation. It runs the `CashVsGlEndToEndTest` class which asserts that data was loaded, the
matching engine produced breaks, and the generated Excel workbook is non-empty.

## Data files

Sample CSV files live under `src/main/resources/etl/cash_gl`. They can be replaced with
integration-specific extracts when adapting this module for a real environment.
