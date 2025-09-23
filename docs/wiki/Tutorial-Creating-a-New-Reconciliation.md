# Tutorial: Creating a New Reconciliation

This tutorial walks you through the process of adding a new reconciliation to the platform. We will build a simple reconciliation from scratch, explaining each step along the way.

To create a new reconciliation, you will create a single Java class that extends `AbstractSampleEtlPipeline`. This class will be responsible for:
1.  Defining the reconciliation and its fields.
2.  Configuring reports and access control.
3.  Loading the source data from CSV files.

Let's get started.

## Step 1: Create the Pipeline Class

First, create a new Java class in the `src/main/java/com/universal/reconciliation/etl/` directory. Let's call it `TradingFeeEtlPipeline.java`.

This class must:
*   Extend `AbstractSampleEtlPipeline`.
*   Be annotated with `@Component` to be detected by Spring.
*   Be annotated with `@Order` to control the execution sequence if you have multiple pipelines.

```java
package com.universal.reconciliation.etl;

import com.universal.reconciliation.domain.entity.*;
import com.universal.reconciliation.domain.enums.*;
import com.universal.reconciliation.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20) // Use a higher order to run after the simple GL example
public class TradingFeeEtlPipeline extends AbstractSampleEtlPipeline {

    private static final String DEFINITION_CODE = "TRADING_FEE_BROKER";

    public TradingFeeEtlPipeline(
            ReconciliationDefinitionRepository definitionRepository,
            AccessControlEntryRepository accessControlEntryRepository,
            SourceRecordARepository sourceRecordARepository,
            SourceRecordBRepository sourceRecordBRepository) {
        super(definitionRepository, accessControlEntryRepository, sourceRecordARepository, sourceRecordBRepository);
    }

    @Override
    public String name() {
        return "Trading Fee vs Broker Statement";
    }

    @Override
    @Transactional
    public void run() {
        // We will fill this in the next steps
    }
}
```

## Step 2: Implement the `run()` Method

The `run()` method is the entry point for your pipeline. It orchestrates all the steps for creating and loading the reconciliation.

Copy the following structure into your `run()` method. It includes a check to prevent creating the reconciliation if it already exists, which is useful for development.

```java
@Override
@Transactional
public void run() {
    if (definitionExists(DEFINITION_CODE)) {
        log.debug("Skipping {} ETL because the definition already exists", DEFINITION_CODE);
        return;
    }

    // 1. Create the Definition
    ReconciliationDefinition definition = definition(
            DEFINITION_CODE,
            name(),
            "A sample reconciliation for matching internal trading fee calculations against external broker statements.",
            true); // Maker-checker enabled

    // 2. Register Fields
    registerFields(definition);

    // 3. Configure Reports
    configureReportTemplate(definition);

    // 4. Save the Definition
    definitionRepository.save(definition);

    // 5. Set Up Access Control
    List<AccessControlEntry> entries = List.of(
            entry(definition, "recon-makers", AccessRole.MAKER, "Equities", "Commission", "US"),
            entry(definition, "recon-checkers", AccessRole.CHECKER, "Equities", "Commission", "US"));
    accessControlEntryRepository.saveAll(entries);

    // 6. Load Source Data
    // (We will add this in a later step)

    log.info("Successfully seeded reconciliation: {}", DEFINITION_CODE);
}
```

## Step 3: Define the Reconciliation Fields

This is the most important step. You need to define the fields that the matching engine will use. The `AbstractSampleEtlPipeline` provides a helper method `addField()` that makes this easy.

Add the following `registerFields` method to your class.

```java
private void registerFields(ReconciliationDefinition definition) {
    // --- Matching Keys ---
    // The engine will group records from both sources using these fields.
    addField(definition, "tradeId", "Trade ID", FieldRole.KEY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
    addField(definition, "settlementDate", "Settlement Date", FieldRole.KEY, FieldDataType.DATE, ComparisonLogic.DATE_ONLY, null);

    // --- Comparison Fields ---
    // After grouping by keys, the engine will compare these fields. A mismatch here creates a break.
    addField(definition, "feeAmount", "Fee Amount", FieldRole.COMPARE, FieldDataType.DECIMAL, ComparisonLogic.NUMERIC_THRESHOLD, new BigDecimal("1.5")); // 1.5% tolerance
    addField(definition, "currency", "Currency", FieldRole.COMPARE, FieldDataType.STRING, ComparisonLogic.CASE_INSENSITIVE, null);

    // --- Dimensional / Access Control Fields ---
    // These fields are used to filter data for users.
    addField(definition, "product", "Product", FieldRole.PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
    addField(definition, "subProduct", "Sub Product", FieldRole.SUB_PRODUCT, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);
    addField(definition, "entityName", "Entity", FieldRole.ENTITY, FieldDataType.STRING, ComparisonLogic.EXACT_MATCH, null);

    // --- Display-Only Fields ---
    // These fields are shown in the UI but not used for matching.
    addField(definition, "executingBroker", "Executing Broker", FieldRole.DISPLAY, FieldDataType.STRING, null, null);
}
```

**Field Roles Explained:**
*   `KEY`: Used to pair records from Source A and Source B.
*   `COMPARE`: Used to check for differences between paired records.
*   `PRODUCT`, `SUB_PRODUCT`, `ENTITY`: Used for data entitlement and filtering in the UI.
*   `DISPLAY`: Shown in the UI but not used for matching logic.

## Step 4: Configure the Excel Report

You can define the layout for Excel exports. The helper methods `template()` and `column()` make this straightforward.

Add the following `configureReportTemplate` method to your class:

```java
private void configureReportTemplate(ReconciliationDefinition definition) {
    ReportTemplate template = template(
            definition,
            "Default Trading Fee Export",
            "Standard template for exporting trading fee breaks.",
            true, true, true, true); // Include matched, mismatched, missing, and highlight differences
    definition.getReportTemplates().add(template);

    // Add columns in the desired order
    template.getColumns().add(column(template, "Trade ID (Internal)", ReportColumnSource.SOURCE_A, "tradeId", 1, true));
    template.getColumns().add(column(template, "Trade ID (Broker)", ReportColumnSource.SOURCE_B, "tradeId", 2, true));
    template.getColumns().add(column(template, "Fee (Internal)", ReportColumnSource.SOURCE_A, "feeAmount", 3, true));
    template.getColumns().add(column(template, "Fee (Broker)", ReportColumnSource.SOURCE_B, "feeAmount", 4, true));
    template.getColumns().add(column(template, "Currency", ReportColumnSource.SOURCE_A, "currency", 5, true));
    template.getColumns().add(column(template, "Break Status", ReportColumnSource.BREAK_METADATA, "status", 6, false));
}
```

## Step 5: Prepare and Load the Data

The final step is to load the data from CSV files.

First, create two new CSV files:
1.  `backend/src/main/resources/etl/trading/trading_fee_source_a.csv`
2.  `backend/src/main/resources/etl/trading/trading_fee_source_b.csv`

**`trading_fee_source_a.csv` (Internal System):**
```csv
tradeId,settlementDate,feeAmount,currency,product,subProduct,entityName,executingBroker
TX-101,2025-09-20,150.25,USD,Equities,Commission,US,BROKER-X
TX-102,2025-09-20,200.00,USD,Equities,Commission,US,BROKER-Y
```

**`trading_fee_source_b.csv` (Broker Statement):**
```csv
tradeId,settlementDate,feeAmount,currency,product,subProduct,entityName,executingBroker
TX-101,2025-09-20,150.50,usd,Equities,Commission,US,BROKER-X
TX-102,2025-09-21,200.00,USD,Equities,Commission,US,BROKER-Y
```

Now, add the data loading logic to your `run()` method and create the required mapping methods.

```java
// In run() method, replace the "// 6. Load Source Data" comment with this:
List<SourceRecordA> sourceARecords = readCsv("etl/trading/trading_fee_source_a.csv").stream()
        .map(row -> mapSourceA(definition, row))
        .toList();
sourceRecordARepository.saveAll(sourceARecords);

List<SourceRecordB> sourceBRecords = readCsv("etl/trading/trading_fee_source_b.csv").stream()
        .map(row -> mapSourceB(definition, row))
        .toList();
sourceRecordBRepository.saveAll(sourceBRecords);

log.info(
        "Seeded {} with {} source A and {} source B records",
        DEFINITION_CODE,
        sourceARecords.size(),
        sourceBRecords.size());

// Add these mapping methods to your class:
private SourceRecordA mapSourceA(ReconciliationDefinition definition, Map<String, String> row) {
    SourceRecordA record = new SourceRecordA();
    record.setDefinition(definition);
    record.setTradeId(row.get("tradeId"));
    record.setSettlementDate(date(row.get("settlementDate")));
    record.setFeeAmount(decimal(row.get("feeAmount")));
    record.setCurrency(row.get("currency"));
    record.setProduct(row.get("product"));
    record.setSubProduct(row.get("subProduct"));
    record.setEntityName(row.get("entityName"));
    record.setExecutingBroker(row.get("executingBroker"));
    return record;
}

private SourceRecordB mapSourceB(ReconciliationDefinition definition, Map<String, String> row) {
    SourceRecordB record = new SourceRecordB();
    record.setDefinition(definition);
    record.setTradeId(row.get("tradeId"));
    record.setSettlementDate(date(row.get("settlementDate")));
    record.setFeeAmount(decimal(row.get("feeAmount")));
    record.setCurrency(row.get("currency"));
    record.setProduct(row.get("product"));
    record.setSubProduct(row.get("subProduct"));
    record.setEntityName(row.get("entityName"));
    record.setExecutingBroker(row.get("executingBroker"));
    return record;
}
```
**Note:** The fields in `SourceRecordA` and `SourceRecordB` (e.g., `setTradeId`, `setFeeAmount`) are generic. You must add any new fields you need to these classes if they don't already exist. For this tutorial, we assume they are already present.

## Conclusion

That's it! When you restart the application, the `TradingFeeEtlPipeline` will run, and your new "Trading Fee vs Broker Statement" reconciliation will appear in the UI, ready for use.

You have successfully:
- Defined a new reconciliation with matching keys and comparison rules.
- Configured a custom Excel report.
- Mapped user access to the new reconciliation.
- Created a data loading pipeline from CSV files.
