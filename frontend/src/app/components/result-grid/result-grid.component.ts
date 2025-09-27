import { CdkVirtualScrollViewport, ScrollingModule } from '@angular/cdk/scrolling';
import { AsyncPipe, CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { BreakResultRow, GridColumn } from '../../models/api-models';

@Component({
  selector: 'urp-result-grid',
  standalone: true,
  imports: [CommonModule, ScrollingModule, AsyncPipe],
  templateUrl: './result-grid.component.html',
  styleUrls: ['./result-grid.component.css']
})
export class ResultGridComponent implements OnChanges {
  @Input() rows: BreakResultRow[] = [];
  @Input() columns: GridColumn[] = [];
  @Input() loading = false;
  @Input() total: number | null = null;
  @Input() selectedRowId: number | null = null;
  @Input() selectedBreakIds: number[] | null = null;

  @Output() selectRow = new EventEmitter<BreakResultRow>();
  @Output() requestMore = new EventEmitter<void>();
  @Output() selectionChange = new EventEmitter<number[]>();
  @Output() selectFiltered = new EventEmitter<void>();

  @ViewChild(CdkVirtualScrollViewport) viewport?: CdkVirtualScrollViewport;

  readonly baseColumns = [
    { key: 'breakId', label: 'Break ID' },
    { key: 'runDateTime', label: 'Run Time' },
    { key: 'status', label: 'Status' },
    { key: 'breakType', label: 'Type' },
    { key: 'triggerType', label: 'Trigger' }
  ];

  private selectedIds = new Set<number>();

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
  }

  onScrolledIndexChange(index: number): void {
    if (this.loading || this.rows.length === 0) {
      return;
    }
    const threshold = Math.max(this.rows.length - 50, 0);
    if (index >= threshold) {
      this.requestMore.emit();
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
    if (this.rows.length === 0) {
      return;
    }
    const allSelected = this.rows.every((row) => this.selectedIds.has(row.breakId));
    if (allSelected) {
      this.rows.forEach((row) => this.selectedIds.delete(row.breakId));
    } else {
      this.rows.forEach((row) => this.selectedIds.add(row.breakId));
    }
    this.emitSelection();
  }

  selectLoaded(): void {
    this.rows.forEach((row) => this.selectedIds.add(row.breakId));
    this.emitSelection();
  }

  requestFilteredSelection(): void {
    this.selectFiltered.emit();
  }

  rowClasses(row: BreakResultRow): Record<string, boolean> {
    return {
      active: row.breakId === this.selectedRowId
    };
  }

  displayAttribute(row: BreakResultRow, columnKey: string): string {
    return row.attributes[columnKey] ?? row.breakItem.classifications[columnKey] ?? '';
  }

  select(row: BreakResultRow): void {
    this.selectRow.emit(row);
  }

  get selectedCount(): number {
    return this.selectedIds.size;
  }

  private emitSelection(): void {
    this.selectionChange.emit(Array.from(this.selectedIds));
  }
}
