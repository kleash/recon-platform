import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ReconciliationStateService } from '../../services/reconciliation-state.service';
import { SessionService } from '../../services/session.service';
import {
  BreakItem,
  BulkBreakUpdatePayload,
  ReconciliationListItem,
  RunDetail,
  TriggerRunPayload
} from '../../models/api-models';
import { BreakStatus } from '../../models/break-status';
import { BreakFilter } from '../../models/break-filter';
import { ReconciliationListComponent } from '../reconciliation-list/reconciliation-list.component';
import { RunDetailComponent } from '../run-detail/run-detail.component';
import { BreakDetailComponent } from '../break-detail/break-detail.component';
import { SystemActivityComponent } from '../system-activity/system-activity.component';
import { CheckerQueueComponent } from '../checker-queue/checker-queue.component';

@Component({
  selector: 'urp-analyst-workspace',
  standalone: true,
  imports: [
    CommonModule,
    AsyncPipe,
    ReconciliationListComponent,
    RunDetailComponent,
    BreakDetailComponent,
    SystemActivityComponent,
    CheckerQueueComponent
  ],
  templateUrl: './analyst-workspace.component.html',
  styleUrls: ['./analyst-workspace.component.css']
})
export class AnalystWorkspaceComponent implements OnInit, OnDestroy {
  readonly reconciliations$ = this.state.reconciliations$;
  readonly selectedReconciliation$ = this.state.selectedReconciliation$;
  readonly runDetail$ = this.state.runDetail$;
  readonly selectedBreak$ = this.state.selectedBreak$;
  readonly filter$ = this.state.filter$;
  readonly filterMetadata$ = this.state.filterMetadata$;
  readonly activity$ = this.state.activity$;

  readonly BreakStatus = BreakStatus;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly state: ReconciliationStateService,
    public readonly session: SessionService
  ) {}

  ngOnInit(): void {
    this.state.loadReconciliations();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleSelectReconciliation(reconciliation: ReconciliationListItem): void {
    this.state.selectReconciliation(reconciliation);
  }

  handleTriggerRun(payload: TriggerRunPayload): void {
    this.state.triggerRun(payload);
  }

  handleSelectBreak(breakItem: BreakItem): void {
    this.state.selectBreak(breakItem);
  }

  handleAddComment(event: { breakId: number; comment: string; action: string }): void {
    this.state.addComment(event.breakId, event.comment, event.action);
  }

  handleStatusChange(event: { breakId: number; status: BreakStatus; comment?: string; correlationId?: string }): void {
    this.state.updateStatus(event.breakId, {
      status: event.status,
      comment: event.comment,
      correlationId: event.correlationId
    });
  }

  handleFilterChange(filter: BreakFilter): void {
    this.state.updateFilter(filter);
  }

  handleBulkAction(payload: BulkBreakUpdatePayload): void {
    this.state.bulkUpdateBreaks(payload);
  }

  getPendingApprovalBreaks(detail: RunDetail): BreakItem[] {
    return detail?.breaks?.filter((item) => item.status === BreakStatus.PendingApproval) ?? [];
  }

  handleQueueApprove(event: { breakIds: number[]; comment: string }): void {
    this.state.bulkUpdateBreaks({
      breakIds: event.breakIds,
      status: BreakStatus.Closed,
      comment: event.comment,
      action: 'QUEUE_APPROVE'
    });
  }

  handleQueueReject(event: { breakIds: number[]; comment: string }): void {
    this.state.bulkUpdateBreaks({
      breakIds: event.breakIds,
      status: BreakStatus.Rejected,
      comment: event.comment,
      action: 'QUEUE_REJECT'
    });
  }

  handleExportRun(): void {
    const runDetail = this.state.getCurrentRunDetail();
    const runId = runDetail?.summary.runId;
    if (!runId) {
      return;
    }
    this.state
      .exportLatestRun()
      .pipe(takeUntil(this.destroy$))
      .subscribe((blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `reconciliation-run-${runId}.xlsx`;
        anchor.click();
        window.URL.revokeObjectURL(url);
      });
  }
}
