import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BreakItem, FilterMetadata, RunDetail } from '../../models/api-models';
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

  selectedStatuses: BreakStatus[] = [];
  localFilter: BreakFilter = {};

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['filter']) {
      this.localFilter = { ...(this.filter ?? {}) };
      this.selectedStatuses = [...(this.filter?.statuses ?? [])];
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
}
