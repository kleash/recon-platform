# Securities Position Reconciliation Example

This module delivers a global securities position reconciliation that exercises maker-checker
workflow, tolerance-based comparisons, and dynamic reporting metadata. It showcases how the
Universal Reconciliation Platform can be bootstrapped with complex field mappings using only a
self-contained Spring Boot application.

## Scenario highlights

- **Business context:** Global markets operations reconcile custodian positions against the firm’s
  portfolio accounting system to validate holdings, valuations, and workflow status.
- **Sources:** `CUSTODIAN` captures the aggregated statements delivered by global custodians, while
  `PORTFOLIO` represents the internal book of record.
- **Matching logic:** Positions are keyed by account and ISIN with numeric tolerances applied to
  quantity and market value fields. Currency and valuation date comparisons enforce straight-through
  accuracy, and additional metadata captures custodian and portfolio manager context.
- **Workflow:** Maker-checker approvals are enabled; access control entries grant makers and checkers
  visibility across the securities desk.

## Module layout

- `src/main/java/.../SecuritiesPositionExampleApplication.java` – Spring Boot entry point that wires
  the ETL pipeline into the platform runtime.
- `src/main/java/.../SecuritiesPositionEtlPipeline.java` – defines canonical fields, tolerance
  thresholds, report columns, and sample access control entries before ingesting the CSV fixtures.
- `src/main/resources/etl/securities/*.csv` – custodian and portfolio source files ingested during the
  pipeline run.
- `src/test/java/.../SecuritiesPositionEndToEndTest.java` – integration test that validates batch
  ingestion, tolerance-aware matching, and Excel export generation.
- `scripts/run_e2e.sh` – wrapper that builds prerequisites and executes the integration test with the
  backend Maven wrapper.

## Prerequisites

- Java 17 or newer.
- POSIX shell if you plan to rely on the helper script.

## Quick start

```bash
cd examples/securities-position
./scripts/run_e2e.sh
```

The script compiles the backend, installs the shared example utilities, and runs
`SecuritiesPositionEndToEndTest`, which in turn seeds the maker-checker definition and triggers a
scheduled-style reconciliation run.

## Manual execution

From the repository root you can execute the same flow manually:

```bash
./backend/mvnw -f backend/pom.xml clean install -DskipTests -Dspring-boot.repackage.skip=true
./backend/mvnw -f examples/common/pom.xml install -DskipTests
./backend/mvnw -f examples/securities-position/pom.xml test
```

The final command spins up the Spring Boot application, ingests both CSV sources, invokes the matching
engine with maker-checker credentials, and produces an Excel workbook using the configured report
template.

## Inspecting the results

Consult `target/surefire-reports/SecuritiesPositionEndToEndTest.txt` for a breakdown of the batches,
run summary metrics, and assertions that confirm matched, mismatched, and missing activity. The log
output also confirms that maker-checker is enabled for the provisioned definition.

To review the exported workbook, modify the test to persist the byte array returned by
`ExportService.exportToExcel(run)` to disk. This is useful when demonstrating the layout of the
report template configured in the pipeline.

## Customizing the scenario

- Update `configureFields` inside `SecuritiesPositionEtlPipeline` to tweak tolerance thresholds or add
  supplementary data points such as regional classifications.
- Replace the CSV fixtures under `src/main/resources/etl/securities/` with custodian and portfolio
  extracts from your environment to pilot the module with real data.
- Extend the access control entries or report template configuration to align with the desks and KPIs
  used by your operations teams.

Combine this module with the cash vs. GL and custodian trade examples to cover a broad spectrum of
asset-servicing and cash management reconciliations.
