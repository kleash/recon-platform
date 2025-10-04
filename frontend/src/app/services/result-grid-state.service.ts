import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import {
  BreakResultRow,
  BreakSearchResponse,
  ExportJobRequestPayload,
  ExportJobSummary,
  GridColumn,
  SavedView,
  SavedViewRequestPayload
} from '../models/api-models';
import { ApiService } from './api.service';
import { NotificationService } from './notification.service';
import { ReconciliationListItem } from '../models/api-models';
import { catchError, map, tap } from 'rxjs/operators';

type QueryValue = string | number | boolean | Array<string | number>;
type QueryParams = Record<string, QueryValue>;

/**
 * Provides a single source of truth for grid query state, including pagination cursors, saved views, and export
 * job histories. Components interact through observables rather than mutating API calls directly.
 */
@Injectable({ providedIn: 'root' })
export class ResultGridStateService {
  private reconciliation: ReconciliationListItem | null = null;
  private currentQuery: QueryParams = {};
  private cursor: string | null = null;
  private exhausted = false;
  private loading = false;

  private readonly rowsSubject = new BehaviorSubject<BreakResultRow[]>([]);
  private readonly columnsSubject = new BehaviorSubject<GridColumn[]>([]);
  private readonly totalSubject = new BehaviorSubject<number | null>(null);
  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  private readonly savedViewsSubject = new BehaviorSubject<SavedView[]>([]);
  private readonly activeViewSubject = new BehaviorSubject<number | null>(null);
  private readonly exportJobsSubject = new BehaviorSubject<ExportJobSummary[]>([]);

  readonly rows$ = this.rowsSubject.asObservable();
  readonly columns$ = this.columnsSubject.asObservable();
  readonly total$ = this.totalSubject.asObservable();
  readonly loading$ = this.loadingSubject.asObservable();
  readonly savedViews$ = this.savedViewsSubject.asObservable();
  readonly activeView$ = this.activeViewSubject.asObservable();
  readonly exportJobs$ = this.exportJobsSubject.asObservable();

  constructor(private readonly api: ApiService, private readonly notifications: NotificationService) {}

  setReconciliation(reconciliation: ReconciliationListItem | null): void {
    this.reconciliation = reconciliation;
    this.resetState();
    if (reconciliation) {
      this.currentQuery = this.buildDefaultQuery();
      this.fetchResults(true);
      this.loadSavedViews();
      this.loadExportJobs();
    }
  }

  applyQuery(update: QueryParams, options: { resetCursor?: boolean } = {}): void {
    if (!this.reconciliation) {
      return;
    }
    this.currentQuery = { ...this.currentQuery, ...update };
    if (options.resetCursor !== false) {
      this.cursor = null;
      this.exhausted = false;
      this.rowsSubject.next([]);
    }
    this.fetchResults(true);
  }

  refresh(): void {
    if (!this.reconciliation) {
      return;
    }
    this.cursor = null;
    this.exhausted = false;
    this.rowsSubject.next([]);
    this.fetchResults(true);
    this.loadExportJobs();
  }

  refreshExportHistory(): void {
    this.loadExportJobs();
  }

  replaceQuery(newQuery: QueryParams): void {
    if (!this.reconciliation) {
      return;
    }
    this.activeViewSubject.next(null);
    this.currentQuery = { ...newQuery };
    this.cursor = null;
    this.exhausted = false;
    this.rowsSubject.next([]);
    this.fetchResults(true);
  }

  loadMore(): void {
    if (!this.reconciliation || this.exhausted || this.loading) {
      return;
    }
    this.fetchResults(false);
  }

  saveView(payload: SavedViewRequestPayload, existingId?: number): void {
    if (!this.reconciliation) {
      return;
    }
    const settings = this.serializeCurrentQuery();
    const request: SavedViewRequestPayload = { ...payload, settingsJson: settings };
    const reconciliationId = this.reconciliation.id;
    const handler = existingId
      ? this.api.updateSavedView(reconciliationId, existingId, request)
      : this.api.createSavedView(reconciliationId, request);
    handler.subscribe({
      next: () => {
        this.notifications.push('Saved view updated.', 'success');
        this.loadSavedViews();
      },
      error: (err) => {
        const message = err?.error?.message ?? 'Unable to save view.';
        this.notifications.push(message, 'error');
      }
    });
  }

  deleteView(viewId: number): void {
    if (!this.reconciliation) {
      return;
    }
    this.api.deleteSavedView(this.reconciliation.id, viewId).subscribe({
      next: () => {
        this.notifications.push('Saved view deleted.', 'success');
        this.loadSavedViews();
      },
      error: () => this.notifications.push('Failed to delete saved view.', 'error')
    });
  }

  setDefaultView(viewId: number): void {
    if (!this.reconciliation) {
      return;
    }
    this.api.setDefaultSavedView(this.reconciliation.id, viewId).subscribe({
      next: () => {
        this.notifications.push('Default view updated.', 'success');
        this.loadSavedViews();
      },
      error: () => this.notifications.push('Failed to update default view.', 'error')
    });
  }

  applySavedView(view: SavedView): void {
    try {
      const payload = JSON.parse(view.settingsJson ?? '{}');
      const query = (payload?.query ?? {}) as QueryParams;
      this.activeViewSubject.next(view.id);
      this.applyQuery(query, { resetCursor: true });
    } catch (error) {
      this.notifications.push('Saved view settings are invalid.', 'error');
    }
  }

  queueExport(format: ExportFormat, options: { fileNamePrefix?: string | null; includeMetadata?: boolean } = {}): void {
    if (!this.reconciliation) {
      return;
    }
    const filters = this.sanitiseQueryForPersistence();
    const payload: ExportJobRequestPayload = {
      format,
      filters,
      fileNamePrefix: options.fileNamePrefix ?? null,
      includeMetadata: options.includeMetadata ?? true
    };
    this.api.queueExportJob(this.reconciliation.id, payload).subscribe({
      next: (job) => {
        this.notifications.push('Export queued successfully.', 'success');
        this.exportJobsSubject.next([job, ...this.exportJobsSubject.value]);
      },
      error: () => this.notifications.push('Failed to queue export.', 'error')
    });
  }

  refreshExportJob(jobId: number): void {
    this.api.getExportJob(jobId).subscribe({
      next: (job) => {
        const next = this.exportJobsSubject.value.map((existing) => (existing.id === job.id ? job : existing));
        this.exportJobsSubject.next(next);
      },
      error: () => this.notifications.push('Unable to refresh export job.', 'error')
    });
  }

  downloadExport(job: ExportJobSummary): void {
    this.api.downloadExportJob(job.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = job.fileName || `export-${job.id}.${job.format.toLowerCase()}`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
        this.notifications.push('Export downloaded successfully.', 'success');
      },
      error: () => this.notifications.push('Unable to download export.', 'error')
    });
  }

  fetchFilteredSelection(): Observable<{ breakIds: number[]; totalCount: number }> {
    if (!this.reconciliation) {
      return of({ breakIds: [], totalCount: 0 });
    }
    const query: QueryParams = { ...this.currentQuery };
    delete query['cursor'];
    return this.api
      .selectBreakIds(this.reconciliation.id, query)
      .pipe(
        map((response) => ({
          breakIds: response.breakIds ?? [],
          totalCount: response.totalCount ?? 0
        })),
        tap(({ breakIds, totalCount }) => {
          if (breakIds.length === 0) {
            this.notifications.push('No records match the current filters.', 'info');
          } else {
            const message = totalCount > breakIds.length
              ? `Loaded ${breakIds.length} filtered breaks (truncated from ${totalCount}).`
              : `Selected ${breakIds.length} filtered break${breakIds.length === 1 ? '' : 's'}.`;
            this.notifications.push(message, 'success');
          }
        }),
        catchError(() => {
          this.notifications.push('Unable to select filtered breaks.', 'error');
          return of({ breakIds: [], totalCount: 0 });
        })
      );
  }

  private fetchResults(reset: boolean): void {
    if (!this.reconciliation) {
      return;
    }
    this.loading = true;
    this.loadingSubject.next(true);

    const query: QueryParams = { ...this.currentQuery };
    if (!reset && this.cursor) {
      query['cursor'] = this.cursor;
    } else {
      delete query['cursor'];
    }

    this.api.searchBreakResults(this.reconciliation.id, query).subscribe({
      next: (response: BreakSearchResponse) => {
        const existing = reset ? [] : this.rowsSubject.value;
        const combined = [...existing, ...response.rows].sort((a, b) => {
          if (a.breakId === b.breakId) {
            return 0;
          }
          return a.breakId < b.breakId ? -1 : 1;
        });
        this.rowsSubject.next(combined);
        this.columnsSubject.next(response.columns);
        this.totalSubject.next(response.page.totalCount >= 0 ? response.page.totalCount : null);
        this.cursor = response.page.nextCursor ?? null;
        this.exhausted = !response.page.hasMore || !response.page.nextCursor;
      },
      error: () => {
        this.notifications.push('Failed to load results.', 'error');
        this.finishLoading();
      },
      complete: () => this.finishLoading()
    });
  }

  private loadSavedViews(): void {
    if (!this.reconciliation) {
      return;
    }
    this.api.listSavedViews(this.reconciliation.id).subscribe({
      next: (views) => this.savedViewsSubject.next(views),
      error: () => this.notifications.push('Unable to load saved views.', 'error')
    });
  }

  private loadExportJobs(): void {
    if (!this.reconciliation) {
      this.exportJobsSubject.next([]);
      return;
    }
    this.api.listExportJobs(this.reconciliation.id).subscribe({
      next: (jobs) => this.exportJobsSubject.next(jobs),
      error: () => this.notifications.push('Unable to load export jobs.', 'error')
    });
  }

  private resetState(): void {
    this.rowsSubject.next([]);
    this.columnsSubject.next([]);
    this.totalSubject.next(null);
    this.savedViewsSubject.next([]);
    this.exportJobsSubject.next([]);
    this.activeViewSubject.next(null);
    this.cursor = null;
    this.exhausted = false;
  }

  private buildDefaultQuery(): QueryParams {
    const today = this.todayInSgt();
    return {
      fromDate: today,
      toDate: today,
      size: 200,
      includeTotals: true
    };
  }

  private todayInSgt(): string {
    const now = new Date();
    const utcMillis = now.getTime() + now.getTimezoneOffset() * 60000;
    const sgtMillis = utcMillis + 8 * 60 * 60000;
    return new Date(sgtMillis).toISOString().slice(0, 10);
  }

  private sanitiseQueryForPersistence(): Record<string, string[]> {
    const output: Record<string, string[]> = {};
    Object.entries(this.currentQuery).forEach(([key, value]) => {
      if (Array.isArray(value)) {
        output[key] = value.map((entry) => String(entry));
      } else {
        output[key] = [String(value)];
      }
    });
    return output;
  }

  private serializeCurrentQuery(): string {
    return JSON.stringify({ query: this.currentQuery });
  }

  private finishLoading(): void {
    this.loading = false;
    this.loadingSubject.next(false);
  }
}

export type ExportFormat = 'CSV' | 'XLSX' | 'JSONL' | 'PDF';
