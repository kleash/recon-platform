# Maker-Checker Enhancements

The maker-checker workflow now enforces separation of duties, introduces rejection handling, and surfaces a richer user
experience across the platform. Use this page as the canonical reference for working with the enhanced approvals process.

## Workflow Highlights

- **New statuses** – Breaks can move between `OPEN`, `PENDING_APPROVAL`, `REJECTED`, and `CLOSED`. Checkers can reject a
  submission, routing it back to makers with a mandatory comment.
- **Submission tracking** – Each break records the submitting maker’s directory name, group, and timestamp so checkers can
  see who initiated the request and self-approval attempts are blocked automatically.
- **Audit trail** – Every maker and checker action is persisted as an immutable audit record capturing actor, role, previous
  status, new status, comment, correlation ID, and timestamp. Free-form comments remain available for collaborative notes.
- **Bulk parity** – Bulk operations now return structured successes and failures, enforce per-break eligibility, and honour
  comment requirements for approvals and rejections.

## Backend Changes

- `BreakStatus` includes `REJECTED` and the database records the submitting actor metadata on `BreakItem`.
- `BreakWorkflowAudit` stores the canonical workflow audit trail. APIs expose history alongside existing comments.
- `BreakAccessService` normalises dual-role sessions (maker + checker) to maker-only and throws `AccessDeniedException`
  when permissions are missing, ensuring HTTP 403 responses.
- `BreakService` validates comments for approvals/rejections, blocks self-approval, and publishes aggregated results for
  bulk requests. Consumers receive per-item error messages without losing successful updates.

## Frontend Changes

- **Break detail** – Dedicated maker/checker controls replace the generic status buttons. Approvals and rejections require a
  justification, and the workflow history merges audit entries with manual comments for full traceability. View-only users
  see informative messaging instead of disabled inputs.
- **Run detail** – A quick filter enables ad-hoc searching across break attributes. Bulk actions display validation feedback
  when required comments are missing or no work is selected.
- **Checker queue** – A new dashboard lists all `PENDING_APPROVAL` breaks with product, sub-product, and entity filters. Checkers
  can approve or reject multiple items with a shared comment directly from the queue.
- **Notifications** – Maker-checker actions now surface toast notifications for success and failure, giving users immediate
  feedback when permissions are missing or bulk operations encounter issues.

## Automation & Correlation

All workflow calls accept an optional `correlationId`. The UI automatically generates IDs for bulk actions so downstream
systems can cross-reference audit entries with user operations. When integrating with the API directly, supply a descriptive
identifier (e.g., `queue-approve-<timestamp>`) to simplify monitoring.

## Related Documentation

- [Feature Request: Enhanced Maker-Checker Workflow](./feature-request-maker-checker-enhancements.md)
- [Run Detail Analytics](./run-detail.md)

