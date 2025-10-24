import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Subject } from 'rxjs';
import { DateTime } from 'luxon';
import { takeUntil } from 'rxjs/operators';
import { BreakStatus } from '../../models/break-status';
import {
  BreakResultRow,
  ExportJobSummary,
  GridColumn,
  ReconciliationListItem,
  SavedView,
  SavedViewRequestPayload,
  TriggerRunPayload
} from '../../models/api-models';
import { ReconciliationListComponent } from '../reconciliation-list/reconciliation-list.component';
import { SystemActivityComponent } from '../system-activity/system-activity.component';
import { ResultGridComponent } from '../result-grid/result-grid.component';
import { RunDetailComponent } from '../run-detail/run-detail.component';
import { CheckerQueueComponent } from '../checker-queue/checker-queue.component';
import { ReconciliationStateService } from '../../services/reconciliation-state.service';
import { ResultGridStateService, ExportFormat } from '../../services/result-grid-state.service';
import { SessionService } from '../../services/session.service';
import { NotificationService } from '../../services/notification.service';

type WorkspaceTab = 'runs' | 'breaks' | 'approvals' | 'reports';

interface ColumnFilterRow {
  id: number;
  columnKey: string;
  operator: string;
  value: string;
}

/**
 * Shell container for the analyst workspace. Coordinates reconciliation selection, grid filters, and
 * cross-widget refresh behaviour so the underlying services remain stateful but presentation stays declarative.
 */
@Component({
    selector: 'urp-analyst-workspace',
    imports: [
        CommonModule,
        FormsModule,
        AsyncPipe,
        ReconciliationListComponent,
        SystemActivityComponent,
        ResultGridComponent,
        RunDetailComponent,
        CheckerQueueComponent
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
  readonly runDetail$ = this.state.runDetail$;
  readonly filterMetadata$ = this.state.filterMetadata$;
  readonly approvals$ = this.state.approvals$;
  readonly approvalMetadata$ = this.state.approvalMetadata$;
  readonly filter$ = this.state.filter$;
  readonly workflowSummary$ = this.state.workflowSummary$;

  readonly BreakStatus = BreakStatus;
  readonly allStatuses = Object.values(BreakStatus);
  readonly triggerTypeOptions = ['MANUAL_API', 'SCHEDULED_CRON', 'EXTERNAL_API', 'KAFKA_EVENT'];

  activeTab: WorkspaceTab = 'runs';
  dateRange = { from: this.todayInSgt(), to: this.todayInSgt() };
  statusFilters = new Set<BreakStatus>();
  triggerTypeFilters = new Set<string>();
  runIdFilter = '';
  searchTerm = '';
  bulkComment = '';
  newView = { name: '', shared: false, defaultView: false, description: '' };

  columnFilters: ColumnFilterRow[] = [];
  private columnFilterId = 0;
  availableColumns: GridColumn[] = [];

  selectedBreakIds: number[] = [];
  selectedRow: BreakResultRow | null = null;
  activeBreakId: number | null = null;
  hasInlineDetail = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    public readonly state: ReconciliationStateService,
    public readonly resultState: ResultGridStateService,
    public readonly session: SessionService,
    private readonly notifications: NotificationService
  ) {}

  ngOnInit(): void {
    this.state.loadReconciliations();

    this.state.selectedReconciliation$
      .pipe(takeUntil(this.destroy$))
      .subscribe((reconciliation) => {
        this.resultState.setReconciliation(reconciliation);
        if (reconciliation) {
          this.applyFilters();
        } else {
          this.selectedBreakIds = [];
          this.selectedRow = null;
          this.activeBreakId = null;
        }
      });

    this.state.breakEvents$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        // Refresh the grid and re-select the active break so inline detail stays in sync after workflow updates.
        const currentBreak = this.state.getCurrentBreak();
        this.resultState.refresh();
        if (currentBreak) {
          setTimeout(() => this.state.selectBreak(currentBreak), 0);
        }
      });

    this.columns$
      .pipe(takeUntil(this.destroy$))
      .subscribe((columns) => {
        this.availableColumns = columns ?? [];
        const validKeys = new Set(this.availableColumns.map((column) => column.key));
        this.columnFilters = this.columnFilters
          .filter((row) => validKeys.has(row.columnKey))
          .map((row) => this.ensureOperator(row));
      });

    this.rows$
      .pipe(takeUntil(this.destroy$))
      .subscribe((rows) => {
        if (!rows) {
          this.hasInlineDetail = false;
          return;
        }
        if (this.activeBreakId != null) {
          const match = rows.find((row) => row.breakId === this.activeBreakId) ?? null;
          this.selectedRow = match;
          this.hasInlineDetail = !!match;
        } else {
          this.hasInlineDetail = false;
        }
      });

    this.selectedBreak$
      .pipe(takeUntil(this.destroy$))
      .subscribe((selected) => {
        this.activeBreakId = selected?.id ?? null;
        if (!selected) {
          this.hasInlineDetail = false;
          this.selectedRow = null;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  setActiveTab(tab: WorkspaceTab): void {
    if (this.activeTab === tab) {
      return;
    }
    this.activeTab = tab;
    if (tab === 'runs') {
      this.resultState.refresh();
    } else if (tab === 'approvals') {
      this.state.loadApprovalQueue();
    } else if (tab === 'reports') {
      this.resultState.refreshExportHistory();
    }
  }

  isActiveTab(tab: WorkspaceTab): boolean {
    return this.activeTab === tab;
  }

  handleSelectReconciliation(reconciliation: ReconciliationListItem): void {
    this.state.selectReconciliation(reconciliation);
  }

  handleTriggerRun(payload: TriggerRunPayload): void {
    this.state.triggerRun(payload);
  }

  handleExportLatestRun(): void {
    this.state.exportLatestRun().subscribe({
      next: (blob) => {
        const fileName = `run-export-${DateTime.now().toFormat('yyyyLLdd-HHmmss')}.csv`;
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
        this.notifications.push('Run export downloaded successfully.', 'success');
      },
      error: () => this.notifications.push('Failed to export latest run.', 'error')
    });
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
    if (this.triggerTypeFilters.size > 0) {
      query['runType'] = Array.from(this.triggerTypeFilters);
    }
    const runIds = this.normaliseFilterValues(this.runIdFilter);
    if (runIds.length > 0) {
      query['runId'] = runIds;
    }
    if (this.searchTerm.trim().length > 0) {
      query['search'] = this.searchTerm.trim();
    }

    this.columnFilters.forEach((row) => {
      const values = this.normaliseFilterValues(row.value);
      if (values.length === 0) {
        return;
      }
      query[`filter.${row.columnKey}`] = values;
      query[`operator.${row.columnKey}`] = row.operator;
    });

    this.resultState.replaceQuery(query);
    this.selectedBreakIds = [];
  }

  resetFilters(): void {
    this.statusFilters.clear();
    this.triggerTypeFilters.clear();
    this.runIdFilter = '';
    this.searchTerm = '';
    this.columnFilters = [];
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

  handleTriggerTypeToggle(triggerType: string, checked: boolean): void {
    if (checked) {
      this.triggerTypeFilters.add(triggerType);
    } else {
      this.triggerTypeFilters.delete(triggerType);
    }
  }

  addColumnFilter(): void {
    if (this.availableColumns.length === 0) {
      return;
    }
    const firstColumn = this.availableColumns[0];
    const defaultOperator = firstColumn.operators[0] ?? 'EQUALS';
    this.columnFilters = [
      ...this.columnFilters,
      {
        id: ++this.columnFilterId,
        columnKey: firstColumn.key,
        operator: defaultOperator,
        value: ''
      }
    ];
  }

  removeColumnFilter(filterId: number): void {
    this.columnFilters = this.columnFilters.filter((row) => row.id !== filterId);
  }

  handleColumnKeyChange(filterId: number, columnKey: string): void {
    this.columnFilters = this.columnFilters.map((row) => {
      if (row.id !== filterId) {
        return row;
      }
      const defaultOperator = this.defaultOperator(columnKey);
      return { ...row, columnKey, operator: defaultOperator };
    });
  }

  handleColumnOperatorChange(filterId: number, operator: string): void {
    this.columnFilters = this.columnFilters.map((row) => (row.id === filterId ? { ...row, operator } : row));
  }

  handleColumnValueChange(filterId: number, value: string): void {
    this.columnFilters = this.columnFilters.map((row) => (row.id === filterId ? { ...row, value } : row));
  }

  operatorOptions(columnKey: string): string[] {
    const column = this.availableColumns.find((entry) => entry.key === columnKey);
    return column?.operators ?? ['EQUALS'];
  }

  columnLabel(columnKey: string): string {
    const column = this.availableColumns.find((entry) => entry.key === columnKey);
    return column?.label ?? columnKey;
  }

  handleRowSelect(row: BreakResultRow): void {
    this.selectedRow = row;
    this.activeBreakId = row.breakId;
    this.hasInlineDetail = true;
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

  handleAddComment(event: { breakId: number; comment: string; action: string }): void {
    this.state.addComment(event.breakId, event.comment, event.action);
  }

  handleUpdateStatus(event: { breakId: number; status: BreakStatus; comment?: string; correlationId?: string }): void {
    this.state.updateStatus(event.breakId, {
      status: event.status,
      comment: event.comment,
      correlationId: event.correlationId
    });
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

  downloadExport(job: ExportJobSummary): void {
    this.resultState.downloadExport(job);
  }

  refreshApprovals(): void {
    this.state.loadApprovalQueue();
  }

  handleApproval(event: { breakIds: number[]; comment: string }, status: BreakStatus): void {
    if (event.breakIds.length === 0) {
      return;
    }
    this.state.bulkUpdateBreaks({
      breakIds: event.breakIds,
      status,
      comment: event.comment
    });
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
  }

  private syncFiltersFromSavedView(view: SavedView): void {
    try {
      const payload = JSON.parse(view.settingsJson ?? '{}');
      const query = (payload?.query ?? {}) as Record<string, unknown>;

      this.dateRange.from = String(query['fromDate'] ?? this.todayInSgt());
      this.dateRange.to = String(query['toDate'] ?? this.todayInSgt());
      this.searchTerm = String(query['search'] ?? '');

      this.statusFilters.clear();
      const statuses = query['status'];
      if (Array.isArray(statuses)) {
        statuses.forEach((status) => this.statusFilters.add(status as BreakStatus));
      } else if (typeof statuses === 'string' && statuses) {
        this.statusFilters.add(statuses as BreakStatus);
      }

      this.triggerTypeFilters.clear();
      const triggerTypes = query['runType'];
      if (Array.isArray(triggerTypes)) {
        triggerTypes.forEach((type) => this.triggerTypeFilters.add(String(type)));
      } else if (typeof triggerTypes === 'string' && triggerTypes) {
        this.triggerTypeFilters.add(triggerTypes);
      }

      const runIds = query['runId'];
      if (Array.isArray(runIds)) {
        this.runIdFilter = runIds.map(String).join(', ');
      } else if (typeof runIds === 'string') {
        this.runIdFilter = runIds;
      } else {
        this.runIdFilter = '';
      }

      this.columnFilters = [];
      Object.entries(query)
        .filter(([key]) => key.startsWith('filter.'))
        .forEach(([key, value]) => {
          const columnKey = key.substring('filter.'.length);
          const operatorKey = query[`operator.${columnKey}`];
          const values = Array.isArray(value) ? value.map(String) : [String(value)];
          const operator = Array.isArray(operatorKey)
            ? String(operatorKey[0])
            : typeof operatorKey === 'string'
            ? operatorKey
            : this.defaultOperator(columnKey);
          this.columnFilters.push({
            id: ++this.columnFilterId,
            columnKey,
            operator,
            value: values.join(', ')
          });
        });
    } catch {
      // ignore parsing errors; filter UI will remain unchanged
    }
  }

  private ensureOperator(row: ColumnFilterRow): ColumnFilterRow {
    const options = this.operatorOptions(row.columnKey);
    if (options.includes(row.operator)) {
      return row;
    }
    return { ...row, operator: options[0] ?? 'EQUALS' };
  }

  private defaultOperator(columnKey: string): string {
    const options = this.operatorOptions(columnKey);
    return options[0] ?? 'EQUALS';
  }

  private normaliseFilterValues(raw: string): string[] {
    return raw
      .split(/\r?\n|,/)
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0);
  }

  private todayInSgt(): string {
    const nowInSgt = DateTime.now().setZone('Asia/Singapore');
    return nowInSgt.toISODate() ?? nowInSgt.toFormat('yyyy-LL-dd');
  }
}
