# Reconciliation Examples

The `examples/` directory contains standalone Spring Boot applications that seed the Universal
Reconciliation Platform with opinionated reconciliation scenarios. Each module provisions its own
metadata, ingests sample flat files, and runs an end-to-end integration test so you can observe the
platform in action without deploying the full stack.

## Prerequisites

- Java 17 or newer.
- A POSIX-compatible shell if you plan to use the provided helper scripts.
- Sufficient disk space to build the backend jar once; all examples reuse the same artifact.

## How to run an example

1. From the repository root install the backend jar so the examples can resolve the
   `reconciliation-platform` dependency:
   ```bash
   ./backend/mvnw -f backend/pom.xml clean install -DskipTests -Dspring-boot.repackage.skip=true
   ```
2. Install the shared `example-support` helper library:
   ```bash
   ./backend/mvnw -f examples/common/pom.xml install -DskipTests
   ```
3. Change into the desired module and run its `scripts/run_e2e.sh` (or follow the manual commands
   documented in the module README) to execute the scenario-specific integration test.

Each README linked below expands on the business context, data files, and customization guidance for
that module.

## Available examples

| Module | Focus | Helper script |
| --- | --- | --- |
| [`cash-vs-gl`](cash-vs-gl/README.md) | Introductory cash versus general ledger reconciliation using CSV inputs. | `examples/cash-vs-gl/scripts/run_e2e.sh` |
| [`securities-position`](securities-position/README.md) | Maker-checker securities position example with tolerance-based comparisons. | `examples/securities-position/scripts/run_e2e.sh` |
| [`custodian-trade`](custodian-trade/README.md) | Complex multi-custodian trade reconciliation with cutoffs and report automation. | `examples/custodian-trade/scripts/run_e2e.sh` |
| [`admin-configurator`](admin-configurator/README.md) | Metadata-driven admin workflow that provisions a reconciliation via REST and ingests a sample batch. | `examples/admin-configurator/scripts/bootstrap.sh` |
| [`integration-harness`](integration-harness/README.md) | Boots multiple examples together and validates runtime workflows via REST. | `examples/integration-harness/scripts/run_multi_example_e2e.sh` |

All examples share the [`example-support`](common) library, which wraps repository access and CSV
ingestion helpers to keep the pipeline code succinct.
