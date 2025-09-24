import { CommonModule, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BreakItem, FilterMetadata } from '../../models/api-models';
import { BreakStatus } from '../../models/break-status';

@Component({
  selector: 'urp-checker-queue',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './checker-queue.component.html',
  styleUrls: ['./checker-queue.component.css']
})
export class CheckerQueueComponent {
  @Input() pendingBreaks: BreakItem[] | null = [];
  @Input() filterMetadata: FilterMetadata | null = null;
  @Output() approve = new EventEmitter<{ breakIds: number[]; comment: string }>();
  @Output() reject = new EventEmitter<{ breakIds: number[]; comment: string }>();

  selectedIds = new Set<number>();
  comment = '';
  error: string | null = null;
  filterProduct: string | null = null;
  filterSubProduct: string | null = null;
  filterEntity: string | null = null;

  get queue(): BreakItem[] {
    const items = this.pendingBreaks ?? [];
    return items.filter((item) => {
      const product = item.classifications?.['product'];
      const subProduct = item.classifications?.['subProduct'];
      const entity = item.classifications?.['entity'];
      const productMatches = !this.filterProduct || product === this.filterProduct;
      const subMatches = !this.filterSubProduct || subProduct === this.filterSubProduct;
      const entityMatches = !this.filterEntity || entity === this.filterEntity;
      return productMatches && subMatches && entityMatches;
    });
  }

  toggleSelection(breakId: number, checked: boolean): void {
    if (checked) {
      this.selectedIds.add(breakId);
    } else {
      this.selectedIds.delete(breakId);
    }
  }

  selectAll(): void {
    this.queue.forEach((item) => this.selectedIds.add(item.id));
  }

  clear(): void {
    this.selectedIds.clear();
  }

  submitApproval(): void {
    this.dispatch(this.approve, BreakStatus.Closed);
  }

  submitRejection(): void {
    this.dispatch(this.reject, BreakStatus.Rejected);
  }

  private dispatch(
    emitter: EventEmitter<{ breakIds: number[]; comment: string }>,
    status: BreakStatus
  ): void {
    if (this.selectedIds.size === 0) {
      this.error = 'Select at least one break from the queue.';
      return;
    }
    const trimmed = this.comment.trim();
    if (!trimmed) {
      this.error = `${status === BreakStatus.Closed ? 'Approval' : 'Rejection'} requires a comment.`;
      return;
    }
    emitter.emit({ breakIds: Array.from(this.selectedIds), comment: trimmed });
    this.comment = '';
    this.error = null;
    this.selectedIds.clear();
  }
}
