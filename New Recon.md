# Onboarding a New Reconciliation

This runbook explains how to introduce an additional reconciliation definition into the Universal Reconciliation Platform by extending the existing metadata-driven patterns.

## 1. Gather Business Inputs
- Confirm source systems, key identifiers, comparison fields, tolerances, and display metadata with operations stakeholders.
- Capture maker/checker requirements, required access scopes (product, sub-product, entity), and reporting expectations (matched/mismatched coverage, highlight rules).

## 2. Model the Metadata
- Define a unique reconciliation code, name, and description to represent the process in the UI.
- Classify each field as a key, comparison, display, or product hierarchy attribute, and record its data type and comparison logic (exact, case-insensitive, numeric threshold, date-only, etc.).
- Decide which fields belong in Excel exports, where highlight rules apply, and how the tabs should appear for matched, mismatched, and missing records.

## 3. Build an ETL Pipeline
1. Create a new class in `backend/src/main/java/com/universal/reconciliation/etl/` that extends `AbstractSampleEtlPipeline`.
2. Override `name()` for logging clarity and implement `run()` to:
   - Skip seeding if the definition already exists.
   - Construct the reconciliation definition via `definition(...)`, register fields with `field(...)`, and attach report templates using `template(...)` and `column(...)` helpers.
   - Persist access control entries (`entry(...)`) for each LDAP group, product, sub-product, and entity combination you want to expose.
   - Load Source A/B records by reading CSV resources through `readCsv(...)` and mapping rows into `SourceRecordA` and `SourceRecordB` entities.
3. Annotate the class with `@Component` and assign an `@Order` to control load sequencing relative to the existing sample pipelines.

## 4. Supply Data Files
- Place CSV extracts for Source A and Source B under `backend/src/main/resources/etl/<your-recon>/`.
- Ensure headers match the field names expected in the mapping logic; reuse helper methods `decimal(...)` and `date(...)` to normalise numeric and date values.

## 5. Register the Pipeline
- `SampleEtlRunner` automatically executes every `EtlPipeline` bean on startup, so no manual wiring is required once the class is annotated with `@Component`.
- Restarting the backend will invoke your pipeline and seed the metadata, access control entries, source records, and report templates.

## 6. Expose Access Scopes
- Update the maker and checker LDAP group names as required when creating `AccessControlEntry` instances in the pipeline.
- Adjust the product/sub-product/entity values to align with the business hierarchy so frontend filters work out of the box.

## 7. Validate End-to-End
1. Start the backend and confirm logs show your pipeline executing without errors.
2. Launch the frontend, authenticate, and verify the new reconciliation appears in the list for authorised groups.
3. Trigger a run and inspect run analytics, break inventory, filters, break workflow actions, and Excel export output.
4. Review the system activity feed to confirm run, comment, status, and export events reference the new reconciliation.

## 8. Document the Addition
- Add a summary of the new reconciliation to `docs/business/` and `docs/technical/` following the phase-based documentation pattern.
- Record any special operating procedures (file delivery timing, tolerances, escalation paths) in `docs/operations-guide.md` if needed.

## 9. Prepare for Production
- Swap CSV-based seeding for a production-grade ingestion mechanism (database integration, file transfer, streaming) while retaining the same metadata configuration model.
- Coordinate with identity management to create or reuse LDAP groups that mirror the maker/checker access matrix defined in the pipeline.
- Schedule automated reconciliation triggers (cron, API hooks, messaging) to align with upstream data availability once validated.
