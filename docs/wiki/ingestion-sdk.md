# Ingestion SDK

The ingestion SDK is a Spring Boot library that helps reconciliation teams package lightweight ETL
runners for the Universal Reconciliation Platform. Implementers can query their upstream systems,
transform the results into CSV batches, and stream them to the platform using a single dependency.

## Components

| Component | Description |
| --- | --- |
| `ReconciliationIngestionClient` | Authenticates with the platform, discovers reconciliation IDs, and uploads batches. |
| `IngestionPipeline` | Convenience orchestrator for running scenarios composed of multiple batches. |
| `JdbcCsvBatchBuilder` | Turns SQL query results into CSV payloads. |
| `RestApiCsvBatchBuilder` | Converts JSON arrays returned by REST endpoints into CSV payloads, supporting nested paths and custom extractors. |
| `ClasspathCsvBatchLoader` | Loads static CSV fixtures from the classpath (useful for smoke tests). |

Auto-configuration registers the client and pipeline when the dependency is present. Provide the
credentials and base URL through `reconciliation.ingestion.*` properties.

## Usage pattern

1. **Install the SDK locally** using the Maven wrapper at the repository root:

   ```bash
   ./backend/mvnw -f libraries/ingestion-sdk/pom.xml clean install
   ```

2. **Add the dependency** to the ingestion project and supply configuration:

   ```yaml
   reconciliation:
     ingestion:
       base-url: http://localhost:8080
       username: admin1
       password: password
       connect-timeout: 10s # optional, defaults to 30s
       read-timeout: 30s    # optional, defaults to 60s
       write-timeout: 30s   # optional, defaults to 60s
   ```

3. **Build batches** using the provided builders:

   ```java
   JdbcCsvBatchBuilder jdbc = new JdbcCsvBatchBuilder(jdbcTemplate);
   IngestionBatch cashBatch = jdbc.build(
       "CASH",
       "cash-ledger",
       "SELECT transaction_id AS transactionId, amount, currency, trade_date AS tradeDate FROM cash_ledger",
        Map.of(), // params for the SQL query
        List.of("transactionId", "amount", "currency", "tradeDate"),
        Map.of()); // options forwarded to the ingestion adapter

   RestApiCsvBatchBuilder api = new RestApiCsvBatchBuilder(restTemplate);
   IngestionBatch glBatch = api.get(
       "GL",
       "general-ledger",
       URI.create("https://example.org/api/gl"),
        List.of("transactionId", "amount", "currency", "tradeDate"),
        Map.of(), // options forwarded to the ingestion adapter
        "payload.entries");

   IngestionScenario scenario = IngestionScenario.builder("cash-vs-gl")
       .reconciliationCode("CASH_VS_GL_SIMPLE")
       .addBatch(cashBatch)
       .addBatch(glBatch)
       .build();

   ingestionPipeline.run(scenario);
   ```

4. **Run the jar** from an operations perspective. The SDK handles authentication, multipart upload,
   and reconciliation discovery so implementers only focus on extraction logic.

### Advanced REST extraction

For wrapped or paginated APIs, supply either a JSON Pointer (e.g. `"/payload/entries"`) or a custom
extractor. The dot-separated convenience syntax (e.g. `"payload.entries"`) resolves simple property
paths and does not implement the full JSON Pointer escaping rules. The extractor receives the
response as a Jackson `JsonParser`, enabling fully streaming extraction for large documents:

```java
RestApiCsvBatchBuilder api = new RestApiCsvBatchBuilder(restTemplate);
IngestionBatch glBatch = api.get(
        "GL",
        "general-ledger",
        URI.create("https://example.org/api/gl"),
        List.of("transactionId", "amount"),
        Map.of(),
        (parser, mapper) -> {
            JsonNode root = mapper.readTree(parser);
            List<Map<String, Object>> combined = new ArrayList<>();
            root.path("payload").path("entries")
                    .forEach(node -> combined.add(mapper.convertValue(node, Map.class)));
            root.path("payload").path("adjustments")
                    .forEach(node -> combined.add(mapper.convertValue(node, Map.class)));
            return combined.iterator();
        });
```

## Example application

[`examples/ingestion-sdk-example`](../examples/ingestion-sdk-example/README.md) demonstrates how to
combine an H2-backed data source with a mock REST API to populate the `CASH_VS_GL_SIMPLE`
reconciliation. The example ships with a dedicated integration script that provisions the platform,
executes the ingestion jar, and validates reconciliation results.
