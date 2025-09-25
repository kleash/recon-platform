## 7. API Reference

All endpoints (except `/api/auth/login`) require a JWT bearer token obtained during login. The Angular SPA forwards the token via the `Authorization: Bearer <token>` header for every request.

### 7.1 Authentication
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/auth/login` | POST | Authenticate against LDAP and receive a signed JWT plus group entitlements. |

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

### 7.2 Reconciliations
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/reconciliations` | GET | Return reconciliations visible to the caller based on LDAP group membership. |
| `/api/reconciliations/{id}/run` | POST | Trigger the matching engine for the specified reconciliation definition. |
| `/api/reconciliations/{id}/runs/latest` | GET | Fetch the latest completed run plus optional break filters (`product`, `subProduct`, `entity`, `status`). |
| `/api/reconciliations/runs/{runId}` | GET | Retrieve any historical run by identifier, with the same filter parameters as `runs/latest`. |

**Sample: List reconciliations**

_Response `200 OK`_
```json
[
  {
    "id": 42,
    "code": "SECURITY_POSITIONS",
    "name": "Security Position Reconciliation",
    "description": "Compares custody holdings against internal positions"
  },
  {
    "id": 77,
    "code": "CASH_BALANCES",
    "name": "Cash Nostro Reconciliation",
    "description": "Matches nostro balances to the general ledger"
  }
]
```

**Sample: Trigger a run**

_Request_
```http
POST /api/reconciliations/42/run HTTP/1.1
Content-Type: application/json
Authorization: Bearer <token>

{
  "triggerType": "MANUAL_API",
  "correlationId": "OPS-2024-09-15-001",
  "comments": "Month-end validation",
  "initiatedBy": "analyst.jane"
}
```

_Response `200 OK`_
```json
{
  "summary": {
    "definitionId": 42,
    "runId": 915,
    "runDateTime": "2024-09-15T21:05:17Z",
    "triggerType": "MANUAL_API",
    "triggeredBy": "analyst.jane",
    "triggerCorrelationId": "OPS-2024-09-15-001",
    "triggerComments": "Month-end validation",
    "matched": 15892,
    "mismatched": 37,
    "missing": 4
  },
  "analytics": {
    "breaksByStatus": {"OPEN": 29, "PENDING_APPROVAL": 8},
    "breaksByType": {"MISMATCH": 33, "MISSING_IN_SOURCE_A": 4},
    "breaksByProduct": {"EQUITIES": 19, "FIXED_INCOME": 18},
    "breaksByEntity": {"Fund-01": 12, "Fund-02": 25},
    "openBreaksByAgeBucket": {"<1d": 21, "1-3d": 8},
    "filteredBreakCount": 37,
    "totalBreakCount": 37,
    "totalMatchedCount": 15892
  },
  "breaks": [
    {
      "id": 5012,
      "breakType": "MISMATCH",
      "status": "OPEN",
      "product": "EQUITIES",
      "subProduct": "US LISTED",
      "entity": "Fund-01",
      "allowedStatusTransitions": ["PENDING_APPROVAL"],
      "detectedAt": "2024-09-15T21:05:18Z",
      "sourceA": {"transactionId": "TX-99321", "amount": 100000.0},
      "sourceB": {"transactionId": "TX-99321", "amount": 95000.0},
      "comments": []
    }
  ],
  "filters": {
    "products": ["EQUITIES", "FIXED_INCOME"],
    "subProducts": ["US LISTED", "CORPORATE"],
    "entities": ["Fund-01", "Fund-02"],
    "statuses": ["OPEN", "PENDING_APPROVAL", "CLOSED"]
  }
}
```

**Sample: Fetch latest run with filters**

_Request_
```http
GET /api/reconciliations/42/runs/latest?product=EQUITIES&status=OPEN&status=PENDING_APPROVAL HTTP/1.1
Authorization: Bearer <token>
```

_Response `200 OK`_: identical payload shape as the trigger response but scoped to filtered breaks only.

### 7.3 Break Management
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/breaks/{id}/comments` | POST | Append a timeline comment and optional action code to a break. |
| `/api/breaks/{id}/status` | PATCH | Transition a break to `PENDING_APPROVAL` or `CLOSED` (maker/checker enforced). |
| `/api/breaks/bulk` | POST | Apply a shared status change and/or comment to multiple breaks. |

**Sample: Comment on a break**

_Request_
```http
POST /api/breaks/5012/comments HTTP/1.1
Content-Type: application/json
Authorization: Bearer <token>

{
  "comment": "Investigated variance with custodian, awaiting approval",
  "action": "COMMENT"
}
```

_Response `200 OK`_
```json
{
  "id": 5012,
  "breakType": "MISMATCH",
  "status": "OPEN",
  "product": "EQUITIES",
  "subProduct": "US LISTED",
  "entity": "Fund-01",
  "allowedStatusTransitions": ["PENDING_APPROVAL"],
  "detectedAt": "2024-09-15T21:05:18Z",
  "sourceA": {"transactionId": "TX-99321", "amount": 100000.0},
  "sourceB": {"transactionId": "TX-99321", "amount": 95000.0},
  "comments": [
    {
      "id": 88291,
      "actorDn": "CN=analyst.jane,OU=Users,DC=corp,DC=example",
      "action": "COMMENT",
      "comment": "Investigated variance with custodian, awaiting approval",
      "createdAt": "2024-09-15T21:12:03Z"
    }
  ]
}
```

**Sample: Bulk transition**

_Request_
```http
POST /api/breaks/bulk HTTP/1.1
Content-Type: application/json
Authorization: Bearer <token>

{
  "breakIds": [5012, 5013, 5017],
  "status": "PENDING_APPROVAL",
  "comment": "Escalated for checker review",
  "action": "BULK_ESCALATE"
}
```

_Response `200 OK`_: returns an array of updated `BreakItemDto` objects mirroring the structure above.

### 7.4 Administration
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/admin/reconciliations` | GET | List reconciliation definitions with filters (`status`, `owner`, `search`, `updatedAfter`, `updatedBefore`) and pagination (`page`, `size`). |
| `/api/admin/reconciliations` | POST | Create a reconciliation definition (sources, canonical fields, reports, access entries). Requires `ROLE_RECON_ADMIN`. |
| `/api/admin/reconciliations/{id}` | GET | Retrieve the full configuration graph for authoring workflows. |
| `/api/admin/reconciliations/{id}` | PUT | Replace a configuration graph. Include the latest `version` to satisfy optimistic locking. |
| `/api/admin/reconciliations/{id}` | PATCH | Toggle maker-checker, notes, or lifecycle status without resubmitting nested metadata. |
| `/api/admin/reconciliations/{id}` | DELETE | Soft-retire a reconciliation definition and hide it from analyst views. |
| `/api/admin/reconciliations/{id}/schema` | GET | Export canonical schema metadata for ETL teams and automation scripts. |
| `/api/admin/reconciliations/{id}/sources/{code}/batches` | POST | Upload a source data batch via multipart form-data. Accepts file payload plus adapter metadata. |

**Sample: List reconciliation definitions**

_Request_
```http
GET /api/admin/reconciliations?status=PUBLISHED&owner=operations&page=0&size=10 HTTP/1.1
Authorization: Bearer <token>
```

_Response `200 OK`_
```json
{
  "items": [
    {
      "id": 101,
      "code": "CUSTODY_GL",
      "name": "Custody vs GL",
      "status": "PUBLISHED",
      "makerCheckerEnabled": true,
      "updatedAt": "2024-05-13T08:25:00Z",
      "owner": "Custody Ops",
      "updatedBy": "admin.user",
      "lastIngestionAt": "2024-05-13T08:00:00Z"
    }
  ],
  "totalElements": 12,
  "totalPages": 2,
  "page": 0,
  "size": 10
}
```

**Sample: Create a reconciliation definition**

_Request_
```http
POST /api/admin/reconciliations HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "CUSTODY_GL",
  "name": "Custody vs GL",
  "description": "Matches daily custody positions with the general ledger",
  "owner": "Custody Ops",
  "makerCheckerEnabled": true,
  "notes": "Pilot with the custody operations team",
  "status": "DRAFT",
  "autoTriggerEnabled": true,
  "autoTriggerCron": "0 2 * * *",
  "autoTriggerTimezone": "UTC",
  "autoTriggerGraceMinutes": 30,
  "sources": [
    {
      "code": "CUSTODY_FEED",
      "displayName": "Custody CSV",
      "adapterType": "CSV_FILE",
      "anchor": true,
      "description": "Daily custodial export",
      "arrivalExpectation": "Weekdays by 18:00",
      "arrivalTimezone": "America/New_York",
      "arrivalSlaMinutes": 60
    },
    {
      "code": "GL_LEDGER",
      "displayName": "General Ledger",
      "adapterType": "DATABASE",
      "anchor": false,
      "description": "Ledger staging table"
    }
  ],
  "canonicalFields": [
    {
      "canonicalName": "tradeId",
      "displayName": "Trade ID",
      "role": "KEY",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "formattingHint": null,
      "required": true,
      "mappings": [
        {"sourceCode": "CUSTODY_FEED", "sourceColumn": "trade_id", "required": true},
        {"sourceCode": "GL_LEDGER", "sourceColumn": "trade_id", "required": true}
      ]
    }
  ],
  "reportTemplates": [],
  "accessControlEntries": [
    {
      "ldapGroupDn": "CN=RECON_ADMIN,OU=Groups,DC=corp,DC=example",
      "role": "MAKER",
      "notifyOnPublish": true,
      "notifyOnIngestionFailure": true,
      "notificationChannel": "recon-admins@example.com"
    }
  ]
}
```

_Response `201 Created`_
```json
{
  "id": 101,
  "code": "CUSTODY_GL",
  "name": "Custody vs GL",
  "description": "Matches daily custody positions with the general ledger",
  "status": "DRAFT",
  "makerCheckerEnabled": true,
  "owner": "Custody Ops",
  "autoTriggerEnabled": true,
  "autoTriggerCron": "0 2 * * *",
  "autoTriggerTimezone": "UTC",
  "version": 0,
  "sources": [
    {
      "id": 201,
      "code": "CUSTODY_FEED",
      "adapterType": "CSV_FILE",
      "anchor": true,
      "arrivalExpectation": "Weekdays by 18:00",
      "arrivalTimezone": "America/New_York",
      "arrivalSlaMinutes": 60
    }
  ],
  "canonicalFields": [
    {
      "canonicalName": "tradeId",
      "displayName": "Trade ID",
      "role": "KEY",
      "formattingHint": null,
      "mappings": [
        {"sourceCode": "CUSTODY_FEED", "sourceColumn": "trade_id"},
        {"sourceCode": "GL_LEDGER", "sourceColumn": "trade_id"}
      ]
    }
  ]
}
```

**Sample: Export schema**

_Request_
```http
GET /api/admin/reconciliations/101/schema HTTP/1.1
Authorization: Bearer <token>
```

_Response `200 OK`_
```json
{
  "definitionId": 101,
  "code": "CUSTODY_GL",
  "name": "Custody vs GL",
  "sources": [
    {
      "code": "CUSTODY_FEED",
      "adapterType": "CSV_FILE",
      "anchor": true,
      "connectionConfig": null,
      "arrivalExpectation": "Weekdays by 18:00",
      "arrivalTimezone": "America/New_York",
      "arrivalSlaMinutes": 60,
      "adapterOptions": null,
      "ingestionEndpoint": "/api/admin/reconciliations/101/sources/CUSTODY_FEED/batches"
    },
    {
      "code": "GL_LEDGER",
      "adapterType": "DATABASE",
      "anchor": false,
      "connectionConfig": null,
      "arrivalExpectation": null,
      "arrivalTimezone": null,
      "arrivalSlaMinutes": null,
      "adapterOptions": null,
      "ingestionEndpoint": "/api/admin/reconciliations/101/sources/GL_LEDGER/batches"
    }
  ],
  "fields": [
    {
      "displayName": "Trade ID",
      "canonicalName": "tradeId",
      "role": "KEY",
      "dataType": "STRING",
      "comparisonLogic": "EXACT_MATCH",
      "formattingHint": null,
      "required": true,
      "mappings": [
        {"sourceCode": "CUSTODY_FEED", "sourceColumn": "trade_id"},
        {"sourceCode": "GL_LEDGER", "sourceColumn": "trade_id"}
      ]
    }
  ]
}
```

**Validation rules**

- Source codes must be unique and exactly one source must be flagged as the anchor.
- Canonical field names must be unique, each field requires at least one mapping, and at least one field must use the `KEY` role.
- Numeric threshold comparisons demand a non-negative `thresholdPercentage`; other comparison types must omit the value.
- Report template names are deduplicated per reconciliation; access-control entries remain optional.
- Optimistic locking is enforced through the `version` attribute on `PUT` operations. Conflicts return `409 Conflict`.

**Sample: Upload a batch using the CSV adapter**

_Request_
```http
POST /api/admin/reconciliations/101/sources/CUSTODY_FEED/batches HTTP/1.1
Authorization: Bearer <token>
Content-Type: multipart/form-data; boundary=----BOUNDARY

------BOUNDARY
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{
  "adapterType": "CSV_FILE",
  "label": "custody-2024-04-01",
  "options": {"delimiter": ","}
}
------BOUNDARY
Content-Disposition: form-data; name="file"; filename="custody.csv"
Content-Type: text/csv

trade_id,net_amount,currency
T-1001,125000.00,USD
T-1002,88000.50,USD
------BOUNDARY--
```

_Response `200 OK`_
```json
{
  "id": 301,
  "label": "custody-2024-04-01",
  "status": "COMPLETE",
  "recordCount": 2,
  "checksum": "2e9bf0cb-7f41-4d7a-97bd-7d99179c1e8a",
  "ingestedAt": "2024-04-01T08:15:00Z"
}
```

### 7.5 Reporting & Activity
| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/exports/runs/{runId}` | GET | Stream an Excel workbook for the specified run using the active report template. |
| `/api/activity` | GET | Retrieve the 50 most recent system activity entries visible to the caller. |

**Sample: Activity feed**

_Response `200 OK`_
```json
[
  {
    "id": 3391,
    "eventType": "RECONCILIATION_RUN",
    "details": "Run 915 completed successfully",
    "recordedAt": "2024-09-15T21:05:22Z"
  },
  {
    "id": 3392,
    "eventType": "BREAK_STATUS_CHANGE",
    "details": "Break 5012 moved to PENDING_APPROVAL by CN=analyst.jane,OU=Users,DC=corp,DC=example",
    "recordedAt": "2024-09-15T21:12:05Z"
  }
]
```

> 📄 **Excel exports:** The `/api/exports/runs/{runId}` endpoint streams binary content (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`). Angular triggers it via `window.open` or `HttpClient` with `responseType: 'blob'` and prompts the user to download `reconciliation-run-<id>.xlsx`.

### 7.6 Error Handling & Status Codes
- **401 Unauthorized:** Returned when the JWT is missing, expired, or invalid.
- **403 Forbidden:** Raised when the authenticated user lacks the required LDAP group for the requested reconciliation or activity feed.
- **404 Not Found:** Emitted when a reconciliation, run, or break ID does not exist or is not visible to the caller.
- **422 Unprocessable Entity:** Validation failures on comment, status, or bulk update payloads (e.g., empty comment, missing work for bulk update).
- **500 Internal Server Error:** Unexpected exceptions are logged with correlation IDs; check the activity feed and application logs for context.
