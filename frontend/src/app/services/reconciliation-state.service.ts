import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject, throwError } from 'rxjs';
import { ApiService } from './api.service';
import {
  BreakItem,
  BulkBreakUpdatePayload,
  BulkBreakUpdateResponse,
  FilterMetadata,
  ReconciliationListItem,
  RunDetail,
  SystemActivityEntry,
  TriggerRunPayload
} from '../models/api-models';
import { BreakStatus } from '../models/break-status';
import { BreakFilter } from '../models/break-filter';
import { NotificationService } from './notification.service';

/**
 * Central state manager for reconciliation-level data. It orchestrates API calls for runs, breaks, approvals,
 * and system activity while exposing observable streams consumed by workspace components.
 */
@Injectable({ providedIn: 'root' })
export class ReconciliationStateService {
  private readonly reconciliationsSubject = new BehaviorSubject<ReconciliationListItem[]>([]);
  private readonly selectedReconciliationSubject = new BehaviorSubject<ReconciliationListItem | null>(null);
  private readonly runDetailSubject = new BehaviorSubject<RunDetail | null>(null);
  private readonly selectedBreakSubject = new BehaviorSubject<BreakItem | null>(null);
  private readonly filterSubject = new BehaviorSubject<BreakFilter>({});
  private readonly filterMetadataSubject = new BehaviorSubject<FilterMetadata | null>(null);
  private readonly activitySubject = new BehaviorSubject<SystemActivityEntry[]>([]);
  private readonly breakEventsSubject = new Subject<void>();
  private readonly approvalsSubject = new BehaviorSubject<BreakItem[]>([]);
  private readonly approvalMetadataSubject = new BehaviorSubject<FilterMetadata | null>(null);
  private readonly workflowSummarySubject = new BehaviorSubject<string | null>(null);
  private pendingSelectionLockUntil = 0; // Prevents UI thrash by pausing break reselection immediately after bulk edits.

  readonly reconciliations$ = this.reconciliationsSubject.asObservable();
  readonly selectedReconciliation$ = this.selectedReconciliationSubject.asObservable();
  readonly runDetail$ = this.runDetailSubject.asObservable();
  readonly selectedBreak$ = this.selectedBreakSubject.asObservable();
  readonly filter$ = this.filterSubject.asObservable();
  readonly filterMetadata$ = this.filterMetadataSubject.asObservable();
  readonly activity$ = this.activitySubject.asObservable();
  readonly breakEvents$ = this.breakEventsSubject.asObservable();
  readonly approvals$ = this.approvalsSubject.asObservable();
  readonly approvalMetadata$ = this.approvalMetadataSubject.asObservable();
  readonly workflowSummary$ = this.workflowSummarySubject.asObservable();

  constructor(private readonly api: ApiService, private readonly notifications: NotificationService) {}

  loadReconciliations(): void {
    this.filterSubject.next({});
    this.api.getReconciliations().subscribe({
      next: (data: ReconciliationListItem[]) => {
        this.reconciliationsSubject.next(data);
        const currentSelection = this.selectedReconciliationSubject.value;
        if (currentSelection && data.some((item: ReconciliationListItem) => item.id === currentSelection.id)) {
          this.fetchLatestRun(currentSelection.id);
          return;
        }
        const nextSelection = data.length > 0 ? data[0] ?? null : null;
        this.selectedReconciliationSubject.next(nextSelection);
        if (nextSelection) {
          this.fetchLatestRun(nextSelection.id);
          this.loadApprovalQueue(nextSelection.id);
        } else {
          this.runDetailSubject.next(null);
          this.selectedBreakSubject.next(null);
          this.filterMetadataSubject.next(null);
          this.approvalsSubject.next([]);
          this.approvalMetadataSubject.next(null);
        }
      },
      error: () => this.notifications.push('Unable to load reconciliations. Please try again later.', 'error')
    });
    this.refreshActivity();
  }

  selectReconciliation(reconciliation: ReconciliationListItem): void {
    this.selectedReconciliationSubject.next(reconciliation);
    this.selectedBreakSubject.next(null);
    this.fetchLatestRun(reconciliation.id);
    this.loadApprovalQueue(reconciliation.id);
  }

  triggerRun(payload: TriggerRunPayload): void {
    const selected = this.selectedReconciliationSubject.value;
    if (!selected) {
      return;
    }
    this.api.triggerRun(selected.id, payload).subscribe({
      next: () => {
        this.fetchLatestRun(selected.id);
        this.refreshActivity();
        this.breakEventsSubject.next();
        this.notifications.push('Reconciliation run triggered successfully.', 'success');
      },
      error: () => this.notifications.push('Failed to trigger reconciliation run.', 'error')
    });
  }

  selectBreak(breakItem: BreakItem): void {
    if (this.pendingSelectionLockUntil > Date.now() && breakItem.status === BreakStatus.Open) {
      return;
    }
    this.pendingSelectionLockUntil = 0;
    this.selectedBreakSubject.next(breakItem);
  }

  addComment(breakId: number, comment: string, action: string): void {
    this.api.addComment(breakId, comment, action).subscribe({
      next: (updated: BreakItem) => {
        this.updateBreak(updated);
        this.refreshActivity();
        this.notifications.push('Comment added to break.', 'success');
        this.breakEventsSubject.next();
        this.loadApprovalQueue();
      },
      error: (err) => {
        const message = err?.error?.details ?? err?.error?.message ??
          'Failed to add comment. You may not have permission.';
        this.notifications.push(message, 'error');
      }
    });
  }

  updateStatus(breakId: number, payload: { status: BreakStatus; comment?: string; correlationId?: string }): void {
    this.api.updateStatus(breakId, payload).subscribe({
      next: (updated: BreakItem) => {
        this.updateBreak(updated);
        this.refreshActivity();
        const statusText = payload.status.replace(/_/g, ' ').toLowerCase();
        this.notifications.push(`Break ${breakId} ${statusText}.`, 'success');
        this.breakEventsSubject.next();
        this.loadApprovalQueue();
      },
      error: (err) => {
        const message = err?.error?.details ?? err?.error?.message ?? 'Status update failed.';
        this.notifications.push(message, 'error');
      }
    });
  }

  /**
   * Applies a bulk workflow update and mirrors the backend's correlation identifier so audit trails can be
   * reconciled between UI cues and server-side logs.
   */
  bulkUpdateBreaks(payload: BulkBreakUpdatePayload): void {
    const enriched: BulkBreakUpdatePayload = {
      ...payload,
      correlationId: payload.correlationId ?? this.generateCorrelationId('bulk')
    };
    this.api.bulkUpdateBreaks(enriched).subscribe({
      next: (response: BulkBreakUpdateResponse) => {
        if (response.successes.length > 0) {
          this.applyBreakUpdates(response.successes);
          this.selectedBreakSubject.next(response.successes[0]);
          const summary = response.successes
            .map((item) => `Break ${item.id} - Status: ${item.status}`)
            .join('; ');
          this.workflowSummarySubject.next(summary);
          this.pendingSelectionLockUntil = Date.now() + 15000;
          this.notifications.push(
            `${response.successes.length} break${response.successes.length === 1 ? '' : 's'} updated successfully.`,
            'success'
          );
        } else if (response.failures.length === 0) {
          this.workflowSummarySubject.next(null);
        }
        if (response.failures.length > 0) {
          const details = response.failures
            .map((failure) => `#${failure.breakId}: ${failure.reason}`)
            .slice(0, 3)
            .join('; ');
          this.notifications.push(
            `${response.failures.length} break${response.failures.length === 1 ? '' : 's'} failed to update. ${details}`,
            'error'
          );
          if (response.successes.length === 0) {
            this.workflowSummarySubject.next(
              `Bulk update failed for ${response.failures.length} break${response.failures.length === 1 ? '' : 's'}.`
            );
          }
        }
        this.refreshActivity();
        const selected = this.selectedReconciliationSubject.value;
        if (selected) {
          this.fetchLatestRun(selected.id);
        }
        this.loadApprovalQueue();
        this.breakEventsSubject.next();
      },
      error: (err) => {
        const message = err?.error?.details ?? err?.error?.message ?? 'Bulk update failed.';
        this.notifications.push(message, 'error');
      }
    });
  }

  exportLatestRun(): Observable<Blob> {
    const detail = this.runDetailSubject.value;
    if (!detail || detail.summary.runId == null) {
      return throwError(() => new Error('No reconciliation run available for export'));
    }
    return this.api.exportRun(detail.summary.runId);
  }

  reset(): void {
    this.reconciliationsSubject.next([]);
    this.selectedReconciliationSubject.next(null);
    this.runDetailSubject.next(null);
    this.selectedBreakSubject.next(null);
    this.filterSubject.next({});
    this.filterMetadataSubject.next(null);
    this.activitySubject.next([]);
    this.approvalsSubject.next([]);
    this.approvalMetadataSubject.next(null);
    this.workflowSummarySubject.next(null);
  }

  getCurrentRunDetail(): RunDetail | null {
    return this.runDetailSubject.value;
  }

  getCurrentBreak(): BreakItem | null {
    return this.selectedBreakSubject.value;
  }

  updateFilter(filter: BreakFilter): void {
    this.filterSubject.next(filter);
    const selected = this.selectedReconciliationSubject.value;
    if (selected) {
      this.fetchLatestRun(selected.id);
    }
  }

  getCurrentFilter(): BreakFilter {
    return this.filterSubject.value;
  }

  private fetchLatestRun(reconciliationId: number): void {
    const filter = this.filterSubject.value;
    const selectedBreakId = this.selectedBreakSubject.value?.id ?? null;
    this.api.getLatestRun(reconciliationId, filter).subscribe({
      next: (detail: RunDetail) => {
        this.runDetailSubject.next(detail);
        this.filterMetadataSubject.next(detail.filters);
        const currentSelection = this.selectedBreakSubject.value;
        const nextSelection = selectedBreakId != null
          ? detail.breaks.find((item: BreakItem) => item.id === selectedBreakId) ?? currentSelection ?? detail.breaks[0] ?? null
          : detail.breaks[0] ?? null;
        this.selectedBreakSubject.next(nextSelection);
      },
      error: () => this.notifications.push('Failed to load reconciliation run.', 'error')
    });
  }

  loadApprovalQueue(reconciliationId?: number): void {
    const targetId = reconciliationId ?? this.selectedReconciliationSubject.value?.id;
    if (!targetId) {
      this.approvalsSubject.next([]);
      this.approvalMetadataSubject.next(null);
      return;
    }
    this.api.getApprovalQueue(targetId).subscribe({
      next: (response) => {
        this.approvalsSubject.next(response.pendingBreaks);
        this.approvalMetadataSubject.next(response.filterMetadata);
      },
      error: (err) => {
        const message = err?.error?.message ?? 'Failed to load approvals queue.';
        this.notifications.push(message, 'error');
        this.approvalsSubject.next([]);
      }
    });
  }

  private updateBreak(updated: BreakItem): void {
    const currentDetail = this.runDetailSubject.value;
    if (!currentDetail) {
      return;
    }
    const updatedBreaks = currentDetail.breaks.map((item: BreakItem) => (item.id === updated.id ? updated : item));
    const newDetail: RunDetail = { ...currentDetail, breaks: updatedBreaks };
    this.runDetailSubject.next(newDetail);
    if (this.selectedBreakSubject.value?.id === updated.id) {
      this.selectedBreakSubject.next(updated);
    }
  }

  private applyBreakUpdates(updated: BreakItem[]): void {
    const currentDetail = this.runDetailSubject.value;
    if (!currentDetail || updated.length === 0) {
      return;
    }
    const map = new Map(updated.map((item: BreakItem) => [item.id, item] as const));
    const refreshedBreaks = currentDetail.breaks.map((item: BreakItem) => map.get(item.id) ?? item);
    const newDetail: RunDetail = { ...currentDetail, breaks: refreshedBreaks };
    this.runDetailSubject.next(newDetail);
    const selected = this.selectedBreakSubject.value;
    if (selected && map.has(selected.id)) {
      this.selectedBreakSubject.next(map.get(selected.id)!);
    }
  }

  private refreshActivity(): void {
    this.api.getSystemActivity().subscribe({
      next: (entries: SystemActivityEntry[]) => {
        this.activitySubject.next(entries);
      },
      error: () => this.notifications.push('Unable to refresh system activity.', 'error')
    });
  }

  private generateCorrelationId(prefix: string): string {
    return `${prefix}-${Date.now()}`;
  }
}
