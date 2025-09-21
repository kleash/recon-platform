# Phase 1 Frontend Technical Reference

Each section documents every line (or continuous group of lines) in the Angular codebase.

## Project Structure & Configuration
* `package.json` – Defines Angular 17 dependencies, CLI tooling, and scripts for serve/build/test workflows.
* `angular.json` – Configures the Angular builder, dev server, and Karma test runner entry points.
* `tsconfig*.json` – Enable strict TypeScript compilation with a single application target.
* `karma.conf.js` – Declares Karma plugins, reporters, and a ChromeHeadless testing strategy.
* `src/test.ts` – Bootstraps the Angular testing environment.
* `src/index.html` and `src/styles.css` – Provide the HTML host page and global styling baseline.

## `src/main.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import Angular’s standalone bootstrap utility. |
| 2 | Import HTTP client provider factory along with interceptor support. |
| 3 | Import animation support for potential future transitions. |
| 4 | Import the root `AppComponent`. |
| 5 | Import router provider function. |
| 6 | Import the application route definition array. |
| 7 | Import the authentication interceptor factory. |
| 9-15 | Bootstrap the standalone application with HTTP client (plus interceptor), router, and animations, logging errors to the console on failure. |

## `src/app/app.routes.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import Angular `Routes` type. |
| 3 | Export an empty route array—the MVP does not require additional routes beyond the bootstrapped component. |

## `src/app/models/api-models.ts`
| Lines | Explanation |
| --- | --- |
| 1-4 | Define the `LoginResponse` interface representing backend login payloads. |
| 6-10 | Define `ReconciliationListItem` interface. |
| 12-16 | Define `ReconciliationSummary` interface. |
| 18-24 | Define `BreakComment` interface. |
| 26-33 | Define `BreakItem` interface, mapping to break DTOs. |
| 35-38 | Define `RunDetail` interface bundling summary and breaks. |

## `src/app/services/session.service.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import Angular `Injectable`. |
| 2 | Import login response model. |
| 4-6 | Define local storage keys for token, display name, and groups. |
| 8 | Register `SessionService` as a root-level injectable. |
| 9-11 | Declare cached token, display name, and groups. |
| 13-17 | Constructor loads existing session data from `localStorage`. |
| 19-26 | `storeSession` caches response data and persists to `localStorage`. |
| 28-34 | `clear` removes cached values and clears storage. |
| 36-38 | `getToken` accessor. |
| 40-42 | `getDisplayName` accessor. |
| 44-46 | `getGroups` accessor. |
| 48-50 | `isAuthenticated` convenience flag. |

## `src/app/services/auth.interceptor.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import Angular functional interceptor type. |
| 2 | Import `inject` helper. |
| 3 | Import `SessionService`. |
| 5-13 | Factory function clones outgoing requests with a `Bearer` header when a token exists. |

## `src/app/services/api.service.ts`
| Lines | Explanation |
| --- | --- |
| 1 | Import Angular `Injectable`. |
| 2 | Import `HttpClient`. |
| 3 | Import RxJS `Observable`. |
| 4-8 | Import DTO interfaces used by API methods. |
| 10 | Define API base URL constant pointing to local backend. |
| 12 | Register service as root provider. |
| 13-14 | Constructor injection of `HttpClient`. |
| 16-18 | `login` posts credentials and returns `LoginResponse`. |
| 20-22 | `getReconciliations` fetches accessible reconciliations. |
| 24-26 | `triggerRun` posts to manual run endpoint. |
| 28-30 | `getLatestRun` fetches latest run detail. |
| 32-34 | `addComment` posts break comments. |
| 36-38 | `updateStatus` patches break status. |
| 40-42 | `exportRun` retrieves Excel binary as `Blob`. |

## `src/app/app.component.ts`
| Lines | Explanation |
| --- | --- |
| 1-5 | Import Angular core utilities, pipes, and forms module for the standalone component. |
| 6-8 | Import service dependencies and DTO interfaces. |
| 10-15 | Annotate the component: selector, standalone flag, imported modules/pipes, and template/style references. |
| 16 | Declare `AppComponent` class implementing `OnInit`. |
| 17 | Set the UI title string. |
| 19-23 | Initialise login form defaults, error flag, and loading indicator. |
| 25-28 | Initialise reconciliation state holders. |
| 30-31 | Track comment form state. |
| 33 | Constructor injects API and session services. |
| 35-38 | `ngOnInit` auto-loads reconciliations when a session already exists. |
| 40-52 | `login` posts credentials, stores session, and handles errors/loading state. |
| 54-59 | `logout` clears session and resets component state. |
| 61-67 | `loadReconciliations` retrieves list and auto-selects first reconciliation. |
| 69-74 | `selectReconciliation` captures selection and loads latest run. |
| 76-82 | `triggerRun` executes manual matching for the selected reconciliation. |
| 84-88 | `selectBreak` sets the active break and clears the comment form. |
| 90-97 | `addComment` posts a comment when form data is present and refreshes selected break. |
| 99-104 | `updateStatus` pushes status change to backend and refreshes break. |
| 106-115 | `exportRun` downloads the Excel export as a temporary link. |
| 117-123 | `refreshBreak` updates the run detail state with the latest break snapshot. |
| 124 | Close class. |

## `src/app/app.component.html`
The HTML template is documented by section:
* Header block (lines 1-8) renders title, welcome text, and logout button when authenticated.
* `ng-template` block (lines 10-21) implements the sign-in form with bound username/password fields and error messaging.
* Main `section` (lines 23-78) lays out reconciliation list, run summary, break table, and detail panel.
* Break detail subsection (lines 53-78) includes side-by-side source JSON, comment timeline, add-comment form, and status buttons.

## `src/app/app.component.css`
* Lines 1-7 define host font and base layout.
* Lines 9-22 style header and toolbar.
* Lines 24-83 style cards, list, and grid layout.
* Lines 85-126 style tables, break detail, and commentary timeline.
* Lines 128-146 normalise button states and error message styling.

## Frontend Resources
* `src/styles.css` – Global body background reset.
* `src/app/app.routes.ts` – Already covered above.
