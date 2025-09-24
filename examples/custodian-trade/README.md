# Custodian Trade Reconciliation Example

This module exercises a complex multi-source trade reconciliation in a standalone Spring Boot
application. It demonstrates how the Universal Reconciliation Platform can orchestrate multiple
custodian feeds, enforce cutoffs, and deliver scheduled reports without relying on external runtime
infrastructure.

## Scenario highlights

- **Custodians:** Alpha Bank, Beta Trust, and Omega Clear deliver morning and evening CSV files that
  include the originating source for each trade.
- **Internal platform:** A trading platform extract provides the firm's golden source of trades and
  identifies which custodian should have reported each item.
- **Matching logic:** Trades are keyed by `trade_id` and `source` with tolerance-based comparisons on
  quantity and gross amount so minor rounding differences are tolerated.
- **Cutoffs and scheduling:** Morning runs are triggered automatically when all files are present,
  while the evening run is forced at 18:00 if a custodian is late. Report jobs run at 15:00, 21:00,
  and 02:00 to cover operational and compliance needs.
- **Workflow:** Maker-checker is enabled and access roles are provisioned for makers, checkers, and
  operations viewers across the equities desk.

## Module layout

- `src/main/java/.../CustodianTradeExampleApplication.java` – Spring Boot application that boots the
  example pipeline alongside the scenario clock and scheduler components.
- `src/main/java/.../CustodianTradeEtlPipeline.java` – defines canonical fields, report templates,
  access control entries, and registers custodian-specific scheduling rules.
- `src/main/java/.../CustodianTradeScheduler.java` – drives automated ingestion cutoffs and report
  dispatch windows using the simulated clock.
- `src/main/resources/etl/custodian/*.csv` – sample custodian and trading platform files grouped by
  delivery window.
- `src/test/java/.../CustodianTradeEndToEndTest.java` – integration test that verifies batch counts,
  cutoff behaviour, and workbook generation for each scheduled report.
- `scripts/run_e2e.sh` – shell script that compiles prerequisites and executes the integration test
  with the backend Maven wrapper.

## Prerequisites

- JDK 17+ and a POSIX shell for the helper script.
- Network access is not required; all data files and dependencies are bundled locally.

## Quick start

```bash
cd examples/custodian-trade
./scripts/run_e2e.sh
```

The helper script installs the backend jar (skipping backend tests for speed), builds the shared
`example-support` library, and runs the `CustodianTradeEndToEndTest` class so you can observe the
full scheduler-driven scenario.

## Manual execution

To step through the workflow manually from the repository root:

```bash
./backend/mvnw -f backend/pom.xml clean install -DskipTests -Dspring-boot.repackage.skip=true
./backend/mvnw -f examples/common/pom.xml install -DskipTests
./backend/mvnw -f examples/custodian-trade/pom.xml test
```

The third command launches the Spring Boot context, ingests all CSV fixtures, evaluates morning and
evening cutoffs, advances the scenario clock to the overnight window, and verifies that each report
execution produced a non-empty Excel workbook.

## Inspecting the results

Review the generated `target/surefire-reports/CustodianTradeEndToEndTest.txt` file for detailed
assertion output. The report enumerates the six custodian batches, two platform batches, and the
scheduled run metadata that confirms which executions were triggered automatically versus forced at
the cutoff.

The scheduler retains an in-memory record of each cutoff and report execution. You can log or expose
these results by extending `CustodianTradeScheduler` if you need additional observability hooks.

## Customizing the scenario

- Update `configureFields` inside `CustodianTradeEtlPipeline` to adjust comparison tolerances, add
  custom classifiers, or map additional CSV columns.
- Modify the CSV fixtures under `src/main/resources/etl/custodian/` to mirror your custodian layouts.
  The filenames align with the ingestion logic used by the scheduler helper methods.
- Extend `CustodianTradeScheduler` to incorporate your firm's cutoff calendar or to push status
  updates into downstream notification systems.
- Swap the simulated clock for a real-time implementation when adapting the module for pilot
  environments.

Pair this module with the cash vs. GL and securities position examples for a comprehensive demo tour
covering payments, trading, and asset-servicing reconciliations.
