# Admin Configurator Example

This example demonstrates how platform administrators can author a reconciliation via the Admin
Reconciliation Configurator APIs and immediately ingest sample data without redeploying the
platform. It provisions metadata for a "Custody vs General Ledger" reconciliation, exports the
schema contract, and uploads a CSV batch for the anchor source.

## Prerequisites

- The backend application is running locally and reachable at `http://localhost:8080`.
- The Angular frontend is optional, but running it helps you observe the admin workspace update in
  real time.
- `curl` and `jq` are installed on your PATH (the bootstrap script uses them to call the APIs and
  extract identifiers).

## Quick start

1. From the repository root, ensure the backend is built and running:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
2. In a separate terminal, execute the bootstrap script. It logs in with the embedded admin demo
   user (`admin1`/`password`), posts the reconciliation payload, downloads the published schema, and
   submits a sample CSV batch.
   ```bash
   cd examples/admin-configurator
   ./scripts/bootstrap.sh
   ```
3. Inspect the generated artifacts:
   - `artifacts/reconciliation-response.json` captures the full create response.
   - `artifacts/schema-<id>.json` stores the exported canonical schema for ETL teams.
   - `artifacts/ingestion-response.json` contains the batch acknowledgment returned by the API.

## Payloads

- `payloads/reconciliation.json` mirrors the Admin workspace wizard. It declares two sources, three
  canonical fields (including numeric tolerance and classifier roles), a report template, and LDAP
  access control entries.
- `payloads/ingestion-metadata.json` provides the multipart metadata for the ingestion request. The
  sample uses the CSV adapter and labels the batch "Custody batch".
- `data/custody_feed.csv` is a minimal CSV file aligned with the canonical mappings so the ingestion
  request succeeds out of the box.

Feel free to tweak these payloads—add additional canonical fields, change adapter options, or
experiment with other adapters—to explore how the admin configurator responds.
