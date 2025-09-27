import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { BreakStatus } from '../../models/break-status';
import {
  BreakResultRow,
  ExportJobSummary,
  ReconciliationListItem,
  SavedView,
  SavedViewRequestPayload
} from '../../models/api-models';
import { ReconciliationListComponent } from '../reconciliation-list/reconciliation-list.component';
import { BreakDetailComponent } from '../break-detail/break-detail.component';
import { SystemActivityComponent } from '../system-activity/system-activity.component';
import { ResultGridComponent } from '../result-grid/result-grid.component';
import { ReconciliationStateService } from '../../services/reconciliation-state.service';
import { ResultGridStateService, ExportFormat } from '../../services/result-grid-state.service';
import { SessionService } from '../../services/session.service';

@Component({
  selector: 'urp-analyst-workspace',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AsyncPipe,
    ReconciliationListComponent,
    BreakDetailComponent,
    SystemActivityComponent,
    ResultGridComponent
  ],
  templateUrl: './analyst-workspace.component.html',
  styleUrls: ['./analyst-workspace.component.css']
})
export class AnalystWorkspaceComponent implements OnInit, OnDestroy {
  readonly reconciliations$ = this.state.reconciliations$;
  readonly selectedReconciliation$ = this.state.selectedReconciliation$;
  readonly rows$ = this.resultState.rows$;
  readonly columns$ = this.resultState.columns$;
  readonly loading$ = this.resultState.loading$;
  readonly total$ = this.resultState.total$;
  readonly savedViews$ = this.resultState.savedViews$;
  readonly exportJobs$ = this.resultState.exportJobs$;
  readonly activeView$ = this.resultState.activeView$;
  readonly selectedBreak$ = this.state.selectedBreak$;
  readonly activity$ = this.state.activity$;

  readonly BreakStatus = BreakStatus;
  readonly allStatuses = Object.values(BreakStatus);

  dateRange = { from: this.todayInSgt(), to: this.todayInSgt() };
  statusFilters = new Set<BreakStatus>();
  searchTerm = '';
  bulkComment = '';
  newView = { name: '', shared: false, defaultView: false, description: '' };

  selectedBreakIds: number[] = [];
  selectedRow: BreakResultRow | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    public readonly state: ReconciliationStateService,
    public readonly resultState: ResultGridStateService,
    public readonly session: SessionService
  ) {}

  ngOnInit(): void {
    this.state.loadReconciliations();
    this.state.selectedReconciliation$
      .pipe(takeUntil(this.destroy$))
      .subscribe((reconciliation) => {
        this.resultState.setReconciliation(reconciliation);
        if (reconciliation) {
          this.applyFilters();
        }
      });

    this.state.breakEvents$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.resultState.refresh());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleSelectReconciliation(reconciliation: ReconciliationListItem): void {
    this.state.selectReconciliation(reconciliation);
  }

  applyFilters(): void {
    if (!this.dateRange.from) {
      this.dateRange.from = this.todayInSgt();
    }
    if (!this.dateRange.to) {
      this.dateRange.to = this.todayInSgt();
    }
    const query: Record<string, string | number | boolean | Array<string>> = {
      fromDate: this.dateRange.from,
      toDate: this.dateRange.to,
      size: 200,
      includeTotals: true
    };
    if (this.statusFilters.size > 0) {
      query['status'] = Array.from(this.statusFilters);
    }
    if (this.searchTerm.trim().length > 0) {
      query['search'] = this.searchTerm.trim();
    }
    this.resultState.replaceQuery(query);
    this.selectedBreakIds = [];
  }

  resetFilters(): void {
    this.statusFilters.clear();
    this.searchTerm = '';
    this.dateRange = { from: this.todayInSgt(), to: this.todayInSgt() };
    this.applyFilters();
  }

  handleStatusToggle(status: BreakStatus, checked: boolean): void {
    if (checked) {
      this.statusFilters.add(status);
    } else {
      this.statusFilters.delete(status);
    }
  }

  handleRowSelect(row: BreakResultRow): void {
    this.selectedRow = row;
    this.state.selectBreak(row.breakItem);
  }

  handleSelectionChange(ids: number[]): void {
    this.selectedBreakIds = ids;
  }

  selectFiltered(): void {
    this.resultState
      .fetchFilteredSelection()
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ breakIds }) => {
        this.handleSelectionChange(breakIds);
      });
  }

  loadMore(): void {
    this.resultState.loadMore();
  }

  handleApplySavedView(view: SavedView): void {
    this.syncFiltersFromSavedView(view);
    this.resultState.applySavedView(view);
    this.selectedBreakIds = [];
  }

  saveCurrentView(form: NgForm): void {
    if (!this.newView.name.trim()) {
      return;
    }
    const payload: SavedViewRequestPayload = {
      name: this.newView.name.trim(),
      description: this.newView.description.trim(),
      shared: this.newView.shared,
      defaultView: this.newView.defaultView,
      settingsJson: ''
    };
    this.resultState.saveView(payload);
    form.resetForm({ name: '', shared: false, defaultView: false, description: '' });
  }

  deleteView(view: SavedView): void {
    this.resultState.deleteView(view.id);
  }

  markDefault(view: SavedView): void {
    this.resultState.setDefaultView(view.id);
  }

  queueExport(format: ExportFormat): void {
    this.resultState.queueExport(format);
  }

  refreshExport(job: ExportJobSummary): void {
    this.resultState.refreshExportJob(job.id);
  }

  bulkUpdate(status: BreakStatus): void {
    if (this.selectedBreakIds.length === 0) {
      return;
    }
    this.state.bulkUpdateBreaks({
      breakIds: this.selectedBreakIds,
      status,
      comment: this.bulkComment.trim() || undefined
    });
    this.bulkComment = '';
    this.resultState.refresh();
  }

  private syncFiltersFromSavedView(view: SavedView): void {
    try {
      const payload = JSON.parse(view.settingsJson ?? '{}');
      const query = (payload?.query ?? {}) as Record<string, unknown>;
      this.dateRange.from = String(query['fromDate'] ?? this.todayInSgt());
      this.dateRange.to = String(query['toDate'] ?? this.todayInSgt());
      this.searchTerm = String(query['search'] ?? '');
      const statuses = query['status'];
      this.statusFilters.clear();
      if (Array.isArray(statuses)) {
        statuses.forEach((status) => this.statusFilters.add(status as BreakStatus));
      }
    } catch {
      // ignore parsing errors; filter UI will remain unchanged
    }
  }

  private todayInSgt(): string {
    const now = new Date();
    const utcMillis = now.getTime() + now.getTimezoneOffset() * 60000;
    const sgtMillis = utcMillis + 8 * 60 * 60000;
    return new Date(sgtMillis).toISOString().slice(0, 10);
  }
}
