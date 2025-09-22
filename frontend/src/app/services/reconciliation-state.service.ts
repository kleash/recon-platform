import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { ApiService } from './api.service';
import {
  BreakItem,
  ReconciliationListItem,
  RunDetail
} from '../models/api-models';
import { BreakStatus } from '../models/break-status';

@Injectable({ providedIn: 'root' })
export class ReconciliationStateService {
  private readonly reconciliationsSubject = new BehaviorSubject<ReconciliationListItem[]>([]);
  private readonly selectedReconciliationSubject = new BehaviorSubject<ReconciliationListItem | null>(null);
  private readonly runDetailSubject = new BehaviorSubject<RunDetail | null>(null);
  private readonly selectedBreakSubject = new BehaviorSubject<BreakItem | null>(null);

  readonly reconciliations$ = this.reconciliationsSubject.asObservable();
  readonly selectedReconciliation$ = this.selectedReconciliationSubject.asObservable();
  readonly runDetail$ = this.runDetailSubject.asObservable();
  readonly selectedBreak$ = this.selectedBreakSubject.asObservable();

  constructor(private readonly api: ApiService) {}

  loadReconciliations(): void {
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
      }
    });
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
    this.api.triggerRun(selected.id).subscribe((detail) => {
      this.runDetailSubject.next(detail);
      this.selectedBreakSubject.next(detail.breaks[0] ?? null);
    });
  }

  selectBreak(breakItem: BreakItem): void {
    this.selectedBreakSubject.next(breakItem);
  }

  addComment(breakId: number, comment: string, action: string): void {
    this.api.addComment(breakId, comment, action).subscribe((updated) => {
      this.updateBreak(updated);
    });
  }

  updateStatus(breakId: number, status: BreakStatus): void {
    this.api.updateStatus(breakId, status).subscribe((updated) => {
      this.updateBreak(updated);
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
  }

  getCurrentRunDetail(): RunDetail | null {
    return this.runDetailSubject.value;
  }

  private fetchLatestRun(reconciliationId: number): void {
    this.api.getLatestRun(reconciliationId).subscribe((detail) => {
      this.runDetailSubject.next(detail);
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
}
