import { CommonModule, DatePipe, JsonPipe } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BreakItem } from '../../models/api-models';
import { BreakStatus } from '../../models/break-status';

@Component({
  selector: 'urp-break-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, JsonPipe],
  templateUrl: './break-detail.component.html',
  styleUrls: ['./break-detail.component.css']
})
export class BreakDetailComponent implements OnChanges {
  @Input() breakItem: BreakItem | null = null;
  @Output() addComment = new EventEmitter<{ breakId: number; comment: string; action: string }>();
  @Output() updateStatus = new EventEmitter<{ breakId: number; status: BreakStatus }>();

  commentText = '';
  commentAction = 'INVESTIGATION_NOTE';
  fieldKeys: string[] = [];
  differenceKeys = new Set<string>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['breakItem']) {
      this.commentText = '';
      this.commentAction = 'INVESTIGATION_NOTE';
      this.recomputeKeys();
    }
  }

  submitComment(): void {
    if (!this.breakItem || !this.commentText.trim()) {
      return;
    }
    this.addComment.emit({
      breakId: this.breakItem.id,
      comment: this.commentText.trim(),
      action: this.commentAction.trim()
    });
    this.commentText = '';
  }

  changeStatus(status: BreakStatus): void {
    if (!this.breakItem) {
      return;
    }
    this.updateStatus.emit({ breakId: this.breakItem.id, status });
  }

  get statusOptions(): BreakStatus[] {
    return this.breakItem?.allowedStatusTransitions ?? [];
  }

  getStatusText(status: BreakStatus): string {
    switch (status) {
      case BreakStatus.Open:
        return 'Mark Open';
      case BreakStatus.PendingApproval:
        return 'Submit for Approval';
      case BreakStatus.Closed:
        return 'Mark Closed';
      default:
        return 'Update Status';
    }
  }

  isDifferent(field: string): boolean {
    return this.differenceKeys.has(field);
  }

  valueFor(source: 'A' | 'B', field: string): unknown {
    if (!this.breakItem) {
      return '';
    }
    return source === 'A'
      ? this.breakItem.sourceA[field as keyof typeof this.breakItem.sourceA]
      : this.breakItem.sourceB[field as keyof typeof this.breakItem.sourceB];
  }

  displayValue(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    if (typeof value === 'string') {
      return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return value.toString();
    }
    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch {
        return String(value);
      }
    }
    return String(value);
  }

  private recomputeKeys(): void {
    if (!this.breakItem) {
      this.fieldKeys = [];
      this.differenceKeys.clear();
      return;
    }
    const keys = new Set<string>();
    Object.keys(this.breakItem.sourceA ?? {}).forEach((key) => keys.add(key));
    Object.keys(this.breakItem.sourceB ?? {}).forEach((key) => keys.add(key));
    this.fieldKeys = Array.from(keys).sort();
    this.differenceKeys = new Set(
      this.fieldKeys.filter((key) => {
        const left = this.breakItem?.sourceA[key as keyof typeof this.breakItem.sourceA];
        const right = this.breakItem?.sourceB[key as keyof typeof this.breakItem.sourceB];
        return JSON.stringify(left) !== JSON.stringify(right);
      })
    );
  }
}
