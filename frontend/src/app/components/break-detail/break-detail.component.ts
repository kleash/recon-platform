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
  @Input() statusOptions: BreakStatus[] = [];
  @Output() addComment = new EventEmitter<{ breakId: number; comment: string; action: string }>();
  @Output() updateStatus = new EventEmitter<{ breakId: number; status: BreakStatus }>();

  commentText = '';
  commentAction = 'INVESTIGATION_NOTE';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['breakItem']) {
      this.commentText = '';
      this.commentAction = 'INVESTIGATION_NOTE';
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

  getStatusText(status: BreakStatus): string {
    switch (status) {
      case BreakStatus.Open:
        return 'Mark Open';
      case BreakStatus.PendingApproval:
        return 'Mark Pending Approval';
      case BreakStatus.Closed:
        return 'Mark Closed';
      default:
        return 'Update Status';
    }
  }
}
