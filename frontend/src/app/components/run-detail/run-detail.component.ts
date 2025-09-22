import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  BreakItem,
  BulkBreakUpdatePayload,
  FilterMetadata,
  RunDetail,
  RunAnalytics
} from '../../models/api-models';
import { BreakFilter } from '../../models/break-filter';
import { BreakStatus } from '../../models/break-status';

@Component({
  selector: 'urp-run-detail',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule],
  templateUrl: './run-detail.component.html',
  styleUrls: ['./run-detail.component.css']
})
export class RunDetailComponent implements OnChanges {
  @Input() runDetail: RunDetail | null = null;
  @Input() selectedBreak: BreakItem | null = null;
  @Input() filterMetadata: FilterMetadata | null = null;
  @Input() filter: BreakFilter | null = null;
  @Output() selectBreak = new EventEmitter<BreakItem>();
  @Output() filterChange = new EventEmitter<BreakFilter>();
  @Output() bulkAction = new EventEmitter<BulkBreakUpdatePayload>();

  selectedStatuses: BreakStatus[] = [];
  localFilter: BreakFilter = {};
  selectedIds = new Set<number>();
  bulkComment = '';
  bulkActionCode = 'BULK_NOTE';
  bulkStatus: BreakStatus | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['filter']) {
      this.localFilter = { ...(this.filter ?? {}) };
      this.selectedStatuses = [...(this.filter?.statuses ?? [])];
    }
    if (changes['runDetail']) {
      this.selectedIds.clear();
      this.bulkComment = '';
      this.bulkStatus = null;
      this.bulkActionCode = 'BULK_NOTE';
    }
  }

  onSelectBreak(item: BreakItem): void {
    this.selectBreak.emit(item);
  }

  onFilterChanged(): void {
    const nextFilter: BreakFilter = {
      ...this.localFilter,
      statuses: this.selectedStatuses.length > 0 ? [...this.selectedStatuses] : undefined
    };
    this.filterChange.emit(nextFilter);
  }

  resetStatuses(): void {
    this.selectedStatuses = [];
    this.onFilterChanged();
  }

  toggleStatus(status: BreakStatus, checked: boolean): void {
    if (checked) {
      if (!this.selectedStatuses.includes(status)) {
        this.selectedStatuses = [...this.selectedStatuses, status];
      }
    } else {
      this.selectedStatuses = this.selectedStatuses.filter((item) => item !== status);
    }
    this.onFilterChanged();
  }

  formatStatus(status: BreakStatus): string {
    return status.replace(/_/g, ' ');
  }

  toggleSelection(item: BreakItem, checked: boolean): void {
    if (checked) {
      this.selectedIds.add(item.id);
    } else {
      this.selectedIds.delete(item.id);
    }
  }

  isSelected(breakId: number): boolean {
    return this.selectedIds.has(breakId);
  }

  selectAll(): void {
    this.runDetail?.breaks.forEach((item) => this.selectedIds.add(item.id));
  }

  clearSelection(): void {
    this.selectedIds.clear();
  }

  submitBulkAction(): void {
    if (this.selectedIds.size === 0) {
      return;
    }
    const payload: BulkBreakUpdatePayload = {
      breakIds: Array.from(this.selectedIds),
      comment: this.bulkComment.trim() || undefined,
      action: this.bulkActionCode.trim() || undefined,
      status: this.bulkStatus ?? undefined
    };
    if (!payload.comment && !payload.status) {
      return;
    }
    this.bulkAction.emit(payload);
    this.bulkComment = '';
    this.bulkStatus = null;
  }

  get bulkStatusOptions(): BreakStatus[] {
    if (!this.runDetail || this.selectedIds.size === 0) {
      return [];
    }
    const selectedBreaks = this.runDetail.breaks.filter((item) => this.selectedIds.has(item.id));
    if (selectedBreaks.length === 0) {
      return [];
    }
    const intersection = selectedBreaks
      .map((item) => new Set(item.allowedStatusTransitions))
      .reduce((acc, current) => {
        return new Set([...acc].filter((status) => current.has(status)));
      });
    return Array.from(intersection);
  }

  get analytics(): RunAnalytics | null {
    return this.runDetail?.analytics ?? null;
  }

  get statusAnalytics(): Array<{ label: string; value: number; percent: number }> {
    if (!this.analytics || this.analytics.filteredBreakCount === 0) {
      return [];
    }
    const total = this.analytics.filteredBreakCount;
    return Object.entries(this.analytics.breaksByStatus).map(([label, value]) => ({
      label,
      value,
      percent: Math.round((value / total) * 1000) / 10
    }));
  }

  get productAnalytics(): Array<{ label: string; value: number }> {
    if (!this.analytics) {
      return [];
    }
    return Object.entries(this.analytics.breaksByProduct).map(([label, value]) => ({
      label,
      value
    }));
  }
}
