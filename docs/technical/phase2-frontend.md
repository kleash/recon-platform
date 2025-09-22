# Phase 2 Frontend Technical Reference

Each section documents every line (or contiguous block) touched in Phase 2 of the Angular application.

## `src/app/models/api-models.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import the shared `BreakStatus` enum for type-safe status usage. |
| 3-7 | Define the `LoginResponse` interface unchanged from Phase 1. |
| 9-14 | Define `ReconciliationListItem` metadata returned by the backend. |
| 16-22 | Define `ReconciliationSummary` including optional run identifiers. |
| 24-30 | Define `BreakComment` timeline entries. |
| 32-44 | Expand `BreakItem` with dimensional attributes, allowed transitions, payload maps, and comment arrays. |
| 46-51 | Define `FilterMetadata` representing available filter facets. |
| 53-57 | Define `RunDetail` bundling summary, breaks, and filters. |
| 59-64 | Define `SystemActivityEntry` items for the dashboard feed. |

## `src/app/models/break-filter.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import the `BreakStatus` enum so the filter can reference valid values. |
| 3-7 | Declare the optional `BreakFilter` interface used throughout the UI. |

## `src/app/models/break-status.ts`
| Lines | Explanation |
| --- | --- |
| 1-5 | Enumerate the string literal values used for break workflow statuses. |

## `src/app/services/api.service.ts`
| Lines | Explanation |
| --- | --- |
| 1-3 | Import Angular service, HTTP client, and query parameter utilities. |
| 4-10 | Import model interfaces consumed by API calls. |
| 11 | Import `BreakStatus` enum for status updates. |
| 12 | Import environment configuration for the API base URL. |
| 13 | Import `BreakFilter` helper interface. |
| 15 | Define the base URL constant. |
| 17-18 | Register the service and declare constructor injection for `HttpClient`. |
| 21-22 | `login` posts credentials and returns a `LoginResponse`. |
| 25-26 | `getReconciliations` retrieves accessible definitions. |
| 29-31 | `triggerRun` posts to the manual execution endpoint. |
| 33-37 | `getLatestRun` fetches the newest run with optional filter parameters. |
| 39-43 | `getRun` retrieves a historic run with identical filter support. |
| 45-47 | `addComment` posts comment payloads to the backend. |
| 49-51 | `updateStatus` patches a break status with the supplied enum. |
| 53-55 | `exportRun` downloads the Excel export as a `Blob`. |
| 57-59 | `getSystemActivity` pulls the activity feed. |
| 61-81 | `buildFilterParams` converts optional filter values into HTTP query parameters, appending multiple statuses when provided. |

## `src/app/services/reconciliation-state.service.ts`
| Lines | Explanation |
| --- | --- |
| 1-2 | Import Angular injection decorator and RxJS primitives. |
| 3 | Import `ApiService`. |
| 4-12 | Import model interfaces, enums, and filter definition. |
| 14 | Register service as root provider. |
| 15-22 | Initialise behaviour subjects for reconciliations, selection, run detail, selected break, filter, filter metadata, and activity feed. |
| 24-30 | Expose read-only observables for each subject. |
| 32 | Constructor storing the API dependency. |
| 34-54 | `loadReconciliations` resets the filter, loads reconciliations, maintains selection when possible, fetches latest run, and refreshes activity. |
| 56-60 | `selectReconciliation` updates selection and loads the latest run. |
| 62-71 | `triggerRun` invokes the backend, refreshes run detail, and reloads activity. |
| 73-75 | `selectBreak` updates the active break observable. |
| 77-82 | `addComment` delegates to the API, updates the local break record, and refreshes activity. |
| 84-89 | `updateStatus` performs the same flow for status changes. |
| 91-97 | `exportLatestRun` guards against missing run IDs and proxies the API observable. |
| 99-107 | `reset` clears all cached subjects. |
| 109-111 | `getCurrentRunDetail` accessor. |
| 113-119 | `updateFilter` pushes filter changes and reloads the latest run. |
| 121-123 | `getCurrentFilter` accessor. |
| 125-132 | `fetchLatestRun` helper loads the latest run with the current filter, updates subjects, and auto-selects the first break. |
| 134-145 | `updateBreak` replaces the cached break entry and keeps the selected break in sync. |
| 147-150 | `refreshActivity` fetches the most recent activity entries. |

## `src/app/app.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-10 | Import Angular utilities, RxJS helpers, and application services/models. |
| 11-15 | Import Phase 2 UI components. |
| 17-31 | Annotate the root standalone component and declare imported feature components. |
| 32-41 | Declare observable bindings for reconciliations, selection, run detail, selected break, filters, metadata, and activity. |
| 43-45 | Track login error and loading state. |
| 46 | Create destroy subject for subscription teardown. |
| 48-52 | Constructor injection of API, session, and state services. |
| 54-57 | `ngOnInit` loads reconciliations when already authenticated. |
| 60-63 | `ngOnDestroy` completes the teardown subject. |
| 65-82 | `handleLogin` performs authentication with loading/error handling and refreshes state on success. |
| 84-88 | `logout` clears session data and resets state. |
| 90-92 | `handleSelectReconciliation` delegates to the state service. |
| 94-96 | `handleTriggerRun` executes a manual run through the state service. |
| 98-100 | `handleSelectBreak` updates the active break. |
| 102-104 | `handleAddComment` forwards comment submissions. |
| 106-108 | `handleStatusChange` forwards status updates. |
| 110-112 | `handleFilterChange` updates the active filter. |
| 114-131 | `handleExportRun` fetches the latest run export, creates a download link, and cleans up the object URL. |

## `src/app/app.component.html`
* Lines 1-8 – Render the global header with welcome banner and logout control.
* Lines 10-15 – Include the login component when unauthenticated.
* Lines 17-46 – Lay out the authenticated workspace using a grid with the reconciliation list, run detail card, break detail card, and activity feed.
* Lines 18-25 – Configure the reconciliation list bindings and event handlers.
* Lines 27-40 – Render run detail and break detail components with filter and action outputs.
* Lines 43-45 – Render the system activity feed component.

## `src/app/app.component.html` referenced components
Documented individually below.

## `src/app/app.component.css`
* Lines 1-10 – Define host typography and background gradient.
* Lines 13-20 – Style the application header.
* Lines 22-35 – Style the authenticated content grid and shared card layout.
* Lines 37-53 – Style the activity card span and reconcile list panel.

## `src/app/components/reconciliation-list/reconciliation-list.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-3 | Import Angular component utilities and the reconciliation list model. |
| 5-11 | Annotate the standalone component and reference template/styles. |
| 12-16 | Declare inputs for reconciliation data, selection, and export availability. |
| 17-19 | Declare output events for selection, run trigger, and export. |
| 21-31 | Emit events for list selection, manual runs, and exports. |

## `src/app/components/reconciliation-list/reconciliation-list.component.html`
* Lines 1-19 – Render the reconciliation list, highlight the active item, and expose run/export buttons bound to component events.

## `src/app/components/reconciliation-list/reconciliation-list.component.css`
* Lines 1-26 – Style the list layout, hover/active states, and spacing.

## `src/app/components/run-detail/run-detail.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-6 | Import Angular modules, pipes, and model definitions used in the component. |
| 8-14 | Annotate the standalone component and register template/styles. |
| 15-21 | Declare inputs for run detail, selected break, filter metadata, current filter, and selection/filter outputs. |
| 23-24 | Maintain local status selection state. |
| 26-31 | `ngOnChanges` syncs local filter copies and selected statuses when parent filter changes. |
| 33-35 | `onSelectBreak` emits the clicked break. |
| 37-43 | `onFilterChanged` emits the new filter combining local selections and statuses. |
| 45-48 | `resetStatuses` clears selected statuses and re-emits filters. |
| 50-59 | `toggleStatus` updates local status selections and emits filter changes. |
| 61-63 | `formatStatus` prettifies enum strings for display. |

## `src/app/components/run-detail/run-detail.component.html`
* Lines 1-24 – Display run summary metrics.
* Lines 26-75 – Render filter controls for product, sub-product, entity, and statuses with bindings to component handlers.
* Lines 77-105 – Render the break inventory table highlighting the selected break.
* Lines 107-109 – Provide empty-state messaging when no runs exist.

## `src/app/components/run-detail/run-detail.component.css`
* Lines 1-90 – Style the run summary grid, filter controls, status options, table, and empty state visuals.

## `src/app/components/break-detail/break-detail.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-5 | Import Angular modules and model types needed for rendering and forms. |
| 7-13 | Annotate the standalone component and register template/styles. |
| 15-17 | Declare inputs and action outputs for comments and status changes. |
| 19-21 | Track local comment form state. |
| 22-27 | Reset comment form when the selected break changes. |
| 29-39 | `submitComment` validates and emits comment payloads. |
| 41-46 | `changeStatus` emits status updates for maker/checker actions. |
| 48-50 | `statusOptions` getter returns allowed status transitions. |
| 52-63 | `getStatusText` maps workflow statuses to button labels. |

## `src/app/components/break-detail/break-detail.component.html`
* Lines 1-52 – Present break metadata, payload JSON, comment timeline, add-comment form, and maker/checker buttons.
* Lines 53-55 – Display empty state when no break is selected.

## `src/app/components/break-detail/break-detail.component.css`
* Lines 1-96 – Style break metadata, payload panels, commentary timeline, forms, action buttons, and empty state text.

## `src/app/components/system-activity/system-activity.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-4 | Import Angular modules and the activity model. |
| 5-10 | Annotate the standalone component and register resources. |
| 12-13 | Declare input array for activity entries. |

## `src/app/components/system-activity/system-activity.component.html`
* Lines 1-15 – Render the activity feed list with timestamp and details, plus empty-state messaging.

## `src/app/components/system-activity/system-activity.component.css`
* Lines 1-43 – Style the list layout, timestamp column, badges, and empty state.
