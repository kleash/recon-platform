# Tutorial: Creating a New Reconciliation

This tutorial walks through the modern, metadata-driven workflow for launching a new reconciliation on the Universal
Reconciliation Platform. You will:

1. Author a reconciliation definition with canonical fields and access control via the admin API.
2. Upload sample source data using the ingestion endpoint.
3. Trigger the matching engine and review run outcomes.
4. Explore analyst tooling (break search, saved views, bulk actions).

The steps below assume you have a valid admin JWT token and `curl` (or any REST client) available. Replace sample IDs with the
values returned by your environment.

## Prerequisites
- Backend service running locally or in a sandbox environment.
- Admin credentials mapped to the `RECON_ADMIN` LDAP group.
- Sample CSV data for both sources (see examples below).
- `BASE_URL` shell variable pointing at the backend (e.g., `http://localhost:8080`).
- `TOKEN` shell variable holding a valid JWT (`export TOKEN=...`).

## Step 1: Create the Reconciliation Definition
Use the admin authoring endpoint to declare sources, canonical fields, report templates, and access control in a single payload.

```bash
curl -X POST "$BASE_URL/api/admin/reconciliations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "code": "TRADING_FEE",
  "name": "Trading Fee vs Broker Statement",
  "description": "Compares internal trading fee accruals against broker statements",
  "owner": "Capital Markets Ops",
  "makerCheckerEnabled": true,
  "notes": "Pilot scoped to equities desk",
  "status": "PUBLISHED",
  "autoTriggerEnabled": false,
  "sources": [
    {
      "code": "INTERNAL",
      "displayName": "Internal Fee Accruals",
      "adapterType": "CSV",
      "anchor": true,
      "adapterOptions": "{\"delimiter\":\",\"}"
    },
    {
      "code": "BROKER",
      "displayName": "Broker Statement",
      "adapterType": "CSV",
      "anchor": false,
      "adapterOptions": "{\"delimiter\":\",\"}"
    }
  ],
  "canonicalFields": [
    {
      "canonicalName": "tradeId",
      "displayName": "Trade ID",
      "role": "KEY",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "required": true,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "tradeId", "required": true },
        { "sourceCode": "BROKER", "sourceColumn": "tradeId", "required": true }
      ]
    },
    {
      "canonicalName": "settlementDate",
      "displayName": "Settlement Date",
      "role": "KEY",
      "dataType": "DATE",
      "comparisonLogic": "DATE_ONLY",
      "required": true,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "settlementDate" },
        { "sourceCode": "BROKER", "sourceColumn": "settlementDate" }
      ]
    },
    {
      "canonicalName": "feeAmount",
      "displayName": "Fee Amount",
      "role": "COMPARE",
      "dataType": "DECIMAL",
      "comparisonLogic": "NUMERIC_THRESHOLD",
      "thresholdPercentage": 1.0,
      "required": true,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "feeAmount" },
        { "sourceCode": "BROKER", "sourceColumn": "feeAmount" }
      ]
    },
    {
      "canonicalName": "product",
      "displayName": "Product",
      "role": "PRODUCT",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "required": true,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "product" },
        { "sourceCode": "BROKER", "sourceColumn": "product" }
      ]
    },
    {
      "canonicalName": "desk",
      "displayName": "Desk",
      "role": "SUB_PRODUCT",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "required": false,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "desk" },
        { "sourceCode": "BROKER", "sourceColumn": "desk" }
      ]
    },
    {
      "canonicalName": "legalEntity",
      "displayName": "Legal Entity",
      "role": "ENTITY",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "required": false,
      "mappings": [
        { "sourceCode": "INTERNAL", "sourceColumn": "legalEntity" },
        { "sourceCode": "BROKER", "sourceColumn": "legalEntity" }
      ]
    }
  ],
  "reportTemplates": [
    {
      "name": "Trading Fee Exceptions",
      "description": "Break-only export for approvals",
      "includeMatched": false,
      "includeMismatched": true,
      "includeMissing": true,
      "highlightDifferences": true,
      "columns": [
        { "header": "Trade ID", "source": "BREAK_METADATA", "sourceField": "tradeId", "displayOrder": 1, "highlightDifferences": false },
        { "header": "Internal Fee", "source": "SOURCE_A", "sourceField": "feeAmount", "displayOrder": 2, "highlightDifferences": true },
        { "header": "Broker Fee", "source": "SOURCE_B", "sourceField": "feeAmount", "displayOrder": 3, "highlightDifferences": true },
        { "header": "Status", "source": "BREAK_METADATA", "sourceField": "status", "displayOrder": 4, "highlightDifferences": false }
      ]
    }
  ],
  "accessControlEntries": [
    {
      "ldapGroupDn": "CN=RECON_MAKERS,OU=Groups,DC=corp,DC=example",
      "role": "MAKER",
      "product": "Equities",
      "subProduct": "Cash Desk",
      "notifyOnPublish": true,
      "notifyOnIngestionFailure": true,
      "notificationChannel": "teams://ops-alerts"
    },
    {
      "ldapGroupDn": "CN=RECON_CHECKERS,OU=Groups,DC=corp,DC=example",
      "role": "CHECKER"
    },
    {
      "ldapGroupDn": "CN=RECON_VIEWERS,OU=Groups,DC=corp,DC=example",
      "role": "VIEWER"
    }
  ]
}
JSON
```

Record the returned `id`; the examples below assume the API created definition **ID 101**.

## Step 2: Upload Source Batches
Create two CSV files (`internal_trading_fee.csv` and `broker_trading_fee.csv`) with aligned columns. Example:

```
tradeId,settlementDate,feeAmount,product,desk,legalEntity
TX-101,2024-09-20,150.25,Equities,Cash Desk,US Fund
TX-102,2024-09-20,200.00,Equities,Cash Desk,US Fund
```

Upload each file against its source using multipart requests:

```bash
curl -X POST "$BASE_URL/api/admin/reconciliations/101/sources/INTERNAL/batches" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'metadata={"adapterType":"CSV","label":"2024-09-20","options":{"delimiter":","}};type=application/json' \
  -F "file=@internal_trading_fee.csv;type=text/csv"

curl -X POST "$BASE_URL/api/admin/reconciliations/101/sources/BROKER/batches" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'metadata={"adapterType":"CSV","label":"2024-09-20","options":{"delimiter":","}};type=application/json' \
  -F "file=@broker_trading_fee.csv;type=text/csv"
```

Monitor the ingestion status via the activity feed (`GET /api/activity`) or by polling the admin UI.

## Step 3: Trigger a Run
Once both batches are available, execute the reconciliation. Makers or admins can invoke the standard analyst endpoint:

```bash
curl -X POST "$BASE_URL/api/reconciliations/101/run" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "triggerType": "MANUAL_API",
        "correlationId": "TRADING-FEE-UAT-001",
        "comments": "Pilot dry run"
      }'
```

The response includes a `runId`. Use it to fetch detailed analytics and break rows:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/reconciliations/runs/{runId}?status=OPEN&status=PENDING_APPROVAL"
```

## Step 4: Investigate Breaks & Save Analyst Views
Analysts consume break data through the `/api/reconciliations/{id}/results` endpoint. Example query for open breaks detected on
20 September:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/reconciliations/101/results?fromDate=2024-09-20&toDate=2024-09-20&status=OPEN"
```

Use the `columns` metadata in the response to build a grid layout. Persist personalised filters or column order by POSTing a
saved view:

```bash
curl -X POST "$BASE_URL/api/reconciliations/101/saved-views" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "FX Desk Exceptions",
        "description": "Filters to open breaks for the FX desk",
        "settingsJson": "{\"filters\":{\"product\":[\"Equities\"],\"desk\":[\"Cash Desk\"],\"status\":[\"OPEN\"]}}",
        "shared": true,
        "defaultView": false
      }'
```

Share the generated token (`GET /api/reconciliations/101/saved-views`) with teammates or set it as the default view using the
`/default` endpoint.

## Step 5: Action Breaks & Export Evidence
Makers can comment and request approval directly from the API:

```bash
curl -X POST "$BASE_URL/api/breaks/5012/comments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"Validated fee difference with broker","action":"COMMENT"}'

curl -X PATCH "$BASE_URL/api/breaks/5012/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"PENDING_APPROVAL","comment":"Ready for checker sign-off"}'
```

For large volumes, collect break IDs using `/results/ids` and submit a bulk request:

```bash
curl -X POST "$BASE_URL/api/breaks/bulk" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "breakIds": [5012, 5013, 5014],
        "status": "PENDING_APPROVAL",
        "comment": "Batch close-out",
        "correlationId": "TRADING-FEE-UAT-BULK-1"
      }'
```

When approvals complete, queue an asynchronous export for audit evidence:

```bash
curl -X POST "$BASE_URL/api/reconciliations/101/export-jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "format": "XLSX",
        "filters": {
          "status": ["CLOSED"],
          "filter.product": ["Equities"]
        },
        "fileNamePrefix": "trading-fee-uplift",
        "includeMetadata": true
      }'
```

Poll `/api/export-jobs/{jobId}` until the status is `COMPLETED`, then download the file via `/download`.

## Step 6: Validate Activity & Monitor SLAs
Use `/api/activity` to confirm run execution, bulk operations, and exports are recorded. Operations teams typically
surface this feed in dashboards to monitor SLA adherence and investigate anomalies.

## Troubleshooting Tips
| Symptom | Suggested Checks |
| --- | --- |
| `403 Forbidden` when calling analyst endpoints | Ensure your JWT contains an LDAP group mapped in `access_control_entries` with the appropriate role. |
| Missing breaks after ingestion | Confirm both source batches completed successfully and the canonical keys align (e.g., `tradeId`, `settlementDate`). |
| Export job stuck in `PROCESSING` | Review backend logs for transformation errors; payload metadata is stored in `export_jobs.error_message`. |
| Approval queue empty for checkers | Verify at least one access entry grants the `CHECKER` role and that makers submitted breaks for approval. |

By following these steps you can stand up a new reconciliation end-to-end using the current platform APIs without writing
custom Java pipelines.
