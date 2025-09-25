import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, tap } from 'rxjs/operators';
import {
  AdminIngestionBatch,
  AdminIngestionRequestMetadata,
  AdminReconciliationDetail,
  AdminReconciliationRequest,
  AdminReconciliationSchema,
  AdminReconciliationSummary,
  ReconciliationLifecycleStatus
} from '../models/admin-api-models';
import { ApiService } from './api.service';
import { NotificationService } from './notification.service';
import { SystemActivityEntry } from '../models/api-models';

type AdminCatalogFilters = {
  status?: ReconciliationLifecycleStatus;
  owner?: string;
  updatedAfter?: string;
  updatedBefore?: string;
  search?: string;
  page: number;
  size: number;
};

@Injectable({ providedIn: 'root' })
export class AdminReconciliationStateService {
  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  private readonly summariesSubject = new BehaviorSubject<AdminReconciliationSummary[]>([]);
  private readonly selectedSubject = new BehaviorSubject<AdminReconciliationDetail | null>(null);
  private readonly activitySubject = new BehaviorSubject<SystemActivityEntry[]>([]);
  private readonly filtersSubject = new BehaviorSubject<AdminCatalogFilters>({ page: 0, size: 20 });
  private readonly listMetaSubject = new BehaviorSubject<{
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({ totalElements: 0, totalPages: 0, page: 0, size: 20 });

  readonly loading$ = this.loadingSubject.asObservable();
  readonly reconciliations$ = this.summariesSubject.asObservable();
  readonly selected$ = this.selectedSubject.asObservable();
  readonly activity$ = this.activitySubject.asObservable();
  readonly filters$ = this.filtersSubject.asObservable();
  readonly listMeta$ = this.listMetaSubject.asObservable();

  constructor(private readonly api: ApiService, private readonly notifications: NotificationService) {}

  loadSummaries(partial?: Partial<AdminCatalogFilters>): void {
    const current = this.filtersSubject.value;
    const next: AdminCatalogFilters = {
      ...current,
      ...partial,
      page:
        partial?.page !== undefined
          ? partial.page
          : partial && this.shouldResetPage(partial)
          ? 0
          : current.page,
      size: partial?.size ?? current.size
    };
    this.filtersSubject.next(next);
    this.fetchSummaries(next);
  }

  loadDefinition(id: number): void {
    this.loadingSubject.next(true);
    this.api
      .getAdminReconciliation(id)
      .pipe(
        tap((detail) => this.selectedSubject.next(detail)),
        catchError((error) => {
          console.error('Failed to load reconciliation detail', error);
          this.notifications.push('Unable to load reconciliation detail.', 'error');
          this.selectedSubject.next(null);
          return of(null);
        }),
        finalize(() => this.loadingSubject.next(false))
      )
      .subscribe();
  }

  clearSelection(): void {
    this.selectedSubject.next(null);
  }

  fetchDefinition(id: number): Observable<AdminReconciliationDetail> {
    return this.api.getAdminReconciliation(id);
  }

  createDefinition(payload: AdminReconciliationRequest): Observable<AdminReconciliationDetail> {
    this.loadingSubject.next(true);
    return this.api.createAdminReconciliation(payload).pipe(
      tap((detail) => {
        this.notifications.push(`Created ${detail.code} (${detail.name}).`, 'success');
        this.selectedSubject.next(detail);
        this.refreshSummaries();
      }),
      catchError((error) => {
        console.error('Failed to create reconciliation', error);
        this.notifications.push('Failed to create reconciliation definition.', 'error');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  updateDefinition(
    id: number,
    payload: AdminReconciliationRequest
  ): Observable<AdminReconciliationDetail> {
    this.loadingSubject.next(true);
    return this.api.updateAdminReconciliation(id, payload).pipe(
      tap((detail) => {
        this.notifications.push(`Updated ${detail.code} (${detail.name}).`, 'success');
        this.selectedSubject.next(detail);
        this.refreshSummaries();
      }),
      catchError((error) => {
        console.error('Failed to update reconciliation', error);
        this.notifications.push('Failed to update reconciliation definition.', 'error');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  patchDefinition(
    id: number,
    payload: Partial<
      Pick<
        AdminReconciliationRequest,
        | 'notes'
        | 'makerCheckerEnabled'
        | 'status'
        | 'owner'
        | 'autoTriggerEnabled'
        | 'autoTriggerCron'
        | 'autoTriggerTimezone'
        | 'autoTriggerGraceMinutes'
      >
    > & {
      version?: number | null;
    }
  ): Observable<AdminReconciliationDetail> {
    this.loadingSubject.next(true);
    return this.api.patchAdminReconciliation(id, payload).pipe(
      tap((detail) => {
        this.notifications.push(`Saved updates for ${detail.code}.`, 'success');
        this.selectedSubject.next(detail);
        this.refreshSummaries();
      }),
      catchError((error) => {
        console.error('Failed to patch reconciliation', error);
        this.notifications.push('Failed to apply updates.', 'error');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  deleteDefinition(id: number): void {
    this.loadingSubject.next(true);
    this.api
      .deleteAdminReconciliation(id)
      .pipe(
        tap(() => {
          this.notifications.push('Reconciliation retired successfully.', 'success');
          this.selectedSubject.next(null);
          this.refreshSummaries();
        }),
        catchError((error) => {
          console.error('Failed to delete reconciliation', error);
          this.notifications.push('Failed to retire reconciliation.', 'error');
          return of(null);
        }),
        finalize(() => this.loadingSubject.next(false))
      )
      .subscribe();
  }

  exportSchema(id: number): Observable<AdminReconciliationSchema> {
    return this.api.exportAdminReconciliationSchema(id);
  }

  uploadBatch(
    definitionId: number,
    sourceCode: string,
    file: File,
    metadata: AdminIngestionRequestMetadata
  ): Observable<AdminIngestionBatch> {
    this.loadingSubject.next(true);
    return this.api.uploadAdminIngestionBatch(definitionId, sourceCode, file, metadata).pipe(
      tap((batch) => {
        this.notifications.push('Ingestion batch submitted.', 'success');
        const selected = this.selectedSubject.value;
        if (selected) {
          const batches = Array.isArray(selected.ingestionBatches)
            ? [...selected.ingestionBatches]
            : [];
          batches.unshift(batch);
          this.selectedSubject.next({ ...selected, ingestionBatches: batches });
        }
      }),
      catchError((error) => {
        console.error('Failed to upload ingestion batch', error);
        this.notifications.push('Failed to submit ingestion batch.', 'error');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  loadActivityForDefinition(definitionCode: string): void {
    this.api
      .getSystemActivity()
      .pipe(
        map((entries) =>
          entries.filter((entry) =>
            entry.details?.toLowerCase().includes(definitionCode.toLowerCase())
          )
        ),
        tap((entries) => this.activitySubject.next(entries)),
        catchError((error) => {
          console.error('Failed to load admin activity', error);
          this.notifications.push('Failed to load recent activity.', 'error');
          this.activitySubject.next([]);
          return of([]);
        })
      )
      .subscribe();
  }

  private refreshSummaries(): void {
    this.fetchSummaries(this.filtersSubject.value);
  }

  private fetchSummaries(filters: AdminCatalogFilters): void {
    this.loadingSubject.next(true);
    this.api
      .getAdminReconciliations(filters)
      .pipe(
        tap((page) => {
          this.summariesSubject.next(page.items);
          this.listMetaSubject.next({
            totalElements: page.totalElements,
            totalPages: page.totalPages,
            page: page.page,
            size: page.size
          });
        }),
        catchError((error) => {
          console.error('Failed to load admin reconciliations', error);
          this.notifications.push('Unable to load reconciliation catalog.', 'error');
          this.summariesSubject.next([]);
          this.listMetaSubject.next({
            totalElements: 0,
            totalPages: 0,
            page: filters.page,
            size: filters.size
          });
          return of(null);
        }),
        finalize(() => this.loadingSubject.next(false))
      )
      .subscribe();
  }

  private shouldResetPage(partial?: Partial<AdminCatalogFilters>): boolean {
    if (!partial) {
      return false;
    }
    const keys = Object.keys(partial) as (keyof AdminCatalogFilters)[];
    return keys.some((key) =>
      ['status', 'owner', 'search', 'updatedAfter', 'updatedBefore'].includes(key as string)
    );
  }
}
