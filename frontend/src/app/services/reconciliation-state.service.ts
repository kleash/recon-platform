import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { ApiService } from './api.service';
import {
  BreakItem,
  ReconciliationListItem,
  RunDetail,
  FilterMetadata,
  SystemActivityEntry
} from '../models/api-models';
import { BreakStatus } from '../models/break-status';
import { BreakFilter } from '../models/break-filter';

@Injectable({ providedIn: 'root' })
export class ReconciliationStateService {
  private readonly reconciliationsSubject = new BehaviorSubject<ReconciliationListItem[]>([]);
  private readonly selectedReconciliationSubject = new BehaviorSubject<ReconciliationListItem | null>(null);
  private readonly runDetailSubject = new BehaviorSubject<RunDetail | null>(null);
  private readonly selectedBreakSubject = new BehaviorSubject<BreakItem | null>(null);
  private readonly filterSubject = new BehaviorSubject<BreakFilter>({});
  private readonly filterMetadataSubject = new BehaviorSubject<FilterMetadata | null>(null);
  private readonly activitySubject = new BehaviorSubject<SystemActivityEntry[]>([]);

  readonly reconciliations$ = this.reconciliationsSubject.asObservable();
  readonly selectedReconciliation$ = this.selectedReconciliationSubject.asObservable();
  readonly runDetail$ = this.runDetailSubject.asObservable();
  readonly selectedBreak$ = this.selectedBreakSubject.asObservable();
  readonly filter$ = this.filterSubject.asObservable();
  readonly filterMetadata$ = this.filterMetadataSubject.asObservable();
  readonly activity$ = this.activitySubject.asObservable();

  constructor(private readonly api: ApiService) {}

  loadReconciliations(): void {
    this.filterSubject.next({});
    this.api.getReconciliations().subscribe((data) => {
      this.reconciliationsSubject.next(data);
      const currentSelection = this.selectedReconciliationSubject.value;
      if (currentSelection && data.some((item) => item.id === currentSelection.id)) {
        this.fetchLatestRun(currentSelection.id);
        return;
      }
      const nextSelection = data.length > 0 ? data[0] : null;
      this.selectedReconciliationSubject.next(nextSelection);
      if (nextSelection) {
        this.fetchLatestRun(nextSelection.id);
      } else {
        this.runDetailSubject.next(null);
        this.selectedBreakSubject.next(null);
        this.filterMetadataSubject.next(null);
      }
    });
    this.refreshActivity();
  }

  selectReconciliation(reconciliation: ReconciliationListItem): void {
    this.selectedReconciliationSubject.next(reconciliation);
    this.selectedBreakSubject.next(null);
    this.fetchLatestRun(reconciliation.id);
  }

  triggerRun(): void {
    const selected = this.selectedReconciliationSubject.value;
    if (!selected) {
      return;
    }
    this.api.triggerRun(selected.id).subscribe(() => {
      this.fetchLatestRun(selected.id);
      this.refreshActivity();
    });
  }

  selectBreak(breakItem: BreakItem): void {
    this.selectedBreakSubject.next(breakItem);
  }

  addComment(breakId: number, comment: string, action: string): void {
    this.api.addComment(breakId, comment, action).subscribe((updated) => {
      this.updateBreak(updated);
      this.refreshActivity();
    });
  }

  updateStatus(breakId: number, status: BreakStatus): void {
    this.api.updateStatus(breakId, status).subscribe((updated) => {
      this.updateBreak(updated);
      this.refreshActivity();
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
  }

  getCurrentRunDetail(): RunDetail | null {
    return this.runDetailSubject.value;
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
    this.api.getLatestRun(reconciliationId, filter).subscribe((detail) => {
      this.runDetailSubject.next(detail);
      this.filterMetadataSubject.next(detail.filters);
      this.selectedBreakSubject.next(detail.breaks[0] ?? null);
    });
  }

  private updateBreak(updated: BreakItem): void {
    const currentDetail = this.runDetailSubject.value;
    if (!currentDetail) {
      return;
    }
    const updatedBreaks = currentDetail.breaks.map((item) => (item.id === updated.id ? updated : item));
    const newDetail: RunDetail = { ...currentDetail, breaks: updatedBreaks };
    this.runDetailSubject.next(newDetail);
    if (this.selectedBreakSubject.value?.id === updated.id) {
      this.selectedBreakSubject.next(updated);
    }
  }

  private refreshActivity(): void {
    this.api.getSystemActivity().subscribe((entries) => {
      this.activitySubject.next(entries);
    });
  }
}
