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
  classificationKeys: string[] = [];
  tableFilter = '';
  bulkError: string | null = null;

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
      this.tableFilter = '';
      this.bulkError = null;
      this.recomputeClassificationKeys();
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

  classificationValue(item: BreakItem, key: string): string {
    const value = item.classifications?.[key];
    return value && value.length > 0 ? value : '—';
  }

  formatClassificationKey(key: string): string {
    if (!key) {
      return '';
    }
    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/_/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/^./, (character) => character.toUpperCase());
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
    this.filteredBreaks.forEach((item) => this.selectedIds.add(item.id));
  }

  clearSelection(): void {
    this.selectedIds.clear();
  }

  submitBulkAction(): void {
    if (this.selectedIds.size === 0) {
      return;
    }
    this.bulkError = null;
    const payload: BulkBreakUpdatePayload = {
      breakIds: Array.from(this.selectedIds),
      comment: this.bulkComment.trim() || undefined,
      action: this.bulkActionCode.trim() || undefined,
      status: this.bulkStatus ?? undefined
    };
    if (!payload.comment && !payload.status) {
      this.bulkError = 'Select a status or provide a comment before applying the bulk action.';
      return;
    }
    if (payload.status && this.requiresComment(payload.status) && !payload.comment) {
      this.bulkError = 'Approvals and rejections require a justification comment.';
      return;
    }
    this.bulkAction.emit(payload);
    this.bulkComment = '';
    this.bulkStatus = null;
    this.bulkError = null;
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

  get filteredBreaks(): BreakItem[] {
    if (!this.runDetail) {
      return [];
    }
    const term = this.tableFilter.trim().toLowerCase();
    if (!term) {
      return this.runDetail.breaks;
    }
    return this.runDetail.breaks.filter((item) => this.matchesFilter(item, term));
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

  private recomputeClassificationKeys(): void {
    if (!this.runDetail?.breaks || this.runDetail.breaks.length === 0) {
      this.classificationKeys = [];
      return;
    }

    const keyUsage = new Map<string, boolean>();
    this.runDetail.breaks.forEach((item) => {
      const entries = Object.entries(item.classifications ?? {});
      entries.forEach(([key, value]) => {
        const hasValue = typeof value === 'string' ? value.trim().length > 0 : value !== null && value !== undefined;
        keyUsage.set(key, (keyUsage.get(key) ?? false) || hasValue);
      });
    });

    const priorityOrder = new Map<string, number>([
      ['product', 0],
      ['subProduct', 1],
      ['entity', 2]
    ]);

    this.classificationKeys = Array.from(keyUsage.entries())
      .filter(([, hasValue]) => hasValue)
      .map(([key]) => key)
      .sort((a, b) => {
        const priorityA = priorityOrder.get(a);
        const priorityB = priorityOrder.get(b);

        if (priorityA !== undefined && priorityB !== undefined) {
          return priorityA - priorityB;
        }
        if (priorityA !== undefined) {
          return -1;
        }
        if (priorityB !== undefined) {
          return 1;
        }
        return a.localeCompare(b);
      });
  }

  private matchesFilter(item: BreakItem, term: string): boolean {
    const classificationValues = Object.values(item.classifications ?? {})
      .map((value) => (value ? value.toString().toLowerCase() : ''))
      .join(' ');
    return (
      item.id.toString().includes(term) ||
      item.breakType.toLowerCase().includes(term) ||
      item.status.toLowerCase().includes(term) ||
      classificationValues.includes(term)
    );
  }

  private requiresComment(status: BreakStatus): boolean {
    return status === BreakStatus.Closed || status === BreakStatus.Rejected;
  }
}
