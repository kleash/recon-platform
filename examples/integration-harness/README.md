# Multi-example Integration Harness

The integration harness boots the Universal Reconciliation Platform with multiple standalone
examples enabled at once. It provides a thin REST surface to validate scheduler automation, manual
run triggers, and report generation without embedding additional fixtures into the core backend.

## Scenario highlights

- Loads both the cash vs. GL and custodian trade examples into a single Spring Boot process.
- Exposes diagnostic endpoints (via the `example-harness` Spring profile) so scripts can inspect
  seeded reconciliations, trigger runs, and fetch scheduler history.
- Automates API authentication and verification to mirror real production flows.

## Module layout

- `src/main/java/.../IntegrationHarnessApplication.java` – Spring Boot entry point that enables
  the example profile and wires controller endpoints.
- `src/main/java/.../HarnessClient.java` and supporting classes – issue authenticated REST calls to
  validate seeded reconciliations and trigger runs.
- `scripts/run_multi_example_e2e.sh` – orchestrates the end-to-end validation, including packaging the
  harness, running the verification workflow, and tearing down the process.
- `src/test/java/...` – lightweight sanity tests for the harness-specific components.

## Prerequisites

- Java 17+.
- POSIX shell and the ability to execute background processes (required by the helper script).

## Quick start

```bash
examples/integration-harness/scripts/run_multi_example_e2e.sh
```

The script performs the following steps:

1. Installs the backend jar and compiles each standalone example so their ETL pipelines can be reused
   inside the harness application.
2. Packages the harness with the `example-harness` profile enabled and launches it in the background.
3. Authenticates with the running process, checks that the cash vs. GL and custodian trade definitions
   were provisioned, and triggers automated runs to verify matched/mismatched/missing metrics.
4. Validates that the custodian trade scheduler executed the morning auto-trigger, forced the evening
   cutoff, and generated all three scheduled reports.
5. Issues manual run requests to confirm the public API can override scheduling gaps.
6. Shuts down the harness gracefully and reports the location of the captured logs.

## Inspecting the results

While the script prints a concise summary to stdout, detailed logs are written to the temporary
working directory reported at the end of the run. Review those files to inspect API payloads, run
summaries, or troubleshooting information.

## Customizing the harness

- Update the controllers under `src/main/java/.../web/` to expose additional diagnostics or custom
  health checks.
- Modify the verification script to call other platform APIs (for example, to approve maker-checker
  tasks or download generated Excel exports).
- Add your own example module dependency to the harness `pom.xml` and extend the verification steps to
  cover new scenarios as they are introduced.

The harness pairs well with the individual module scripts when you need to present an integrated demo
or perform regression checks across multiple reconciliation patterns at once.
