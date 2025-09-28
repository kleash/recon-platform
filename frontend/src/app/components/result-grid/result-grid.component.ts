import { animate, state, style, transition, trigger } from '@angular/animations';
import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { BreakResultRow, GridColumn } from '../../models/api-models';
import { BreakStatus } from '../../models/break-status';
import { BreakDetailComponent } from '../break-detail/break-detail.component';

interface DetailRow {
  detailRow: true;
  element: BreakResultRow;
}

type TableRow = BreakResultRow | DetailRow;

@Component({
  selector: 'urp-result-grid',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSortModule,
    MatTableModule,
    BreakDetailComponent
  ],
  templateUrl: './result-grid.component.html',
  styleUrls: ['./result-grid.component.css'],
  animations: [
    trigger('detailExpand', [
      state(
        'collapsed',
        style({ height: '0px', minHeight: '0', visibility: 'hidden', opacity: 0 })
      ),
      state('expanded', style({ height: '*', visibility: 'visible', opacity: 1 })),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)'))
    ])
  ]
})
export class ResultGridComponent implements OnChanges {
  @Input() rows: BreakResultRow[] = [];
  @Input() columns: GridColumn[] = [];
  @Input() loading = false;
  @Input() total: number | null = null;
  @Input() selectedRowId: number | null = null;
  @Input() selectedBreakIds: number[] | null = null;
  @Input() workflowSummary: string | null = null;

  @Output() selectRow = new EventEmitter<BreakResultRow>();
  @Output() requestMore = new EventEmitter<void>();
  @Output() selectionChange = new EventEmitter<number[]>();
  @Output() selectFiltered = new EventEmitter<void>();
  @Output() addComment = new EventEmitter<{ breakId: number; comment: string; action: string }>();
  @Output() updateStatus = new EventEmitter<{
    breakId: number;
    status: BreakStatus;
    comment?: string;
    correlationId?: string;
  }>();

  readonly baseColumns: Array<{ key: string; label: string; sortable: boolean }> = [
    { key: 'breakId', label: 'Break ID', sortable: true },
    { key: 'runDateTime', label: 'Run Time', sortable: true },
    { key: 'status', label: 'Status', sortable: true },
    { key: 'breakType', label: 'Type', sortable: true },
    { key: 'triggerType', label: 'Trigger', sortable: true }
  ];

  readonly detailColumns = ['expandedDetail'];
  columnsWithExpand: string[] = [];
  tableData: TableRow[] = [];
  filteredRows: BreakResultRow[] = [];
  currentPageRows: BreakResultRow[] = [];
  filterText = '';
  pageIndex = 0;
  pageSize = 25;
  readonly pageSizeOptions = [25, 50, 100];
  sortState: Sort = { active: '', direction: '' };
  expandedRow: BreakResultRow | null = null;

  @ViewChild(MatPaginator) paginator?: MatPaginator;

  private selectedIds = new Set<number>();
  get selectedCount(): number {
    return this.selectedIds.size;
  }

  get paginatorLength(): number {
    if (this.filterText.trim().length > 0) {
      return this.filteredRows.length;
    }
    return this.total ?? this.filteredRows.length;
  }

  get isPageFullySelected(): boolean {
    return this.currentPageRows.length > 0 && this.currentPageRows.every((row) => this.selectedIds.has(row.breakId));
  }

  get isPagePartiallySelected(): boolean {
    return !this.isPageFullySelected && this.currentPageRows.some((row) => this.selectedIds.has(row.breakId));
  }

  readonly isExpansionDetailRow = (_: number, row: TableRow): row is DetailRow =>
    (row as DetailRow).detailRow === true;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedBreakIds']) {
      const incoming: number[] = changes['selectedBreakIds'].currentValue ?? [];
      this.selectedIds = new Set(incoming);
    }

    if (changes['rows'] && !changes['rows'].firstChange) {
      const existingIds = new Set(this.rows.map((row) => row.breakId));
      let removed = false;
      this.selectedIds.forEach((id) => {
        if (!existingIds.has(id)) {
          this.selectedIds.delete(id);
          removed = true;
        }
      });
      if (removed) {
        this.emitSelection();
      }
    }

    if (changes['rows'] || changes['columns']) {
      this.configureColumns();
      this.processData();
    }
  }

  toggleSelection(row: BreakResultRow, checked: boolean): void {
    if (checked) {
      this.selectedIds.add(row.breakId);
    } else {
      this.selectedIds.delete(row.breakId);
    }
    this.emitSelection();
  }

  isSelected(row: BreakResultRow): boolean {
    return this.selectedIds.has(row.breakId);
  }

  toggleSelectPage(): void {
    this.setPageSelection(!this.isPageFullySelected);
  }

  setPageSelection(checked: boolean): void {
    if (this.currentPageRows.length === 0) {
      return;
    }
    if (checked) {
      this.currentPageRows.forEach((row) => this.selectedIds.add(row.breakId));
    } else {
      this.currentPageRows.forEach((row) => this.selectedIds.delete(row.breakId));
    }
    this.emitSelection();
  }

  selectLoaded(): void {
    this.filteredRows.forEach((row) => this.selectedIds.add(row.breakId));
    this.emitSelection();
  }

  requestFilteredSelection(): void {
    this.selectFiltered.emit();
  }

  handleFilterChange(): void {
    this.pageIndex = 0;
    this.processData();
  }

  clearFilter(): void {
    if (!this.filterText) {
      return;
    }
    this.filterText = '';
    this.handleFilterChange();
  }

  handleSort(sort: Sort): void {
    this.sortState = sort.direction ? sort : { active: '', direction: '' };
    this.processData();
  }

  handlePage(event: PageEvent): void {
    if (event.pageSize !== this.pageSize) {
      this.pageSize = event.pageSize;
      this.pageIndex = 0;
    } else {
      this.pageIndex = event.pageIndex;
    }

    if (!this.filterText.trim()) {
      this.maybeRequestMoreFor(event.pageIndex, event.pageSize);
    }

    this.processData();
  }

  updatePageSize(size: number): void {
    if (this.pageSize === size) {
      return;
    }

    this.pageSize = size;
    this.pageIndex = 0;

    if (!this.filterText.trim()) {
      this.maybeRequestMoreFor(0, size);
    }

    this.processData();

    if (this.paginator) {
      this.paginator.firstPage();
    }
  }

  onRowClick(row: TableRow): void {
    if (!this.isBaseRow(row)) {
      return;
    }
    this.select(row);
    this.toggleExpandedRow(row);
  }

  toggleExpandedRow(row: BreakResultRow): void {
    if (this.expandedRow && this.expandedRow.breakId === row.breakId) {
      this.expandedRow = null;
    } else {
      this.expandedRow = row;
    }
    this.rebuildTableData();
  }

  select(row: BreakResultRow): void {
    this.selectRow.emit(row);
  }

  handleAddComment(event: { breakId: number; comment: string; action: string }): void {
    this.addComment.emit(event);
  }

  handleUpdateStatus(event: { breakId: number; status: BreakStatus; comment?: string; correlationId?: string }): void {
    this.updateStatus.emit(event);
  }

  displayAttribute(row: BreakResultRow, columnKey: string): string {
    const value = this.resolveAttribute(row, columnKey);
    return value ?? '—';
  }

  getBaseDisplay(row: BreakResultRow, key: string): string {
    switch (key) {
      case 'breakId':
        return String(row.breakId);
      case 'runDateTime':
        return row.runDateTime || '—';
      case 'status':
        return row.breakItem.status;
      case 'breakType':
        return row.breakItem.breakType;
      case 'triggerType':
        return row.triggerType || '—';
      default:
        return this.displayAttribute(row, key);
    }
  }

  isBaseRow(row: TableRow): row is BreakResultRow {
    return !(row as DetailRow).detailRow;
  }

  isSortable(columnKey: string): boolean {
    const base = this.baseColumns.find((column) => column.key === columnKey);
    if (base) {
      return base.sortable;
    }
    const dynamic = this.columns.find((column) => column.key === columnKey);
    return dynamic?.sortable ?? false;
  }

  private configureColumns(): void {
    const dynamicKeys = this.columns.map((column) => column.key);
    this.columnsWithExpand = ['select', ...this.baseColumns.map((column) => column.key), ...dynamicKeys, 'expand'];
  }

  private processData(): void {
    const filtered = this.applyFilter(this.rows);
    const sorted = this.applySort(filtered);
    this.filteredRows = sorted;

    const totalPages = Math.max(Math.ceil(sorted.length / this.pageSize), 1);
    if (this.pageIndex >= totalPages) {
      this.pageIndex = Math.max(totalPages - 1, 0);
    }

    const start = this.pageIndex * this.pageSize;
    this.currentPageRows = sorted.slice(start, start + this.pageSize);

    if (this.expandedRow) {
      const match = this.currentPageRows.find((row) => row.breakId === this.expandedRow?.breakId);
      this.expandedRow = match ?? null;
    }

    this.rebuildTableData();
  }

  private applyFilter(rows: BreakResultRow[]): BreakResultRow[] {
    const query = this.filterText.trim().toLowerCase();
    if (!query) {
      return [...rows];
    }
    return rows.filter((row) => {
      const values: string[] = [];
      this.baseColumns.forEach((column) => {
        values.push(this.getSortableValue(row, column.key) ?? '');
      });
      this.columns.forEach((column) => {
        values.push(this.resolveAttribute(row, column.key) ?? '');
      });
      return values.some((value) => value.toLowerCase().includes(query));
    });
  }

  private applySort(rows: BreakResultRow[]): BreakResultRow[] {
    const { active, direction } = this.sortState;
    if (!active || !direction) {
      return [...rows];
    }

    const multiplier = direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const aValue = (this.getSortableValue(a, active) ?? '').toLowerCase();
      const bValue = (this.getSortableValue(b, active) ?? '').toLowerCase();

      return aValue.localeCompare(bValue, undefined, { numeric: true, sensitivity: 'base' }) * multiplier;
    });
  }

  private rebuildTableData(): void {
    const data: TableRow[] = [];
    this.currentPageRows.forEach((row) => {
      data.push(row);
      if (this.expandedRow && row.breakId === this.expandedRow.breakId) {
        data.push({ detailRow: true, element: row });
      }
    });
    this.tableData = data;
  }

  private maybeRequestMoreFor(pageIndex: number, pageSize: number): void {
    const loaded = this.rows.length;
    const required = (pageIndex + 1) * pageSize;
    const total = this.total ?? loaded;
    if (required > loaded && loaded < total) {
      this.requestMore.emit();
    }
  }

  private emitSelection(): void {
    this.selectionChange.emit(Array.from(this.selectedIds));
  }

  private getSortableValue(row: BreakResultRow, key: string): string | null {
    switch (key) {
      case 'breakId':
        return String(row.breakId);
      case 'runDateTime':
        return row.runDateTime ?? null;
      case 'status':
        return row.breakItem.status ?? null;
      case 'breakType':
        return row.breakItem.breakType ?? null;
      case 'triggerType':
        return row.triggerType ?? null;
      default:
        return this.resolveAttribute(row, key);
    }
  }

  private resolveAttribute(row: BreakResultRow, columnKey: string): string | null {
    const direct = this.valueOrNull(row.attributes?.[columnKey]);
    if (direct) {
      return direct;
    }

    const classification = this.valueOrNull(row.breakItem?.classifications?.[columnKey]);
    if (classification) {
      return classification;
    }

    const sourced = this.lookupSourceValue(row, columnKey);
    if (sourced) {
      return sourced;
    }

    return null;
  }

  private lookupSourceValue(row: BreakResultRow, columnKey: string): string | null {
    const sources = row.breakItem?.sources;
    if (!sources) {
      return null;
    }
    for (const source of Object.values(sources)) {
      if (!source) {
        continue;
      }
      const candidate = this.formatSourceValue(source[columnKey]);
      if (candidate) {
        return candidate;
      }
    }
    return null;
  }

  private valueOrNull(value: string | undefined | null): string | null {
    if (value === undefined || value === null) {
      return null;
    }
    const trimmed = value.toString().trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private formatSourceValue(value: unknown): string | null {
    if (value === null || value === undefined) {
      return null;
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      return trimmed.length > 0 ? trimmed : null;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    try {
      return JSON.stringify(value);
    } catch {
      return null;
    }
  }
}
