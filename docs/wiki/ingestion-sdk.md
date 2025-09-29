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
| `StructuredDataBatchBuilder` | Converts JSON arrays, Excel worksheets, delimited text, or in-memory records into CSV batches. |
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

### Structured files and staged records

Use `StructuredDataBatchBuilder` when the ingestion job has access to files or staged records and
needs to convert them into CSV payloads. The builder closes supplied streams and normalizes header
names, making it safe to work with JSON arrays, Excel workbooks, and other delimited feeds. The
snippet below shows how Apache POI can generate an in-memory workbook for conversion:

```java
StructuredDataBatchBuilder structured = new StructuredDataBatchBuilder();

// Build an Excel worksheet with Apache POI
byte[] custodianWorkbook;
try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
    Sheet sheet = workbook.createSheet("Positions");
    Row header = sheet.createRow(0);
    header.createCell(0).setCellValue("positionId");
    header.createCell(1).setCellValue("quantity");
    header.createCell(2).setCellValue("symbol");

    Row first = sheet.createRow(1);
    first.createCell(0).setCellValue("POS-1");
    first.createCell(1).setCellValue(250);
    first.createCell(2).setCellValue("AAPL");

    Row second = sheet.createRow(2);
    second.createCell(0).setCellValue("POS-2");
    second.createCell(1).setCellValue(125.5);
    second.createCell(2).setCellValue("MSFT");

    workbook.write(buffer);
    custodianWorkbook = buffer.toByteArray();
}

IngestionBatch excelBatch = structured.fromExcel(
        "CUSTODIAN_XLSX",
        "custodian-positions",
        new ByteArrayInputStream(custodianWorkbook),
        "Positions",
        true,
        List.of(),
        Map.of());

String pipeDelimited = """
        tradeId|tradeDate|symbol|quantity|price
        TR-1001|2024-05-15|AAPL|150|185.42
        TR-1002|2024-05-15|MSFT|250|312.18
        TR-1003|2024-05-16|GOOGL|75|132.55
        """;
IngestionBatch tradesBatch = structured.fromDelimitedText(
        "TRADES_PIPE",
        "trades-psv",
        new StringReader(pipeDelimited),
        '|',
        true,
        List.of(),
        Map.of("ingestionMode", "append"));

String jsonArray = """
        [{"transactionId":"TXN-1001","amount":1550.45,"currency":"USD"},
         {"transactionId":"TXN-1002","amount":-320.10,"currency":"EUR"}]
        """;
IngestionBatch jsonBatch = structured.fromJsonArray(
        "CASH_JSON",
        "cash-ledger-json",
        new ByteArrayInputStream(jsonArray.getBytes(StandardCharsets.UTF_8)),
        List.of("transactionId", "amount", "currency"),
        Map.of());
```

## Example application

[`examples/ingestion-sdk-example`](../examples/ingestion-sdk-example/README.md) demonstrates how to
combine an H2-backed data source with a mock REST API to populate the `CASH_VS_GL_SIMPLE`
reconciliation. The example ships with a dedicated integration script that provisions the platform,
executes the ingestion jar, and validates reconciliation results.
