# Universal Reconciliation Ingestion SDK

The ingestion SDK is a Spring Boot friendly library that lets reconciliation implementers build
small ETL launchers for pushing source data into the Universal Reconciliation Platform. It provides
high-level helpers for converting database queries or external API responses into CSV batches and
a lightweight HTTP client that streams those batches through the admin ingestion APIs.

## Features

- Auto-configured `ReconciliationIngestionClient` and `IngestionPipeline` beans via Spring Boot
  auto-configuration.
- Fluent builders for authoring ingestion scenarios and batches.
- Utilities for turning JDBC queries, REST API responses, or classpath CSV fixtures into batches.
- OkHttp-based HTTP client with built-in authentication, reconciliation discovery, and retry-on-401
  handling.
- Tunable HTTP client timeouts (`connect`, `read`, and `write`) exposed via
  `reconciliation.ingestion.*` properties.
- `StructuredDataBatchBuilder` utilities for turning JSON arrays, Excel worksheets, or other
  delimited text feeds into CSV batches.

## Quick start

1. Install the library locally:

   ```bash
   ../../backend/mvnw -f libraries/ingestion-sdk/pom.xml clean install
   ```

2. Add the dependency to your Spring Boot ingestion project:

   ```xml
   <dependency>
       <groupId>com.universal.reconciliation.libraries</groupId>
       <artifactId>ingestion-sdk</artifactId>
       <version>0.1.0</version>
   </dependency>
   ```

3. Configure credentials and HTTP client settings via `application.yml` or command-line flags:

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

4. Inject `IngestionPipeline` into a `CommandLineRunner` and use the batch builders to stream data:

   ```java
   @Component
   class CashVsGlRunner implements CommandLineRunner {

       private final IngestionPipeline pipeline;
       private final JdbcTemplate jdbcTemplate;
       private final RestTemplate restTemplate;

       CashVsGlRunner(IngestionPipeline pipeline, JdbcTemplate jdbcTemplate, RestTemplate restTemplate) {
           this.pipeline = pipeline;
           this.jdbcTemplate = jdbcTemplate;
           this.restTemplate = restTemplate;
       }

       @Override
       public void run(String... args) throws Exception {
           JdbcCsvBatchBuilder jdbc = new JdbcCsvBatchBuilder(jdbcTemplate);
           IngestionBatch cashBatch = jdbc.build(
                   "CASH",
                   "cash-ledger",
                   "SELECT transaction_id AS transactionId, amount, currency FROM cash_ledger",
                   Map.of(),
                   List.of("transactionId", "amount", "currency"),
                   Map.of());

           RestApiCsvBatchBuilder api = new RestApiCsvBatchBuilder(restTemplate);
           IngestionBatch glBatch = api.get(
                   "GL",
                   "general-ledger",
                   URI.create("https://example.org/api/gl"),
                   List.of("transactionId", "amount", "currency", "tradeDate"),
                   Map.of(),
                   "payload.entries");

           IngestionScenario scenario = IngestionScenario.builder("cash-vs-gl")
                   .reconciliationCode("CASH_VS_GL_SIMPLE")
                   .description("Cash ledger vs GL daily load")
                   .addBatch(cashBatch)
                   .addBatch(glBatch)
                   .build();

           pipeline.run(scenario);
       }
   }
   ```

See [`examples/ingestion-sdk-example`](../../examples/ingestion-sdk-example/README.md) for a
complete runnable sample that combines JDBC and REST sources.

### Advanced REST extraction

`RestApiCsvBatchBuilder` supports nested responses and custom pagination flows. Point it at a nested
array using JSON Pointer syntax (`"payload.entries"` or `"/payload/entries"`), or supply a
`RecordExtractor` when the records span multiple fields/pages:

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

### Structured data helpers

`StructuredDataBatchBuilder` centralizes conversions for local files and in-memory datasets that are
already structured as records. Each helper produces a CSV-backed `IngestionBatch` so the platform
can ingest the data alongside JDBC or REST sources:

```java
StructuredDataBatchBuilder structured = new StructuredDataBatchBuilder();

// JSON array generated in-memory
String jsonArray = """
        [{"transactionId":"TXN-1001","amount":1550.45,"currency":"USD","tradeDate":"2024-05-15"},
         {"transactionId":"TXN-1002","amount":-320.10,"currency":"EUR","tradeDate":"2024-05-16"}]
        """;
IngestionBatch jsonBatch = structured.fromJsonArray(
        "CASH_JSON",
        "cash-ledger-json",
        new ByteArrayInputStream(jsonArray.getBytes(StandardCharsets.UTF_8)),
        List.of("transactionId", "amount", "currency", "tradeDate"),
        Map.of("format", "json-array"));

// Excel worksheet generated with Apache POI
byte[] custodianWorkbook;
try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
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

    workbook.write(out);
    custodianWorkbook = out.toByteArray();
}

IngestionBatch excelBatch = structured.fromExcel(
        "CUSTODIAN_XLSX",
        "custodian-positions",
        new ByteArrayInputStream(custodianWorkbook),
        "Positions",
        true,
        List.of(),
        Map.of("format", "excel"));

// Pipe-delimited text
String pipeDelimited = """
        tradeId|tradeDate|symbol|quantity|price
        TR-1001|2024-05-15|AAPL|150|185.42
        TR-1002|2024-05-15|MSFT|250|312.18
        TR-1003|2024-05-16|GOOGL|75|132.55
        """;
IngestionBatch pipeBatch = structured.fromDelimitedText(
        "TRADES_PIPE",
        "trades-psv",
        new StringReader(pipeDelimited),
        '|',
        true,
        List.of(),
        Map.of("format", "pipe-delimited"));

// Directly from in-memory records
List<Map<String, Object>> stagingRows = fetchRowsFromSomewhere();
IngestionBatch stagingBatch = structured.fromRecords(
        "STAGING",
        "staging-records",
        stagingRows,
        List.of("id", "amount"),
        Map.of());
```

## Build & test

```bash
../../backend/mvnw -f libraries/ingestion-sdk/pom.xml verify
```
