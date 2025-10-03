# Admin Reconciliation Configurator Guide

This guide is the day-one handbook for platform administrators who need to design, publish, and
maintain reconciliations in the Universal Reconciliation Platform (URP). It explains the concepts
behind the tooling, provides a step-by-step walkthrough of the configurator, highlights validation
and testing features, and lists operational tips and troubleshooting advice so you can move from
zero to a production-ready reconciliation with confidence.

---

## 1. Audience & Prerequisites

**Who should read this?**

- Platform administrators responsible for onboarding or updating reconciliations.
- Business or operations SMEs partnering with admins on schema design and workflow governance.

**Before you begin**

- An LDAP identity that includes `ROLE_RECON_ADMIN`. The demo directory provides the
  `admin1/password` user with this role.
- A running URP stack. Locally, launch the backend (`./mvnw spring-boot:run` in `backend/`) and the
  Angular SPA (`npm start` in `frontend/`). For end-to-end automation, see
  `examples/integration-harness/README.md`.
- Familiarity with reconciliation concepts: anchor vs. compare sources, canonical schema design,
  maker/checker workflow, and ingestion lifecycles.

> **Tip:** Bookmark the [API Reference – Administration](./API-Reference.md#74-administration) for
> payload formats and the [Development Workflow](./Development-Workflow.md#81-onboarding-a-new-reconciliation-definition)
> for the broader delivery process.


---

## 2. Touring the Administration Workspace

1. **Login:** Visit the SPA, authenticate, then open the **Administration** workspace from the main
   navigation. Only admin users will see this tab.
2. **Catalog:** The landing page lists all reconciliations, displaying:
   - Lifecycle badge (Draft, Published, Retired)
   - Owner and last-updated timestamp
   - Maker/checker status
   - Quick filters (search, owner, date, lifecycle)
3. **Detail View:** Selecting a reconciliation opens an information panel with:
   - Schema summary and download link (`GET /api/admin/reconciliations/{id}/schema`)
   - Ingestion helper snippets (REST + curl examples)
   - Auto-trigger configuration overview
   - Recent ingestion batches and run summaries
   - Access control matrix (maker/checker/viewer groups)

The workspace is designed so that you can audit an existing reconciliation before editing—always
review current access control entries and ingestion history before making changes.

---

## 3. Quick Start Checklist

Use this checklist whenever you set up a new reconciliation:

1. ✅ Confirm admin access and the target LDAP groups for maker/checker roles.
2. ✅ Gather business requirements: sources, key fields, tolerances, break classifications.
3. ✅ Verify sample data availability for each source (CSV, database extracts, APIs).
4. ✅ Log into the Administration workspace and launch **New reconciliation**.
5. ✅ Complete the eight-step wizard (definition → sources → source schema → transformations → matching → reports → access → review).
6. ✅ Validate transformations (Groovy/Excel/pipeline) and preview results using sample data.
7. ✅ Publish the reconciliation and notify maker/checker teams.
8. ✅ Trigger an ingestion test using the CLI or API and confirm analytics in the results workspace.
9. ✅ Record the configuration in your runbook or change management tracker.

---

## 4. Wizard Walkthrough (Step by Step)

The **New reconciliation** wizard guides you through authoring. Each step saves progress locally so
you can pause and resume.

### 4.1 Definition

- **Code:** Unique identifier (uppercase, underscores) used across APIs and automation payloads.
- **Name & Description:** Analyst-facing metadata. Aim for concise, descriptive text.
- **Owner & Notes:** Free-form fields for accountability and implementation detail.
- **Maker/Checker:** Toggle to enable two-step approval. Required for regulated reconciliations.
- **Auto-trigger:** Optional cron schedule (timezone + grace window). Validation blocks incomplete
  schedules (e.g., cron missing while toggle is enabled).

### 4.2 Sources

- Add each data source with adapter type (CSV, JDBC, REST, S3, etc.). Adapter-specific options are
  stored as JSON and surfaced in the API.
- Selecting the **LLM_DOCUMENT** adapter unlocks a prompt-driven editor for unstructured sources.
  Configure the prompt template, optional JSON schema hints, model overrides, and record-path
  extraction so PDFs, emails, and other freeform payloads can be normalised via OpenAI without
  hand-authoring JSON.
- Mark at least one **anchor** source; the wizard enforces this.
- Configure arrival expectations (e.g., daily by 09:00 in New York). This metadata powers ingestion
  alerts and dashboards.

#### LLM ingestion adapter quick tips

- The wizard auto-generates adapter options JSON as you tweak the prompt, schema, and runtime
  parameters. Advanced admins can still adjust the JSON through the API or database when necessary.
- Use the `{{document}}` token for the extracted text and `{{schema}}` for the configured JSON
  schema. Documents are truncated using the backend `document-character-limit` setting before
  submitting to OpenAI, and a snippet is persisted in each record's `_llm` metadata.
- Responses may return arrays or objects; set **Record path** (dot-separated) to isolate the desired
  structure when the LLM wraps the payload.

### 4.3 Source Schema

- Define the raw columns that arrive from each source. Every source starts with a placeholder row—
  rename it immediately and select the appropriate data type (`STRING`, `DECIMAL`, `INTEGER`,
  `DATE`).
- **Infer schema from file** accepts the same formats as the transformation preview (CSV, delimited,
  Excel, JSON, XML). The backend analyses the sample, suggests column names, guesses data types, and
  shows the first few rows so you can validate before saving.
- Manual edits are fully supported: add/remove rows, toggle required flags, and capture descriptions
  documenting business meaning or upstream quirks.
- Saving the step persists `schemaFields` back onto each source. These persisted columns feed the
  transformation preview, matching dropdowns, and downstream docs—no more duplicate column lists.

### 4.4 Transformations

- The **Transformations** step sits between Source Schema and Matching and captures source-level
  preprocessing. Each source owns its own transformation plan so you can normalise data before it is
  mapped to canonical fields.
- A transformation plan is executed in three phases:
  1. **Dataset Groovy script** (optional) runs once per preview and can reshape or annotate the full
     list of rows. The editor now includes an **AI prompt** helper that sends the prompt, available
     column list, and sample row to the platform LLM so you can bootstrap scripts without leaving the
     configurator. Generated scripts are validated automatically before the preview refreshes.
  2. **Row operations** execute in order. Use filters to keep or exclude records, aggregations to
     roll up rows (sum, average, min/max, count, first/last), and split rules to explode delimited
     values into multiple rows.
  3. **Column operations** run after row processing. Combine fields into a new column, build
     function pipelines with the visual step editor, or round numeric values. Pipelines no longer
     require hand-authoring JSON—the UI serialises the step graph for you.
- Every input on this step now ships with a tooltip. Hover the information icon beside a field to
  see when the value is evaluated and how it affects the preview.
- Preview controls provide per-format parsing options:
  - CSV/Delimited files support header toggles, custom delimiters, encoding, **skip rows**, and row
    limits.
  - Excel uploads can refresh the workbook to list available sheets, select one or many tabs, and
    optionally append the sheet name to each previewed row via a custom column.
  - JSON/XML uploads expose record-path navigation so you can extract the correct array without
    editing the payload.
  - All formats let you persist encoding and row limits so previews mirror production ingestion.
- The wizard caches discovered columns back onto the source definition as soon as you preview data.
  Those columns populate the dropdowns in both the Source Schema and Matching steps, keeping the
  flow inline.
- **Preview workflow:** upload a sample file (CSV, delimited text, Excel, JSON, XML) or load the
  latest ingested rows. Apply the plan at any time to compare the raw dataset and the transformed
  output side-by-side. The Groovy transformation tester picks up these preview rows so you can run
  expressions against real data before publishing.

### 4.5 Matching Rules

- Review the columns surfaced for each source after transformations. The configurator auto-populates
  available fields so you can map anchor and secondary sources quickly.
- Define canonical fields in this view—set role (`KEY`, `COMPARE`, `CLASSIFIER`, etc.), data type,
  thresholds, and match type. Display names and formatting hints appear in analytics and reports.
- Pick a match type for every canonical field: full match, case-insensitive, numeric threshold, date
  match, or display-only context. Thresholds are captured as percentages; display-only fields are
  excluded from the matching engine but remain visible to analysts.
- For date comparisons, capture the incoming format per source and the normalized format that should
  be written into the canonical payload. These settings are applied automatically during ingestion so
  the matching engine always evaluates ISO-aligned dates.
- Map each canonical field to source columns. For multi-source reconciliations, the same canonical
  field can have different transformation chains per source. Field-level transformations are now
  authored exclusively in the Transformations step and previewed there.
- Use **Validate** to compile transformations immediately via the backend validation API. Errors
  display inline with actionable messages, and the Groovy assistant pulls from the previewed sample
  rows populated in the transformations step.

### 4.6 Reports (Optional)

- Configure export templates: column order, highlighting, inclusion of matched/mismatched/missing
  records, and file naming conventions.
- Templates map directly to report jobs defined in the automation suite.

### 4.7 Access

- Assign LDAP groups as **Maker**, **Checker**, or **Viewer**. You can scope entries by product,
  sub-product, and entity.
- Configure notification preferences for publish events or ingestion failures. URP’s notification
  bridge handles channel delivery (email, Slack, etc.).

### 4.8 Review & Publish

- Inspect the generated summary (definition metadata, sources, schema counts, access matrix).
- Choose **Save draft** to revisit later or **Publish** to make the reconciliation available to
  analysts and automation. Publishing triggers audit logging under the admin’s identity.

---

## 5. Transformation Authoring Deep Dive

URP supports three transformation modes. You can combine them within the same mapping.

### 5.1 Groovy (Multi-block)

- Scripts can include multiple statements and helper functions. Example:

```groovy
def amount = (value ?: 0G).setScale(2)
if (row['feeFlag'] == 'Y') {
  return amount * 1.02G
}
// mutate the bound value instead of returning
value = amount
```

- **Binding variables:** `value` (current field), `row`/`raw` (full source payload as `Map`).
- **Validation:** *Validate script* checks compilation, sandbox restrictions, and disallows illegal
  imports.
- **Testing:** Use *Load sample rows* + *Run test* to execute against live data. Results include
  returned value and mutated `value` bindings.
- **Best practices:**
  - Keep deterministic logic. Avoid `new Date()` or external calls.
  - Use `BigDecimal` for currency math to prevent floating-point drift.
  - Log complex cases via comments and document in the reconciliation notes.

#### AI-assisted Groovy authoring

- Open the **Describe transformation** assistant inside either the dataset Groovy editor (on the
  Transformations step) or a field-level Groovy editor (on the Schema/Matching step) to capture the
  business requirement in plain language.
- Load a sample row before requesting a script so the LLM receives the current value (`value`), raw
  source payload (`row`/`raw`), and the list of available columns. Dataset prompts also forward the
  entire previewed row list so cross-record operations can be suggested.
- The platform injects Groovy sandbox rules (no imports, bindings only) and posts the request to the
  configured OpenAI endpoint.
- The returned script is written directly into the editor and a short summary is displayed beneath
  the assistant. Always run **Validate** and (for field-level scripts) **Run Groovy test** afterwards
  to confirm the behaviour.
- If OpenAI credentials are missing or the call fails, the assistant surfaces the error and preserves
  the prompt so you can retry after adjusting settings.

### 5.2 Excel-style Formulas

- Syntax mirrors Excel/Google Sheets functions. Named ranges are generated automatically:
  - `VALUE` references the canonical input
  - Each source column becomes an uppercase, underscore-separated name
- Example: `IF(ISBLANK(VALUE), RAW_AMOUNT * 1.1, VALUE)`
- **Sanitisation:** The evaluator guards against invalid names (reserved keywords, cell references).
- **Validation:** Use the inline check to ensure the formula compiles via Apache POI.

### 5.3 Function Pipeline Builder

- Compose pre-built operations through the UI. Common functions:
  - `trim`, `uppercase`, `lowercase`
  - `replace(pattern, replacement)`
  - `parseDate(format, targetFormat)`
- Each step now exposes its arguments inline—add, remove, or reorder steps without editing raw JSON.
  The configurator serialises the pipeline to the required configuration string automatically.
- Pipelines run in order; you can rearrange or disable individual steps. Use this mode for common
  cleanup tasks when scripting is overkill.

### 5.4 When to Use What

| Scenario | Recommended Mode |
| --- | --- |
| Simple text/number normalisation | Function pipeline |
| Complex calculations, conditionals, reference data lookup | Groovy |
| Business users replicating spreadsheet logic | Excel formula |

---

## 6. Validation, Testing & Previewing

### 6.1 Inline Validation

- Each transformation exposes a **Validate** button. Fix reported errors before saving the schema.
- The wizard blocks progression if any transformation fails validation.

### 6.2 Sample Data Preview

- The **Transformations** step owns previewing. Every source exposes the same two workflows:
  - **Upload sample file:** Drop a CSV, delimited text, Excel workbook, JSON document, or XML payload
    and provide parsing hints (headers, delimiter, sheet selection, record path, encoding, skip rows,
    row limit). The wizard runs the dataset script, row operations, and column operations and renders
    a side-by-side JSON view of raw versus transformed rows. The default upload limit is 2 MiB and
    can be tuned via `admin.transformations.preview.max-upload-bytes`.
  - **Load live samples:** After the first ingestion succeeds, use *Load recent rows* to fetch the
    latest persisted batch. The preview panel marks which rows were used so you can cross-check
    production inputs ahead of canonical mapping changes.
- Groovy testers pull from the most recent preview dataset, allowing you to exercise field-level
  scripts with exactly the same input that the plan produced.
- Use previews liberally; they are lightweight and do not mutate stored batches. Once you are
  satisfied with the plan, move to the Schema step to wire canonical mappings on top of the
  normalised output.

### 6.3 Harness & Automation Support

- The **Harness Debug Controller** (`/api/harness/breaks/{id}` under `example-harness` profile) lets
  automation run the same validation programmatically—handy for regression suites.
- Playwright regression (`automation/regression/tests/end-to-end.spec.ts`) walks through the entire
  configurator, submits a Groovy-backed reconciliation, ingests data, and steps through maker/checker
  approvals while capturing screenshots. Use it as a reference for expected UI flows.

---

## 7. Publishing & Governance

- **Lifecycle states:**
  - **Draft:** Editable, hidden from analysts.
  - **Published:** Active; analysts can run, makers/checkers receive work.
  - **Retired:** Preserved for audit; ingestion disabled.
- **Versioning:** URP uses optimistic locking. If another admin updates the reconciliation while you
  edit, the UI surfaces a version conflict—refresh and merge changes manually.
- **Maker/Checker flow:** Ensure the LDAP groups you assign have active users. Use the admin detail
  view to verify ledger of permissions. Playwright and the harness scripts simulate maker/checker
  transitions to catch misconfigurations early.
- **Audit trail:** All create/update/publish actions are logged with timestamps and admin identities.
  Reference them via the audit API when preparing compliance reports.

---

## 8. Operating Your Reconciliation

1. **Ingestion:** Use the provided curl snippets or the integration CLI to submit batches. Tag each
   batch with a descriptive label so operational dashboards stay readable.
2. **Monitoring:** The detail view shows the latest 20 ingestion jobs and recent run summaries.
3. **Run Analytics:** After ingestion, analysts can open the reconciliation in the main workspace to
  review matches, mismatches, and missing items. Maker/checker actions happen directly inside the
  inline break detail panel.
4. **Automation:** CI pipelines should run:
   - Backend tests: `./mvnw -B test`
   - Frontend tests: `npm test -- --watch=false --browsers=ChromeHeadless`
   - Playwright suite: `npm test` (in `automation/regression`)
   - Integration harness: `examples/integration-harness/scripts/run_multi_example_e2e.sh`
  - Seed scripts:
    - `./scripts/local-dev.sh bootstrap|seed`
    - `./scripts/seed-historical.sh --days 3 --runs-per-day 1 --report-format NONE --ci-mode`
    - `./scripts/verify-historical-seed.sh --days 3 --runs-per-day 1 --skip-export-check`

Keep automation outputs for audit evidence when rolling out new configurations.

---

## 9. Troubleshooting & FAQs

| Symptom | Likely Cause | Resolution |
| --- | --- | --- |
| Wizard refuses to progress from Schema step | Validation errors on transformations | Click the **Validate** badge on each mapping; fix syntax or missing fields. |
| Groovy script returns original value | No explicit return and `value` binding unchanged | Return the new value or mutate `value` inside the script. |
| Excel formula errors about invalid name | Field name sanitises to reserved keyword/cell reference | Adjust source column name or prefix with an underscore. |
| Maker cannot submit break | Maker LDAP group missing from access step | Update Access step and republish; verify with harness debug endpoint. |
| Automation Playwright test fails to find detail panel | Inline detail grid not expanded | Use updated selectors (`.inline-break-detail .break-detail-view`) and click expand button. |
| Ingestion CLI reports 403 | Token lacks admin scope or reconciliation is Draft | Ensure admin token, confirm status is Published, and maker/checker groups are set. |

**Need more help?**

- Review `docs/wiki/API-Reference.md` for request/response formats.
- Explore `examples/admin-configurator/payloads/` for end-to-end payload examples.
- Ping the #reconciliation-admins channel (or your organisation’s equivalent) with audit logs and
  reproduction steps.

---

## 10. Related Resources

- [API Reference – Administration](./API-Reference.md#74-administration)
- [Integration Harness Guide](../examples/integration-harness/README.md)
- [Admin Configurator Example Payloads](../examples/admin-configurator/README.md)
- [Development Workflow & Change Controls](./Development-Workflow.md#81-onboarding-a-new-reconciliation-definition)

Keep this guide bookmarked. Update it whenever new transformation types or governance controls are
introduced so new administrators always have an up-to-date, authoritative reference.
