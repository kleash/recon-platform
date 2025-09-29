# Ingestion SDK Example

This example demonstrates how to build a minimal ingestion runner using the
[`ingestion-sdk`](../libraries/ingestion-sdk/README.md). It loads `CASH_VS_GL_SIMPLE` data by reading
from an H2 database for the cash ledger and a mock REST API for the general ledger feed.

## Running locally

1. Install the ingestion SDK if you have not already:

   ```bash
   ../../backend/mvnw -f libraries/ingestion-sdk/pom.xml clean install
   ```

2. Package the example application:

   ```bash
   ../../backend/mvnw -f examples/ingestion-sdk-example/pom.xml package
   ```

3. Start the reconciliation platform (for example using the integration harness script).

4. Run the jar and provide platform credentials:

   ```bash
   java -jar examples/ingestion-sdk-example/target/ingestion-sdk-example-0.1.0.jar \
     --reconciliation.ingestion.base-url=http://localhost:8080 \
     --reconciliation.ingestion.username=admin1 \
     --reconciliation.ingestion.password=password
   ```

The runner performs the following steps:

- Queries the in-memory cash ledger table and builds a CSV batch for the `CASH` source.
- Hosts the reference general ledger feed via an in-process mock web server, extracting
  `payload.entries` from the nested JSON response with the REST batch builder before converting it
  into a `GL` CSV batch.
- Uses `IngestionPipeline` to stream both batches through the ingestion API.

### Multi-format ingestion samples

Enable the `format-samples` Spring profile to preview the new `StructuredDataBatchBuilder` helpers
for JSON arrays, Excel worksheets, and delimited text files. The profile logs a CSV preview for each
input format without submitting the batches to the ingestion API:

```bash
java -jar examples/ingestion-sdk-example/target/ingestion-sdk-example-0.1.0.jar \
  --spring.profiles.active=format-samples \
  --reconciliation.ingestion.base-url=http://localhost:8080 \
  --reconciliation.ingestion.username=admin1 \
  --reconciliation.ingestion.password=password
```

The profile synthesizes each dataset at runtime so the repository remains free of sample payloads:

- JSON cash ledger array – emitted from an in-memory string.
- Generated custodian workbook – Excel sheet named `Positions` created with Apache POI.
- Pipe-delimited trade blotter – emitted from an in-memory string.

## Integration validation

`examples/integration-harness/scripts/run_ingestion_library_e2e.sh` provisions the platform,
executes this example jar, triggers a reconciliation run, and validates the summary counts to ensure
end-to-end ingestion continues to work.
