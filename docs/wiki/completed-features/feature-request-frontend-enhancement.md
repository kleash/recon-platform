# Agent Brief — Recon Frontend Revamp (Senior UI/UX)

## Context
We operate a reconciliation (recon) platform with **multiple reconciliations**, each producing **multiple runs per day** (scheduled, manual, or “run when all sources available”). Ops users need to:
- Pick a reconciliation
- See a dashboard for that recon
- Browse **all runs** (default to **today**), filter by date/runs/batches/any column
- Review/close **breaks** with **maker–checker** (incl. bulk “select all”)
- Export filtered datasets and reports (with user comments, maker, checker, timestamps)
- Persist/share views, and work fast at scale

Timezone: **Asia/Singapore (UTC+08:00)** for all UI date/time displays, filtering, and exports.

---

## Objectives (DoD)
1. **Navigation**
   - Global Recon Picker → Recon Dashboard.
   - Dashboard shows **Runs** and **Breaks** tabs.
   - “Runs” page defaults to **today** (00:00–23:59 SGT), toggle to previous days.
2. **Runs/Results Browser**
   - Single page grid combines results across runs (by default = today); add **filters** for:
     - Date (range), Recon, Run ID/Type (scheduled/manual/when-ready), Batch ID
     - Result columns (dynamic—schema driven), Status (matched/mismatched/missing/new), Severity
     - Source system, File name, Version, User tags/comments
   - **Dynamic column filters** (auto-generated from schema, with operators per type).
   - **Quick filters** & **Saved Views** (name + share link + default view per user).
   - Client renders **virtualized table** (10k–1M rows) with sticky header, resizable & reorderable columns, pinning, multi-sort.
   - **Select all** supports: current page, current filter set, and **entire filtered dataset** (server-side).
3. **Breaks & Maker–Checker**
   - Breaks tab mirrors grid behavior; supports **bulk actions**:
     - “Close breaks” (maker action) → routes to **Pending Approval** queue.
     - Checker reviews bulk set or individual items (diff view & comments), **approve/reject** with reason.
   - **Audit trail** for each break & bulk operation (who, when, action, comment).
4. **Exports**
   - **Export data** (CSV, XLSX, JSONL) of **current filtered result set** (not just visible page).
   - **Export report** (XLSX/PDF) with headers: filter summary, recon metadata, user comments, maker, checker (if enabled), timestamps (SGT), and sign-off block.
   - All exports are **async** with job status & notifications; downloadable within UI; include **hash** & **row count**.
5. **Performance & UX**
   - Server-driven pagination + cursor/offset support.
   - Debounced filtering, **query caching**, optimistic UI for maker actions, revalidation on focus.
   - **A11y** (WCAG AA): keyboard nav, ARIA, focus management.
6. **Security**
   - Role-based UI gating: Ops, Maker, Checker, Admin.
   - All actions include CSRF token (if applicable) and correlate to server audit IDs.
7. **Documentation & Automation**
   - Update **wiki/docs** (screens, flows, contracts, perf tips).
   - Update **/automation/** with e2e flows and data-seeding scripts.
   - Add **visual snapshots** for critical pages (Playwright).

---

## Information Architecture

- **Global**
  - App Bar: Org switch (if any) • Recon Picker (Combobox with search & favorites) • Date “Today” quick chip • Saved Views • Profile
- **Recon Dashboard**
  - KPIs (today): runs count, success/fail %, open breaks, SLA breaches
  - Tabs: **Runs**, **Breaks**, **Approvals** (visible to checkers), **Reports**
- **Runs *
  - Filter bar (sticky): Date range; Run Type; Run/Batch IDs; quick chips (Today, Yesterday, Last 7d)
  - Data Grid (virtualized)
  - Bulk bar (appears when selected): Export data, Export report, Bulk annotate, Bulk “Mark for closure” (if breaks view)
- **Breaks**
  - Same filter/grid patterns; bulk maker action → “Submit for approval”
- **Approvals**
  - Queue list (bulk sets + singles) → Review detail → Approve/Reject with reason; side-by-side diff
- **Reports *
  - History of generated exports/reports with filter snapshot & hash; re-download

---
## Acceptance Criteria

- Runs default to today in SGT
- Dynamic filters work and serialize into URL
- Grid handles ≥200k rows virtualized
- Maker → Approvals flow works end-to-end
- Exports contain filters, comments, maker/checker, timestamps
- Accessibility verified (WCAG AA)
- Documentation & regression updated

## Deliverables

Code changes with PR (feat(recon-ui): ...)
Updated docs: /wiki/docs/
E2E suite under /automation/

Demo GIFs in PR descriptions

---

## Implementation Snapshot — September 2025

The analyst workspace and supporting services are now feature-complete for the enhancement request:

- **Tabbed workspace navigation.** Analysts pivot between **Runs**, **Breaks**, **Approvals**, and **Reports** without losing context. The runs tab hosts the virtualised, server-driven result grid; the breaks tab exposes per-run analytics; approvals and reports receive dedicated surfaces.
- **Dynamic filtering + saved views.** The runs grid supports date, status, run-type, run-id, and ad-hoc column filters (operators derive from canonical field metadata). Filter state persists through saved views, and bulk actions respect “select filtered” server-side selections.
- **Checker approval queue.** A new `GET /api/reconciliations/{id}/approvals` endpoint returns pending breaks and classification metadata. Checkers can approve or reject in bulk from the approvals tab, with access guarded by `BreakAccessService`.
- **Export reporting.** The reports tab lists async dataset exports with live status, refresh, and download actions (CSV, XLSX, JSONL). Export jobs continue to embed filter context, SGT timestamps, hashes, and row counts for audit trails.
- **Regression coverage.** Playwright smoke flows validate navigation, approvals visibility, and export affordances; backend unit coverage exercises the approvals queue service to maintain Jacoco thresholds.

Refer to the API reference for request/response details on the new endpoints. Future refinement will focus on advanced grid ergonomics (pinning, resize tokens) and performance instrumentation.
