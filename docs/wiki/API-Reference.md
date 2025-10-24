## 7. API Reference

This section documents the REST surface exposed by the Universal Reconciliation Platform. Unless noted otherwise every endpoint
expects and produces `application/json` and requires a JWT bearer token issued by the authentication service. All timestamps
are emitted in ISO-8601 format and, where relevant, are normalised to the Asia/Singapore time zone.

### 7.1 Authentication
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/auth/login` | POST | Exchanges LDAP credentials for a signed JWT plus resolved group memberships. |

**Sample: Login**

_Request_
```http
POST /api/auth/login HTTP/1.1
Content-Type: application/json

{
  "username": "analyst.jane",
  "password": "Sup3rSecret!"
}
```

_Response `200 OK`_
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "displayName": "Jane Analyst",
  "groups": [
    "CN=RECON_PREPARERS,OU=Groups,DC=corp,DC=example",
    "CN=RECON_APPROVERS,OU=Groups,DC=corp,DC=example"
  ]
}
```

### 7.2 Analyst Workspace APIs
The analyst SPA orchestrates reconciliations, runs, break searches, and exports via the endpoints listed below. All calls require
that the JWT principal belongs to at least one access-control entry for the target reconciliation definition.

#### 7.2.1 Reconciliations & Runs
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/reconciliations` | GET | Lists reconciliations visible to the caller based on LDAP group membership. |
| `/api/reconciliations/{id}/runs` | GET | Returns the most recent runs for a reconciliation. Accepts `limit` (1–50, defaults to 5). |
| `/api/reconciliations/{id}/approvals` | GET | Fetches the checker approval queue (requires checker role within the user’s groups). |
| `/api/reconciliations/{id}/run` | POST | Triggers the matching engine. Body is optional; missing fields default to manual metadata. |
| `/api/reconciliations/{id}/runs/latest` | GET | Retrieves the latest run and applies optional filters (`product`, `subProduct`, `entity`, repeated `status`). |
| `/api/reconciliations/runs/{runId}` | GET | Retrieves a specific run, optionally filtered by the same query parameters as `runs/latest`. |

**Sample: Trigger a run**

_Request_
```http
POST /api/reconciliations/42/run HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "triggerType": "MANUAL_UI",
  "correlationId": "OPS-2024-09-15-001",
  "comments": "Month-end validation",
  "initiatedBy": "analyst.jane"
}
```

_Response `200 OK`_
The response body is a `RunDetailDto` object containing run summary, analytics, filtered break rows, and filter metadata, matching
the structure returned by `GET /api/reconciliations/{id}/runs/latest`.

#### 7.2.2 Break Search & Selection
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/reconciliations/{id}/results` | GET | Cursor-paginated break search. Supports query parameters documented below. |
| `/api/reconciliations/{id}/results/ids` | GET | Returns identifiers for all breaks matching the supplied filters (used for “select filtered”). |

Break search query parameters:
- `fromDate` / `toDate` — Inclusive ISO dates interpreted in Asia/Singapore time (`YYYY-MM-DD`).
- `runId` — Comma-separated run identifiers.
- `runType` — Comma-separated `TriggerType` values (e.g., `MANUAL_UI,SCHEDULED_CRON`).
- `status` — Comma-separated `BreakStatus` values.
- `filter.<attribute>` — Column-level filters, where `<attribute>` matches a canonical field name or classification.
- `operator.<attribute>` — Overrides the default equality operator using values from `FilterOperator` (e.g., `CONTAINS`).
- `search` — Free-text search across break metadata.
- `size` — Page size (defaults to 200, bounded to 5000).
- `cursor` — Encoded token returned from a previous page.
- `includeTotals` — `true` to request aggregate counts in the payload.

_Response shape_
```json
{
  "rows": [
    {
      "breakId": 91501,
      "runId": 1287,
      "runDateTime": "2024-09-20T13:10:42Z",
      "timezone": "Asia/Singapore",
      "triggerType": "SCHEDULED_CRON",
      "breakItem": {
        "id": 91501,
        "breakType": "MISMATCH",
        "status": "PENDING_APPROVAL",
        "classifications": {"product": "FX"},
        "allowedStatusTransitions": ["REJECTED", "CLOSED"],
        "detectedAt": "2024-09-20T13:10:42Z",
        "sources": {
          "SOURCE_A": {"transactionId": "TX-99321", "amount": 100000.0},
          "SOURCE_B": {"transactionId": "TX-99321", "amount": 95000.0}
        },
        "missingSources": [],
        "comments": [],
        "history": []
      },
      "attributes": {"subProduct": "US LISTED"}
    }
  ],
  "page": {
    "cursor": "eyJpZCI6OTE1MDEsIm9mZnNldCI6MjAwfQ==",
    "hasMore": true,
    "totalCount": 37
  },
  "columns": [
    {
      "field": "product",
      "displayName": "Product",
      "dataType": "STRING",
      "filterable": true
    }
  ]
}
```

#### 7.2.3 Break Operations
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/breaks/{id}/comments` | POST | Appends an audit comment to a break. Requires `comment` and an `action` code. |
| `/api/breaks/{id}/status` | PATCH | Transitions a break to a new `BreakStatus`. Optional `comment` and `correlationId`. |
| `/api/breaks/bulk` | POST | Applies a bulk action to multiple breaks. Requires `breakIds` plus a status change and/or comment. |

**Sample: Bulk status change**
```http
POST /api/breaks/bulk HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "breakIds": [91501, 91502, 91503],
  "status": "PENDING_APPROVAL",
  "comment": "Month-end certification",
  "correlationId": "OPS-2024-09-15-BULK"
}
```

#### 7.2.4 Saved Views
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/reconciliations/{id}/saved-views` | GET | Lists saved grid layouts for the reconciliation owned by the caller. |
| `/api/reconciliations/{id}/saved-views` | POST | Creates a saved view from the supplied `SavedViewRequest`. Returns `201 Created` with location header. |
| `/api/reconciliations/{id}/saved-views/{viewId}` | PUT | Updates a saved view (name, description, sharing, default flag, JSON settings). |
| `/api/reconciliations/{id}/saved-views/{viewId}` | DELETE | Deletes a saved view owned by the caller. |
| `/api/reconciliations/{id}/saved-views/{viewId}/default` | POST | Marks the view as the caller’s default. |
| `/api/saved-views/shared/{token}` | GET | Resolves a shared view token and returns the saved view payload if it exists. |

Saved view payloads capture the entire grid state (column order, filters, density, etc.) as a JSON blob inside `settingsJson`.

#### 7.2.5 Exports & Activity Feed
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/exports/runs/{runId}` | GET | Streams an immediate XLSX export for a run using the active report template. |
| `/api/reconciliations/{id}/export-jobs` | GET | Lists asynchronous dataset export jobs created by the caller (most recent first). |
| `/api/reconciliations/{id}/export-jobs` | POST | Queues a dataset export. Accepts `format` (`CSV`, `JSONL`, `XLSX`), optional filters, and metadata options. |
| `/api/export-jobs/{jobId}` | GET | Polls job status (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`). |
| `/api/export-jobs/{jobId}/download` | GET | Downloads the generated file once the job status is `COMPLETED`. |
| `/api/activity` | GET | Returns the most recent platform events (runs, workflow transitions, exports, configuration publishes). |

**Sample: Queue an export job**
```http
POST /api/reconciliations/42/export-jobs HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "format": "CSV",
  "filters": {
    "status": ["OPEN"],
    "fromDate": ["2024-09-01"],
    "toDate": ["2024-09-30"],
    "filter.product": ["FX"]
  },
  "fileNamePrefix": "cash-gl-september",
  "includeMetadata": true
}
```

_Response `202 Accepted`_
```json
{
  "id": 512,
  "definitionId": 42,
  "status": "QUEUED",
  "format": "CSV",
  "jobType": "RESULT_DATASET",
  "fileName": "cash-gl-september-20240930.csv",
  "rowCount": null,
  "createdAt": "2024-09-30T10:15:00Z",
  "updatedAt": "2024-09-30T10:15:00Z"
}
```

### 7.3 Administrative APIs
Administrative endpoints require the caller to hold the `RECON_ADMIN` role (granted via LDAP group membership). These APIs power
the configuration studio, metadata exports, and ingestion tooling.

#### 7.3.1 Reconciliation Authoring
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/admin/reconciliations` | GET | Paginates reconciliation definitions. Supports `status`, `owner`, `updatedAfter`, `updatedBefore`, `search`, `page`, `size`. |
| `/api/admin/reconciliations/{id}` | GET | Retrieves a specific reconciliation definition with sources, canonical fields, reports, and access entries. |
| `/api/admin/reconciliations` | POST | Creates a new reconciliation from an `AdminReconciliationRequest`. Returns `201 Created`. |
| `/api/admin/reconciliations/{id}` | PUT | Replaces the reconciliation definition. Requires a full payload (including sources and canonical fields). |
| `/api/admin/reconciliations/{id}` | PATCH | Applies a partial update (e.g., status transitions, ownership changes). |
| `/api/admin/reconciliations/{id}` | DELETE | Retires the reconciliation (soft delete). |
| `/api/admin/reconciliations/{id}/schema` | GET | Exports a JSON snapshot of the reconciliation metadata. |
| `/api/admin/reconciliations/{id}/sources/{sourceCode}/batches` | POST | Uploads a source batch. Multipart request with `metadata` (JSON) and `file` (payload). |

> **Note:** Canonical field mappings now expose only the structured `transformations` array. The legacy
> `transformationExpression` string has been removed from authoring and schema export payloads; clients
> still sending it will receive an `UnrecognizedPropertyException`.

**Sample: Create a reconciliation**
```http
POST /api/admin/reconciliations HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "CASH_GL",
  "name": "Cash vs GL",
  "description": "Matches nostro balances against the general ledger",
  "owner": "Finance Ops",
  "makerCheckerEnabled": true,
  "notes": "Cutover wave 2",
  "status": "PUBLISHED",
  "autoTriggerEnabled": false,
  "autoTriggerCron": null,
  "autoTriggerTimezone": null,
  "autoTriggerGraceMinutes": null,
  "sources": [
    {
      "code": "CASH",
      "displayName": "Cash staging feed",
      "adapterType": "CSV",
      "anchor": true,
      "description": "Daily nostro balances",
      "adapterOptions": "{\"delimiter\":\",\"}"
    },
    {
      "code": "GL",
      "displayName": "General Ledger",
      "adapterType": "JDBC",
      "anchor": false,
      "connectionConfig": "{\"url\":\"jdbc:mariadb://gl.example.com:3306/ledger\"}"
    }
  ],
  "canonicalFields": [
    {
      "canonicalName": "accountNumber",
      "displayName": "Account",
      "role": "KEY",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "required": true,
      "mappings": [
        { "sourceCode": "CASH", "sourceColumn": "account_id", "required": true },
        { "sourceCode": "GL", "sourceColumn": "account_id", "required": true }
      ]
    },
    {
      "canonicalName": "balance",
      "displayName": "Balance",
      "role": "COMPARE",
      "dataType": "DECIMAL",
      "comparisonLogic": "NUMERIC_THRESHOLD",
      "thresholdPercentage": 0.5,
      "required": true,
      "mappings": [
        { "sourceCode": "CASH", "sourceColumn": "balance" },
        { "sourceCode": "GL", "sourceColumn": "balance" }
      ]
    }
  ],
  "reportTemplates": [
    {
      "name": "Cash vs GL Exceptions",
      "description": "Break-only export",
      "includeMatched": false,
      "includeMismatched": true,
      "includeMissing": true,
      "highlightDifferences": true,
      "columns": [
        {
          "header": "Account",
          "source": "BREAK_METADATA",
          "sourceField": "accountNumber",
          "displayOrder": 1,
          "highlightDifferences": false
        },
        {
          "header": "Cash Balance",
          "source": "SOURCE_A",
          "sourceField": "balance",
          "displayOrder": 2,
          "highlightDifferences": true
        }
      ]
    }
  ],
  "accessControlEntries": [
    {
      "ldapGroupDn": "CN=RECON_MAKERS,OU=Groups,DC=corp,DC=example",
      "role": "MAKER",
      "product": "Payments",
      "notifyOnPublish": true,
      "notifyOnIngestionFailure": true,
      "notificationChannel": "teams://finance-ops"
    },
    {
      "ldapGroupDn": "CN=RECON_CHECKERS,OU=Groups,DC=corp,DC=example",
      "role": "CHECKER"
    }
  ]
}
```

**Sample: Upload a batch**
```bash
curl -X POST "https://recon.example.com/api/admin/reconciliations/42/sources/CASH/batches" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'metadata={"adapterType":"CSV","label":"2024-09-30","options":{"delimiter":","}};type=application/json' \
  -F "file=@cash_20240930.csv;type=text/csv"
```

#### 7.3.2 Transformation Toolkit
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/admin/transformations/validate` | POST | Validates canonical field transformation chains before publishing. |
| `/api/admin/transformations/preview` | POST | Applies transformations to sample data supplied inline and returns a preview payload. |
| `/api/admin/transformations/preview/upload` | POST | Accepts a sample file (CSV, Excel, JSON, XML, delimited text) plus parsing options and streams back up to ten transformed rows. |
| `/api/admin/transformations/groovy/test` | POST | Executes Groovy scripts against synthetic inputs to validate custom logic. |
| `/api/admin/transformations/groovy/generate` | POST | Produces a Groovy script from an AI prompt along with a human-readable summary. |
| `/api/admin/transformations/samples` | GET | Fetches source data samples for a definition/source combination. Query params: `definitionId`, `sourceCode`, `limit`. |

The Groovy generation endpoint accepts a payload containing the administrator prompt plus optional context such as field metadata, the current preview value, and a raw source row. The response returns the compiled script string and a helper summary; the server validates the script before returning it.

Validation and preview endpoints accept payloads describing the canonical field, transformations, and sample input values. If a
transformation fails, the API responds with `400 Bad Request` and a descriptive error message.

The upload-based preview endpoint receives a multipart request with:

- **request** — JSON body conforming to `TransformationFilePreviewUploadRequest` (file type, header
  flag, delimiter/record path, value column, row limit, and the transformation chain to execute).
- **file** — The sample data to parse. The API enforces a 2 MiB limit and returns at most ten
  transformed rows alongside any row-level errors.

### 7.4 Error Handling & Conventions
- **HTTP 400** — Validation failures (missing fields, malformed query parameters, transformation errors).
- **HTTP 401** — Missing or invalid JWT token.
- **HTTP 403** — Caller lacks the required role or group membership for the resource.
- **HTTP 404** — Resource not found (e.g., reconciliation, export job, saved view token).
- **HTTP 409** — Export job download attempted before completion.
- **HTTP 500** — Unexpected server errors (logged with correlation identifiers in the activity feed).

All responses include a stable `message` field when an error is raised so clients can surface actionable feedback to end users.
