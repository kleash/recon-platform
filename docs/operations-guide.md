# Operations & Runbook Guide

This guide explains how to deploy, operate, and use the Universal Reconciliation Platform in a lower environment.

## System overview
- **Backend**: Spring Boot application exposing REST APIs at `/api`. Uses in-memory H2 for demo data.
- **Frontend**: Angular SPA served via `npm start` in development; build artefacts can be hosted on any static server in production.
- **Authentication**: Demo implementation uses an in-memory session service with mock login.

## Starting the platform
1. **Backend**
   - Navigate to `backend/` and run `./mvnw spring-boot:run`.
   - Logs stream to the console; the app listens on `http://localhost:8080`.
   - Health check: `curl http://localhost:8080/actuator/health` should return `UP`.
2. **Frontend**
   - Navigate to `frontend/` and run `npm start`.
   - The Angular dev server listens on `http://localhost:4200` and proxies API requests to the backend URL defined in `src/environments/environment.ts`.

## Using the application
1. Browse to `http://localhost:4200`.
2. Authenticate with any credentials (demo login accepts any username/password).
3. Select a reconciliation definition from the left panel.
4. Trigger a run using the configuration form. Optional metadata:
   - **Trigger type**: manual, external API, scheduled cron, or Kafka event.
   - **Correlation ID**: tracking identifier stored with the run.
   - **Initiated by**: override the actor shown in audit trail.
   - **Comments**: context stored on the run summary.
5. Review run summary, analytics, and break inventory.
6. Apply filters (product, sub-product, entity, status) to focus on subsets of breaks.
7. Select breaks to perform bulk updates (status change and/or comment) or open an individual break to add commentary / transition workflow.
8. Export the latest run to Excel using the button in the trigger configuration card.
9. Monitor activity feed for audit events such as runs, comments, status changes, bulk actions, and exports.

## Maintenance tasks
- **Data refresh**: Restart the backend to reload seed data from `data.sql` (resets all runtime changes).
- **Report templates**: Modify `report_templates` and `report_columns` in the database to adjust export layout; restart backend or rerun migrations.
- **User access**: Update `access_control_entries` table to change maker/checker assignments.

## Operational monitoring
- Inspect backend logs for matching errors or Excel export issues (`ExportService` logs serialization failures).
- Use Spring Actuator endpoints (if enabled) for metrics and health.
- Frontend console logs highlight API errors surfaced via the state service.

## Backup & recovery (demo)
- Export Excel reports as ad-hoc backups of reconciliation results.
- Persisted data resides in H2 (in-memory); for production, configure an external database and schedule backups accordingly.

## Troubleshooting
| Symptom | Resolution |
| --- | --- |
| Frontend cannot reach API | Confirm backend is running on `localhost:8080` and proxy configuration matches. |
| Excel export fails | Check backend logs for serialization errors and ensure report templates exist. |
| Bulk update rejected | Ensure request includes at least a status change or comment; UI enforces this but API will return validation error otherwise. |
| Filters return no data | Review access control entries; filters are intersected with allowed scope. |

For escalations, capture backend logs, frontend console output, and the steps to reproduce before contacting the engineering team.
