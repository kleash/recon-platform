# Feature Request: Enhanced Maker-Checker Workflow

## Summary
Maker-checker support exists across the platform, but current behaviour allows self-approval, lacks rejection handling, and exposes limited insight to end users. This feature request consolidates enforcement fixes and functional enhancements for both backend and frontend to deliver an auditable, role-aware approval experience.

## Current Pain Points
- Security gaps allow users mapped to both maker and checker groups to bypass dual-control by self-approving submitted breaks.
- Checkers cannot reject a submission; the only options are approving (closing) or reopening a break manually.
- Bulk operations only support status changes to a single state without differentiating maker vs. checker actions.
- Audit data is scattered across comments and system activity, making it difficult to reconstruct who approved or rejected a break.
- Frontend UI shows generic status buttons, lacks history context, and surfaces errors poorly for read-only users.
- No dedicated queue or notifications for checkers to triage pending approvals.

## Proposed Enhancements

### Backend
1. **Enforce Separation of Duties**
   - Record the submitting maker (user DN + group) on each break when transitioning to `PENDING_APPROVAL`.
   - Reject approval attempts from the same actor or any user session lacking an exclusive checker role.
2. **Introduce `REJECTED` Status**
   - Extend `BreakStatus` enum and persistence to support a `REJECTED` state.
   - Allow checkers to transition `PENDING_APPROVAL → REJECTED`, automatically routing the break back to makers (and optionally reopening when the maker resubmits).
3. **Comprehensive Audit Trail**
   - Add a dedicated audit entity (e.g., `BreakWorkflowAudit`) that captures actor DN, resolved role (maker/checker), previous status, new status, comment/justification, timestamp, and correlation ID for bulk actions.
   - Emit audit entries for single and bulk transitions, and surface them via API.
4. **Bulk Action Expansion**
   - Extend `BulkBreakUpdateRequest` to support separate maker (`SUBMIT_FOR_APPROVAL`) and checker (`APPROVE`, `REJECT`) intents.
   - Apply per-break eligibility checks and collect successes/failures for clear feedback.
5. **API & Error Responses**
   - Replace raw `SecurityException` throws with `AccessDeniedException` mapped to HTTP 403.
   - Return structured error payloads that include failure reasons for bulk operations.

### Frontend
1. **Dedicated Maker-Checker Controls**
   - Replace generic status buttons with explicit `Submit for Approval`, `Approve`, and `Reject` actions, conditioned by the current user’s role for the break.
   - Require a comment when performing approval or rejection to align with audit requirements.
   - Dynamic filter on any column in table for user to easily filter data to easily do bulk changes
   - Break ui should show reconcillation fields with filters for easy comparison
2. **Break History Panel**
   - Display a chronological workflow history that combines comments and the new audit trail, highlighting maker vs. checker actions and any rejections.
3. **Checker Queue View**
   - Create a `Pending Approvals` dashboard summarising counts by reconciliation and providing filters for product/sub-product/entity.
   - Support bulk approval/rejection directly from the queue with clear summaries of outcomes.
4. **Role-Aware UX and Errors**
   - Hide comment forms and action buttons for users lacking maker/checker roles, replacing them with informative messaging.
   - Surface API authorization failures through toast/dialog notifications with actionable guidance.
5. **Notifications**
   - Trigger in-app alerts (and optionally email/webhook hooks) when new breaks are submitted for approval, when a break is rejected, or when bulk actions complete.

### Cross-Cutting
- Update documentation (`docs/wiki`) detailing maker/checker flows, role assignment requirements, and new API endpoints.
- Add automated tests: backend service/unit tests for new transitions and audit logging; end-to-end UI tests covering maker submission, checker approval/rejection, and bulk flows.
- Extend automation regression suite under automation and cover various workflow test cases with screenshot.

## Acceptance Criteria
- Makers cannot approve their own submissions; self-approval attempts return HTTP 403 with a descriptive message.
- Checkers can approve or reject pending breaks individually or in bulk, with results reflected in break status and audit history.
- Every workflow action generates an immutable audit entry viewable via API and UI history.
- Frontend provides clear role-based controls and a dedicated checker queue for `PENDING_APPROVAL` (and optional `REJECTED`) items.
- Users receive notifications for assignments and status changes, and bulk operations return per-item results.

## FAQ
- Should `REJECTED` automatically revert to `OPEN`, or remain distinct until the maker resubmits?
A: YES
- What delivery channels (email, chat, webhook) are required for notifications beyond in-app alerts?
A: In-app alert is sufficient
- Do bulk operations need per-item comments, or is a shared justification acceptable?
A: shared justification is enough
- Are there regulatory retention requirements for the workflow audit that affect storage design?
A: no
