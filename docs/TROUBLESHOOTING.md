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
