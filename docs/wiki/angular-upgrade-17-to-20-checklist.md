# Angular 17 → 20 Upgrade Checklist

This document is intended to be the single source of truth for upgrading the Universal Reconciliation Platform frontend from Angular 17 to Angular 20. Follow the stages sequentially, committing after every major milestone so you can roll back if required.

## References
- [Angular Update Guide (17.0 → 20.0)](https://update.angular.io/?l=en&v=17.0-20.0)
- [Angular v20 Release Notes](https://github.com/angular/angular/blob/main/CHANGELOG.md#2000-2025-05-28) · [Blog](https://blog.angular.dev/announcing-angular-v20-b5c9c06cf301)
- [Angular v19 Release Notes](https://github.com/angular/angular/blob/main/CHANGELOG.md#1900-2024-11-19) · [Blog](https://blog.angular.dev/meet-angular-v19-7b29dfd05b84)
- [Angular v18 Release Notes](https://github.com/angular/angular/blob/main/CHANGELOG.md#1800-2024-05-22) · [Blog](https://blog.angular.dev/angular-v18-is-now-available-ff43049fafe4)
- [Angular CLI `ng update` docs](https://angular.dev/cli/update)
- [Supported Angular / Node.js versions](https://angular.dev/reference/versions)

## Before you start
- [ ] Create a dedicated branch for the upgrade in `frontend/` and ensure the working tree is clean (`git status`).
- [ ] Capture baseline `package.json` / `package-lock.json` (or `pnpm-lock.yaml`) by committing or copying them aside for diffing.
- [ ] Run existing quality gates (`npm run lint`, `npm test`, `npm run e2e` if available) and document the baseline results.
- [ ] Ensure you have a changelog for third-party packages (Material, CDK, ESLint plugins, Storybook, Cypress, Playwright, tooling wrappers) to confirm compatibility with Angular 18–20.

## Environment & tooling prerequisites
- [ ] Verify local Node.js: `node -v` should be ≥ 18.19.0 before attempting the v18 upgrade. If not, install a compatible version with nvm or the team-approved toolchain.
- [ ] Align package manager versions (npm 10.x or supported pnpm/yarn). Record versions in the upgrade notes.
- [ ] Validate CI runners can switch to the required Node versions (18.19.0+ for v18, ≥ 20.11.1 for v20; avoid Node 22.0–22.10). Coordinate with DevOps before merging.
- [ ] Review tooling that embeds Node/TypeScript (Nx, Bazel, webpack builders, Storybook, Cypress) and note required upgrades.

## Stage 0 – Refresh Angular 17 baseline
- [ ] Run `npx ng update @angular/core@17 @angular/cli@17` to land latest patches.
- [ ] Run `npm install` to sync the lock file.
- [ ] Execute lint/unit/e2e suites and commit the clean baseline (tag the commit message with “Angular 17 baseline updated”).

## Stage 1 – Upgrade to Angular 18
- [ ] Ensure Node.js ≥ 18.19.0 locally and in CI.
- [ ] Run `npx ng update @angular/core@18 @angular/cli@18` (include `@angular/ssr` if SSR is configured).
- [ ] Run `npx ng update @angular/material@18` (and any other first-party Angular packages: `@angular/cdk`, `@angular/forms`, etc. if not covered automatically).
- [ ] Bump TypeScript to ≥ 5.4 in `package.json`, run `npm install`, and update any TypeScript-related tooling configs.
- [ ] Replace deprecated `async` imports in tests with `waitForAsync` and address `ComponentFixture.whenStable` hangs (ensure pending HTTP/router work is flushed or mocked).
- [ ] Review `OnPush` components whose host bindings relied on automatic refresh; add `markForCheck` or adjust change detection strategy.
- [ ] Retest areas relying on `fixture.autoDetectChanges()` because calls now run inside `ApplicationRef.tick`.
- [ ] Decide whether to provide `provideZoneChangeDetection({ schedulingMode: NgZoneSchedulingMode.NgZoneOnly })` to retain legacy zone timing if needed.
- [ ] Run `npm run lint`, `npm test`, Playwright/regression smoke suites, and document failures. Fix or open tasks as required.
- [ ] Commit the Angular 18 upgrade (include lock file changes and migration notes).

## Stage 2 – Upgrade to Angular 19
- [ ] Verify Node.js still satisfies ≥ 18.19.0 (v19 keeps the same requirement) and CI has the same capability.
- [ ] Run `npx ng update @angular/core@19 @angular/cli@19`.
- [ ] Run `npx ng update @angular/material@19`.
- [ ] Upgrade TypeScript to ≥ 5.5 (`package.json`, `npm install`) and update dependent tooling (ts-node, ts-loader, ESLint, Jest transformer, etc.).
- [ ] Accept migrations that add `standalone: false` for declarations inside NgModules. Review your own standalone strategy and adjust shared modules accordingly.
- [ ] Replace `BrowserModule.withServerTransition()` with injecting the `APP_ID` token when SSR or pre-rendering is used.
- [ ] Replace `Router.errorHandler` usage with `withNavigationErrorHandler` (`provideRouter`) or `errorHandler` in `RouterModule.forRoot` options.
- [ ] Update tests now that `ComponentFixture.autoDetect` and `ApplicationRef.tick` rethrow errors through `ErrorHandler`.
- [ ] Rename `ExperimentalPendingTasks` to `PendingTasks` in code/tests. Update `fakeAsync` helper expectations because Angular timers are now visible to the scheduler.
- [ ] For manual `createComponent` calls, add `document.createTextNode('')` where empty `projectableNodes` were previously used to suppress fallback projection.
- [ ] Check Angular Elements integrations for timing regressions due to the hybrid scheduler.
- [ ] Run `npm run lint`, `npm test`, E2E suites, automation harness, and document outcomes. Resolve regressions.
- [ ] Commit the Angular 19 upgrade with detailed notes.

## Stage 3 – Upgrade to Angular 20
- [ ] Upgrade Node.js everywhere (local + CI) to ≥ 20.11.1 and ensure no environment uses Node 18 or Node 22.0–22.10.
- [ ] Upgrade TypeScript to ≥ 5.8 **before** running schematics (`package.json`, lock file, config updates).
- [ ] Run `npx ng update @angular/core@20 @angular/cli@20` (include `@angular/ssr` if relevant).
- [ ] Run `npx ng update @angular/material@20` (plus other Angular ecosystem packages).
- [ ] Remove deprecated injector APIs: delete all `InjectFlags` usages, replace `TestBed.get` with `TestBed.inject`, and drop `TestBed.flushEffects` calls (use `TestBed.tick` instead).
- [ ] Rename lifecycle hooks `afterRender` → `afterEveryRender`; adopt stabilized `provideZonelessChangeDetection` if zoneless mode is desired.
- [ ] For resource APIs, rename the `request` property to `params` and replace `ResourceStatus` enum references with the new string literal constants.
- [ ] Migrate `ng-reflect-*` attribute dependencies. If temporary compatibility is required, add `provideNgReflectAttributes()` (dev mode only) and plan its removal.
- [ ] Audit templates for stricter parsing: parentheses now always apply when nesting nullish coalescing, and reserved identifiers like `void` must be addressed via `this.void`.
- [ ] Update AsyncPipe-related tests: unhandled promise rejections are now reported through `ErrorHandler` even without Zone.js.
- [ ] Review router redirects that return promises/observables to ensure they satisfy the new `RedirectFn` contract.
- [ ] Validate SSR/hydration flows after stabilized incremental hydration and `withI18nSupport()` APIs.
- [ ] Run all quality gates (lint, unit, Playwright, integration harness, bootstrap scripts, historical seed) and capture evidence of success.
- [ ] Commit the Angular 20 upgrade and summarize key migrations.

## Post-upgrade hardening
- [ ] Audit third-party libraries and internal packages for compatibility (Material schematics, ESLint configs, Storybook, Cypress, Jest, custom builders). Update or pin versions as needed.
- [ ] Remove temporary shims (`provideNgReflectAttributes`, custom zone scheduling) once the codebase no longer relies on them.
- [ ] Update documentation in `docs/wiki/DECISIONS.md`, `PATTERNS.md`, `TROUBLESHOOTING.md`, and `AGENTS.md` with upgrade rationale, migration notes, and playbook tips for future upgrades.
- [ ] Refresh CI/CD caches (npm, Bazel, Docker layers) to avoid stale builds with outdated dependencies.
- [ ] Monitor production telemetry after deployment for new errors (change detection loops, hydration mismatches, router navigation issues) surfaced by the stricter runtime.
- [ ] Schedule a retrospective to capture lessons learned and potential automation opportunities for future Angular upgrades.

## Modernization opportunities (optional follow-up)
- [ ] Evaluate migrating legacy inputs/outputs to signal-based APIs and signal queries introduced in v19.
- [ ] Adopt the new control flow syntax and deferrable views where they simplify templates (with hydration coverage for defer blocks).
- [ ] Consider enabling zoneless change detection via `provideZonelessChangeDetection` once blockers are resolved.
- [ ] Explore incremental hydration and `withI18nSupport()` for localized SSR flows now that they are stabilized in v20.
- [ ] Update onboarding guides to highlight standalone-by-default components, TypeScript 5.8+ requirements, and new testing recommendations.
