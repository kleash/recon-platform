# Troubleshooting

## Schema inference fails for uploaded files
- **Symptom**: Admin wizard displays "Unable to infer schema from the uploaded file".
- **Checklist**:
  - Confirm the selected file size is below `admin.transformations.preview.max-upload-bytes` (default 2 MiB).
  - Ensure the chosen file type matches the payload (CSV vs Excel vs JSON/XML) and that delimiter/header options align.
  - For Excel workbooks, refresh sheet metadata after uploading to populate the sheet selector.
- **Diagnostics**: Backend logs include `TransformationEvaluationException` entries with the root cause. Re-run with
  `DEBUG` logging on `AdminSourceSchemaService` for additional context.

## Schema fields missing in matching dropdowns
- Verify the schema step fields were saved (wizard review page lists schema counts per source).
- Check that transformations preview succeeded; available columns combine schema fields with derived columns.
- When editing existing reconciliations, the Sources tab must be saved before schema changes propagate to matching.

## Excel ingestion returns CSV adapter errors
- **Symptom**: Admin ingestion endpoint rejects workbook uploads with `adapterType=CSV_FILE` or "Unsupported file type" errors.
- **Checklist**:
  - Ensure the ingestion metadata's `adapterType` is `EXCEL_FILE`. The ingestion CLI derives this automatically when the
    batch media type is `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
  - Supply the same parsing options used in preview (`hasHeader`, `includeAllSheets`, `sheetNameColumn`) to guarantee
    sheet selection matches the admin configuration.
  - Confirm the workbook size is within the ingestion upload limit and that protected sheets have been unencrypted.
- **Diagnostics**: Enable `DEBUG` logging for `ExcelIngestionAdapter` to trace sheet discovery and row extraction.

## Transformation plan unexpectedly drops rows
- **Symptom**: After seeding `GLOBAL_MULTI_ASSET_HISTORY`, one or more sources ingest fewer rows than the raw fixture.
- **Checklist**:
  - Review the transformation plan filters in the admin workspace; the showcase now includes confidence thresholds and
    status filters per source that intentionally remove stale records.
  - Adjust filter thresholds in the payload or via the admin UI if a fixture needs to retain additional rows (e.g.,
    lower `amer_confidence_tag` minimum from `0.8` to `0.7`).
  - Confirm dataset Groovy scripts do not throw exceptions—logs emit `TransformationEvaluationException` if parsing or
    string manipulation fails.
- **Diagnostics**: Use the admin transformation preview with the fixture sample to see which rows survive the applied
  filters before running a full ingestion.

## Angular CLI reports unsupported Node or TypeScript warnings
- **Symptom**: `ng version` / `ng update` warn about unsupported Node 24.x or emit `TypeScript compiler options 'module'` warnings.
- **Checklist**:
  - Install Node 20 via Homebrew (`brew install node@20`) and run Angular commands with `PATH="/opt/homebrew/opt/node@20/bin:$PATH"` to meet the Angular 20 support matrix.
  - When the CLI forces `module`/`target` to `ES2022`, review project `browserslist`. No action is needed unless custom
    tsconfig overrides reintroduce incompatible values.
- **Diagnostics**: `npx ng version` confirms the effective Node + Angular versions after the PATH override.

## `seed-historical.sh` exits with `fail: command not found`
- **Symptom**: Running the historical seed script immediately aborts with `fail: command not found` on macOS.
- **Checklist**:
  - The default `/bin/bash` (v3.2) cannot pass the script's version check. Prefix the command with
    `PATH="/opt/homebrew/opt/bash/bin:$PATH"` so Bash ≥ 4 is used.
  - Ensure `python3`, `curl`, and `jq` are installed—the script validates their presence with `require_command`.
- **Diagnostics**: Re-run with `bash -x` for detailed tracing once the newer interpreter is on PATH.

## Unable to reconcile reconciliation run activity with logs
- **Symptom**: Analysts cannot confirm which user triggered a run or bulk break update from log output alone.
- **Checklist**:
  - Ensure backend logging level includes INFO; `ReconciliationService` logs each run with definition code, trigger type, and correlation ID.
  - For bulk operations, search for `Break bulk update summary` and match the correlation ID from the UI toast.
  - Enable DEBUG logging for `com.universal.reconciliation.service.matching.DynamicMatchingEngine` when investigating unexpected match counts.
- **Diagnostics**: Cross-reference the correlation ID from the log with `system_activity` entries to verify the event pipeline end-to-end.
