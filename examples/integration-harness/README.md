# Multi-example Integration Harness

The integration harness now boots the Universal Reconciliation Platform without any embedded example
pipelines and relies entirely on the Admin Configurator APIs to author reference reconciliations. A
companion ingestion CLI streams CSV fixtures for each scenario so the end-to-end flow mirrors what
an operations team would execute in production.

## Scenario highlights

- Applies admin payloads for the cash vs GL, custodian trade, and securities position examples at
  runtime – no example code is packaged in the Spring Boot launcher.
- Uses the bundled ingestion CLI to submit representative batches for every source (including
  multiple custodian cutoffs) so the reconciliations start with realistic break profiles.
- Triggers manual runs through the public API and validates that each scenario produces the expected
  mixture of matched, mismatched, and missing activity.
- Exercises the platform exactly the way an administrator would: log in, configure metadata, ingest
  data, and inspect run summaries.

## Module layout

- `src/main/java/.../MultiExampleHarnessApplication.java` – plain Spring Boot entry point that simply
  starts the platform with the `example-harness` profile.
- `src/main/java/.../ingestion/` – lightweight OkHttp/Jackson based CLI for pushing batches via the
  admin ingestion endpoints.
- `payloads/*.json` – Admin Configurator requests for each reconciliation definition.
- `src/main/resources/data/**` – CSV fixtures referenced by the ingestion CLI.
- `scripts/run_multi_example_e2e.sh` – orchestration script that builds the binaries, launches the
  platform, applies admin payloads, runs the ingestion CLI, validates run summaries, and tears the
  process down.

## Prerequisites

- Java 17+
- `curl`, `jq`, and `lsof` on your `PATH`
- POSIX-compatible shell

## Quick start

```bash
examples/integration-harness/scripts/run_multi_example_e2e.sh
```

The script performs the following steps:

1. Builds the backend, the harness launcher, and the fat-jar ingestion CLI.
2. Starts the platform with the `example-harness` profile on the configured port (defaults to 8080).
3. Uses `payloads/*.json` to upsert the three sample reconciliations via `/api/admin/reconciliations`.
4. Executes `target/integration-ingestion-cli.jar --scenario all` to send every sample batch.
5. Logs in as the demo operations user and triggers a manual run for each reconciliation, verifying
   the summary counts.
6. Stops the Spring Boot process and deletes temporary log directories.

A successful run prints condensed summaries similar to:

```
[14:07:35] Run summary for CASH_VS_GL_SIMPLE: {"matched":2,"mismatched":1,"missing":2,...}
[14:07:35] Run summary for CUSTODIAN_TRADE_COMPLEX: {"missing":4,...}
[14:07:35] Run summary for SEC_POSITION_COMPLEX: {"matched":2,"mismatched":1,"missing":2,...}
```

If any API call fails, the script surfaces the HTTP payload and exits non-zero so CI can catch
regressions.

## Ingestion CLI usage

The CLI jar can be invoked independently after the platform is running:

```bash
java -jar examples/integration-harness/target/integration-ingestion-cli.jar \
  --base-url http://localhost:8080 \
  --username admin1 \
  --password password \
  --scenario custodian-trade
```

Valid scenarios are `cash-vs-gl`, `custodian-trade`, `securities-position`, or `all` (default).

## Customising the harness

- Add new payloads under `payloads/` and reference additional CSV fixtures from
  `src/main/resources/data/` to extend the scenario coverage.
- Update `ScenarioRegistry` in the ingestion CLI to describe new sources/batches or change ingestion
  options.
- Adjust `validate_summary` inside the orchestration script if you want to assert different run
  characteristics.
- Run the CLI with `--help` to discover optional flags when pointing at a non-default environment.

Because the harness now operates entirely through the public APIs, it provides a realistic validation
loop for admin-driven configuration changes without re-compiling the backend.
