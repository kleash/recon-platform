# Reconciliation Examples

The `examples/` directory contains standalone ETL modules that seed the Universal Reconciliation
Platform with opinionated reconciliation scenarios. Each module runs independently of the main
platform code base and is backed by its own end-to-end integration test.

## Available examples

| Example | Description | End-to-end script |
| --- | --- | --- |
| [`cash-vs-gl`](cash-vs-gl/README.md) | Introductory cash versus general ledger reconciliation using CSV inputs. | `examples/cash-vs-gl/scripts/run_e2e.sh` |
| [`securities-position`](securities-position/README.md) | Maker-checker securities position example with tolerance-based comparisons. | `examples/securities-position/scripts/run_e2e.sh` |
| [`custodian-trade`](custodian-trade/README.md) | Complex multi-custodian trade reconciliation with cutoffs and report automation. | `examples/custodian-trade/scripts/run_e2e.sh` |
| [`integration-harness`](integration-harness/README.md) | Boots the platform with multiple examples and validates runtime workflows. | `examples/integration-harness/scripts/run_multi_example_e2e.sh` |

## Usage notes

1. Build the backend jar (`./backend/mvnw -f backend/pom.xml install -DskipTests`) so the example module
   can resolve the `reconciliation-platform` dependency.
2. Execute the example's `scripts/run_e2e.sh` helper to run the integration test for that scenario.
3. Review the module-specific README for a detailed walkthrough of the business context and data files.

Each example uses the shared [`example-support`](common) library which provides helper utilities for
constructing definitions, report templates, and parsing seed data files.
