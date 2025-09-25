import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import { ReconciliationStateService } from './services/reconciliation-state.service';
import { NotificationService } from './services/notification.service';
import {
  BreakItem,
  BulkBreakUpdatePayload,
  LoginResponse,
  ReconciliationListItem,
  RunDetail,
  TriggerRunPayload
} from './models/api-models';
import { BreakStatus } from './models/break-status';
import { BreakFilter } from './models/break-filter';
import { LoginComponent } from './components/login/login.component';
import { ReconciliationListComponent } from './components/reconciliation-list/reconciliation-list.component';
import { RunDetailComponent } from './components/run-detail/run-detail.component';
import { BreakDetailComponent } from './components/break-detail/break-detail.component';
import { SystemActivityComponent } from './components/system-activity/system-activity.component';
import { CheckerQueueComponent } from './components/checker-queue/checker-queue.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    AsyncPipe,
    LoginComponent,
    ReconciliationListComponent,
    RunDetailComponent,
    BreakDetailComponent,
    SystemActivityComponent,
    CheckerQueueComponent
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Universal Reconciliation Platform';

  readonly reconciliations$ = this.state.reconciliations$;
  readonly selectedReconciliation$ = this.state.selectedReconciliation$;
  readonly runDetail$ = this.state.runDetail$;
  readonly selectedBreak$ = this.state.selectedBreak$;
  readonly filter$ = this.state.filter$;
  readonly filterMetadata$ = this.state.filterMetadata$;
  readonly activity$ = this.state.activity$;

  loginError: string | null = null;
  isLoading = false;

  private readonly destroy$ = new Subject<void>();
  readonly BreakStatus = BreakStatus;

  constructor(
    private readonly api: ApiService,
    public readonly session: SessionService,
    private readonly state: ReconciliationStateService,
    public readonly notifications: NotificationService
  ) {}

  ngOnInit(): void {
    if (this.session.isAuthenticated()) {
      this.state.loadReconciliations();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleLogin(credentials: { username: string; password: string }): void {
    this.isLoading = true;
    this.loginError = null;
    this.api
      .login(credentials.username, credentials.password)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: LoginResponse) => {
          this.session.storeSession(response);
          this.isLoading = false;
          this.state.loadReconciliations();
        },
        error: () => {
          this.loginError = 'Login failed. Please verify your credentials.';
          this.isLoading = false;
        }
      });
  }

  logout(): void {
    this.session.clear();
    this.state.reset();
    this.loginError = null;
    this.notifications.clear();
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

  dismissNotification(id: number): void {
    this.notifications.dismiss(id);
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
