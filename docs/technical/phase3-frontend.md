# Phase 3 Frontend Technical Reference

The following tables describe every Phase 3 Angular source change line by line (or contiguous block) across templates, styles, services, and models.

## `app/app.component.html`
| Lines | Explanation |
| --- | --- |
| 1-8 | Render application header with title, session greeting, and logout button when authenticated. |
| 10-15 | Show the login component when the user is not authenticated, wiring loading and error bindings. |
| 17-46 | Display the main dashboard grid when authenticated, including reconciliation list, run detail, break detail, and activity feed components with bound inputs and outputs. |

## `app/app.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-20 | Import Angular utilities, RxJS helpers, services, and UI components required by the root component. |
| 22-36 | Declare the standalone component metadata with template and style references. |
| 37-47 | Expose observable streams from the state service for template binding. |
| 48-51 | Track login error/loading UI state and the destroy notifier. |
| 53-68 | Inject API, session, and state services; load reconciliations on init; and tear down subscriptions on destroy. |
| 70-87 | Implement login workflow handling form submission, session storage, and error reporting. |
| 89-93 | Implement logout by clearing session, resetting state, and clearing errors. |
| 95-121 | Proxy UI events to the state service for selection, trigger, comment, status, filter, bulk update, and export actions. |
| 123-140 | Export handler retrieves the current run, calls export service, and triggers a browser download of the returned blob. |

## `components/break-detail/break-detail.component.css`
| Lines | Explanation |
| --- | --- |
| 1-47 | Style the break container, metadata, and difference highlighting table with spacing and colours. |
| 50-66 | Style the commentary timeline list and timestamps. |
| 68-92 | Style comment form layout and status action buttons including hover states. |
| 94-107 | Provide messaging styles for unavailable actions or selections. |

## `components/break-detail/break-detail.component.html`
| Lines | Explanation |
| --- | --- |
| 1-7 | Render selected break metadata with fallback placeholders. |
| 9-27 | Display side-by-side source value table with per-field highlighting; show empty message when no fields exist. |
| 29-59 | Render comment history, comment form inputs, status transition buttons, and fallback messaging when actions unavailable. |
| 61-64 | Provide empty-state messaging when no break is selected. |

## `components/break-detail/break-detail.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-6 | Import Angular modules, forms, DTOs, and enums for the component. |
| 7-13 | Declare the standalone component metadata with template and styles. |
| 14-23 | Define inputs, outputs, and component state for comments and difference tracking. |
| 24-30 | Reset form state and recompute differences when the selected break changes. |
| 32-42 | Emit comment events after validation and reset the form text. |
| 44-49 | Emit status updates when transition buttons clicked. |
| 51-66 | Provide computed getters for status options and display text. |
| 68-99 | Utility helpers for difference detection, value retrieval, and formatting fallback display strings. |
| 101-118 | Recompute field keys and difference set whenever a new break is supplied. |

## `components/reconciliation-list/reconciliation-list.component.css`
| Lines | Explanation |
| --- | --- |
| 1-26 | Style reconciliation list, items, hover/active states, and layout spacing. |
| 28-44 | Style the trigger configuration card container. |
| 42-68 | Style the trigger form grid, labels, inputs, and textarea. |
| 70-95 | Style button row layout, disabled state, and hover styling. |

## `components/reconciliation-list/reconciliation-list.component.html`
| Lines | Explanation |
| --- | --- |
| 1-12 | Render reconciliation list with selection state and descriptions. |
| 13-44 | Display trigger configuration form, binding trigger metadata fields and export action. |

## `components/reconciliation-list/reconciliation-list.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-4 | Import Angular core, forms, and DTO types. |
| 6-12 | Declare component metadata with template and styles. |
| 13-26 | Define inputs, outputs, and form state for trigger metadata and export availability. |
| 27-29 | Emit selected reconciliation to the parent container. |
| 31-39 | Build trigger payload from trimmed form values and emit to parent. |
| 41-43 | Emit export request event. |

## `components/run-detail/run-detail.component.css`
| Lines | Explanation |
| --- | --- |
| 1-59 | Style run summary grid, labels, trigger comment callout, and analytics card layout. |
| 61-118 | Style analytics cards, lists, and filters layout including status chip controls. |
| 119-168 | Style bulk selection toolbar and bulk action form layout. |
| 170-231 | Style break inventory table, selection states, and empty message presentation. |

## `components/run-detail/run-detail.component.html`
| Lines | Explanation |
| --- | --- |
| 1-39 | Render run summary metadata with fallback messaging for missing values. |
| 41-78 | Present analytics dashboard cards including status distribution, product leaders, and aging buckets. |
| 80-128 | Render filter controls for product, sub-product, entity, and statuses with change handlers. |
| 131-156 | Render bulk selection toolbar and conditional bulk action form. |
| 158-195 | Render break inventory table with row selection, checkbox handling, and break attributes. |
| 197-199 | Provide empty-state message when no runs exist. |

## `components/run-detail/run-detail.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-13 | Import Angular modules, forms, DTO types, filters, and enums. |
| 14-20 | Declare component metadata for the standalone component. |
| 21-35 | Define inputs, outputs, and component state for filters, selection, and bulk actions. |
| 37-48 | Respond to input changes by syncing local filter state and resetting bulk action state. |
| 50-52 | Emit selected break to parent container. |
| 54-60 | Emit filter updates using merged local filter and status selection. |
| 62-76 | Manage status selection toggles and clearing interactions. |
| 78-80 | Format status display strings. |
| 82-100 | Manage bulk selection state machine, including toggles, select-all, and clear operations. |
| 102-118 | Construct and validate bulk payload before emitting to parent, ensuring work requested. |
| 120-161 | Derive allowable bulk status options by intersecting transitions and compute analytics display helpers. |

## `models/api-models.ts`
| Lines | Explanation |
| --- | --- |
| 1-95 | Declare TypeScript interfaces mirroring backend DTOs, including new trigger metadata, run analytics, and bulk update payload models used by the UI. |

## `services/api.service.ts`
| Lines | Explanation |
| --- | --- |
| 1-15 | Import Angular HTTP services, observables, DTO interfaces, enums, environment, and filter types. |
| 17-21 | Configure injectable service and inject HTTP client. |
| 23-65 | Expose methods mapping to backend endpoints for authentication, reconciliation discovery, triggering runs, fetching run detail, commenting, status updates, bulk updates, exports, and activity feed retrieval. |
| 67-87 | Build HTTP query parameters from optional filter criteria, appending multi-select statuses as repeated parameters. |

## `services/reconciliation-state.service.ts`
| Lines | Explanation |
| --- | --- |
| 1-15 | Import Angular injectable, RxJS types, API service, DTOs, enums, and filter model. |
| 16-33 | Declare the state service, initialise reactive subjects, and expose observable streams for components. |
| 34-56 | Load reconciliations, manage initial selection, fetch latest runs, and refresh the activity feed. |
| 58-72 | Handle reconciliation selection and trigger run execution, refreshing runs and activity upon completion. |
| 75-91 | Wire break comment and status updates, refreshing activity and cached break data. |
| 93-107 | Implement bulk update dispatch and export helper returning an observable with error handling when unavailable. |
| 108-132 | Provide reset helpers, accessors for current run/filter, and filter updates that drive refreshed data. |
| 134-168 | Internal helpers to fetch runs, merge updated break data, refresh selected break, and reload activity timeline. |
