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
  @Input() workflowSummary: string | null = null;
  @Output() addComment = new EventEmitter<{ breakId: number; comment: string; action: string }>();
  @Output() updateStatus = new EventEmitter<{
    breakId: number;
    status: BreakStatus;
    comment?: string;
    correlationId?: string;
  }>();

  commentText = '';
  commentAction = 'INVESTIGATION_NOTE';
  workflowComment = '';
  workflowError: string | null = null;
  fieldKeys: string[] = [];
  sourceKeys: string[] = [];
  classificationEntries: Array<{ key: string; value: string }> = [];
  differenceKeys = new Set<string>();
  readonly BreakStatus = BreakStatus;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['breakItem']) {
      this.commentText = '';
      this.commentAction = 'INVESTIGATION_NOTE';
      this.workflowComment = '';
      this.workflowError = null;
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

  triggerWorkflow(status: BreakStatus): void {
    if (!this.breakItem) {
      return;
    }
    this.workflowError = null;
    const requiresComment = this.requiresComment(status);
    const trimmed = this.workflowComment.trim();
    if (requiresComment && !trimmed) {
      this.workflowError = 'Comment is required for this action.';
      return;
    }
    this.updateStatus.emit({
      breakId: this.breakItem.id,
      status,
      comment: trimmed || undefined
    });
    this.workflowComment = '';
  }

  get statusOptions(): BreakStatus[] {
    return this.breakItem?.allowedStatusTransitions ?? [];
  }

  getStatusText(status: BreakStatus): string {
    switch (status) {
      case BreakStatus.Open:
        return this.breakItem?.status === BreakStatus.PendingApproval ? 'Withdraw Submission' : 'Mark Open';
      case BreakStatus.PendingApproval:
        return 'Submit for Approval';
      case BreakStatus.Closed:
        return 'Approve Break';
      case BreakStatus.Rejected:
        return 'Reject Break';
      default:
        return 'Update Status';
    }
  }

  get canSubmit(): boolean {
    return this.statusOptions.includes(BreakStatus.PendingApproval);
  }

  get canApprove(): boolean {
    return this.statusOptions.includes(BreakStatus.Closed);
  }

  get canReject(): boolean {
    return this.statusOptions.includes(BreakStatus.Rejected);
  }

  get canReopen(): boolean {
    return (
      this.statusOptions.includes(BreakStatus.Open) &&
      (this.breakItem?.status === BreakStatus.PendingApproval || this.breakItem?.status === BreakStatus.Rejected)
    );
  }

  get canComment(): boolean {
    return (this.statusOptions?.length ?? 0) > 0;
  }

  requiresComment(status: BreakStatus): boolean {
    return status === BreakStatus.Closed || status === BreakStatus.Rejected;
  }

  isDifferent(field: string): boolean {
    return this.differenceKeys.has(field);
  }

  valueFor(source: string, field: string): unknown {
    if (!this.breakItem?.sources) {
      return null;
    }
    return this.breakItem.sources[source]?.[field] ?? null;
  }

  formatSourceLabel(source: string): string {
    if (!source) {
      return '';
    }
    return source
      .toLowerCase()
      .replace(/_/g, ' ')
      .replace(/\b\w/g, (character) => character.toUpperCase());
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
      this.sourceKeys = [];
      this.classificationEntries = [];
      this.differenceKeys.clear();
      return;
    }

    const sources = this.breakItem.sources ?? {};
    this.sourceKeys = Object.keys(sources);

    const fields = new Set<string>();
    this.sourceKeys.forEach((source) => {
      const payload = sources[source];
      if (!payload) {
        return;
      }
      Object.keys(payload).forEach((field) => fields.add(field));
    });
    this.fieldKeys = Array.from(fields).sort((a, b) => a.localeCompare(b));

    this.differenceKeys = new Set(
      this.fieldKeys.filter((field) => {
        const values = this.sourceKeys.map((source) => this.serializeValue(sources[source]?.[field]));
        const distinct = new Set(values);
        return distinct.size > 1;
      })
    );

    const classifications = Object.entries(this.breakItem.classifications ?? {});
    this.classificationEntries = classifications
      .map(([key, value]) => ({ key, value }))
      .sort((a, b) => a.key.localeCompare(b.key));
  }

  private serializeValue(value: unknown): string {
    if (value === undefined) {
      return '__undefined__';
    }
    if (value === null) {
      return '__null__';
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
}
