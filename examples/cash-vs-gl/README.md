# Cash vs. General Ledger Example

This module seeds the Universal Reconciliation Platform with a lightweight cash versus general
ledger reconciliation. It is designed to show how an independent ETL bundle can provision
metadata, ingest flat files, and exercise the matching engine without touching the core backend
code.

## Scenario highlights

- **Business context:** Treasury operations validates that daily cash movements posted in the
general ledger agree with balances delivered by the upstream bank feed.
- **Sources:** `CASH` loads the bank statement while `GL` represents the accounting system. Both
sources are ingested by the CSV adapter that is configured in the pipeline.
- **Matching logic:** The reconciliation key is `transactionId` with exact comparisons on amount,
trade date, and currency plus product/entity classifiers that drive report segmentation.
- **Workflow:** Maker-checker is disabled for brevity, but access control entries still grant maker
and checker visibility across the demo business units.

## Module layout

- `src/main/java/.../CashVsGlExampleApplication.java` – Spring Boot entry point that wires the
example into the platform runtime.
- `src/main/java/.../CashVsGlEtlPipeline.java` – declaratively builds the reconciliation definition,
canonical fields, report template, and sample access control entries.
- `src/main/resources/etl/cash_gl/*.csv` – sample bank and ledger extracts that are ingested during
pipeline execution.
- `src/test/java/.../CashVsGlEndToEndTest.java` – integration test that seeds the definition, kicks
off a reconciliation run, and verifies the generated Excel export.
- `scripts/run_e2e.sh` – helper script that compiles dependencies and launches the end-to-end test
with the backend Maven wrapper.

## Prerequisites

- JDK 17 or newer in your `PATH` (the backend and example modules share the Spring Boot toolchain).
- A POSIX-compatible shell if you plan to use the provided automation script.
- Optional: a spreadsheet viewer if you extend the test to persist the generated Excel workbook.

## Quick start

Run the end-to-end scenario from the module directory:

```bash
cd examples/cash-vs-gl
./scripts/run_e2e.sh
```

The script builds the platform backend without repackaging the Spring Boot fat jar, installs the
shared `example-support` library, and then executes the `CashVsGlEndToEndTest` integration test so
you can observe a full load-match-export cycle.

## Manual execution

If you prefer to run the steps manually, invoke the backend Maven wrapper from the repository root:

```bash
./backend/mvnw -f backend/pom.xml clean install -DskipTests -Dspring-boot.repackage.skip=true
./backend/mvnw -f examples/common/pom.xml install -DskipTests
./backend/mvnw -f examples/cash-vs-gl/pom.xml test
```

- Step 1 installs the `reconciliation-platform` jar without creating an executable archive so that
the example module can resolve it as a standard dependency.
- Step 2 compiles the shared helpers used across all standalone examples.
- Step 3 runs the Spring Boot integration test which uses an in-memory H2 database, ingests the CSV
fixtures, triggers a reconciliation run, and exports a workbook via `ExportService`.

## Inspecting the results

After the Maven build completes, review `examples/cash-vs-gl/target/surefire-reports/
CashVsGlEndToEndTest.txt` for the detailed assertions and log output. The report confirms that both
sources produced a single batch with four records each and that the reconciliation summary contains
matched, mismatched, and missing activity.

To examine the Excel payload, instrument the test (or your own utility class) to persist the byte
array returned by `ExportService.exportToExcel(result)` – for example, by writing it to
`target/cash-vs-gl-report.xlsx` and opening it with your spreadsheet tool of choice.

## Customizing the scenario

- Adjust canonical field definitions, comparison logic, or report columns in
  `CashVsGlEtlPipeline` to mirror your institution's metadata model. The helper methods inherited
  from `AbstractExampleEtlPipeline` keep the mapping code concise while persisting everything through
  the standard repositories.
- Replace the CSV fixtures under `src/main/resources/etl/cash_gl/` with extracts from your own bank
  and ledger systems; the filenames referenced by `ingestCsv` can remain unchanged to avoid code
  modifications.
- Enable maker-checker by changing the `definition(...)` builder call to pass `true` for the workflow
  flag if you want to demonstrate task approvals in addition to automated scheduling.

For a multi-scenario showcase, pair this module with the custodian trade and securities position
examples described in the parent [`examples/README.md`](../README.md).
