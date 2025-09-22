import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import { ReconciliationStateService } from './services/reconciliation-state.service';
import {
  BreakItem,
  ReconciliationListItem
} from './models/api-models';
import { BreakStatus } from './models/break-status';
import { LoginComponent } from './components/login/login.component';
import { ReconciliationListComponent } from './components/reconciliation-list/reconciliation-list.component';
import { RunDetailComponent } from './components/run-detail/run-detail.component';
import { BreakDetailComponent } from './components/break-detail/break-detail.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    AsyncPipe,
    LoginComponent,
    ReconciliationListComponent,
    RunDetailComponent,
    BreakDetailComponent
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
  readonly breakStatusOptions = [BreakStatus.Open, BreakStatus.PendingApproval, BreakStatus.Closed];

  loginError: string | null = null;
  isLoading = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: ApiService,
    public readonly session: SessionService,
    private readonly state: ReconciliationStateService
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
        next: (response) => {
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
  }

  handleSelectReconciliation(reconciliation: ReconciliationListItem): void {
    this.state.selectReconciliation(reconciliation);
  }

  handleTriggerRun(): void {
    this.state.triggerRun();
  }

  handleSelectBreak(breakItem: BreakItem): void {
    this.state.selectBreak(breakItem);
  }

  handleAddComment(event: { breakId: number; comment: string; action: string }): void {
    this.state.addComment(event.breakId, event.comment, event.action);
  }

  handleStatusChange(event: { breakId: number; status: BreakStatus }): void {
    this.state.updateStatus(event.breakId, event.status);
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
      .subscribe((blob) => {
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `reconciliation-run-${runId}.xlsx`;
        anchor.click();
        window.URL.revokeObjectURL(url);
      });
  }
}
